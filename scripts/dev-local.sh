#!/usr/bin/env bash
# Runs DORAi on this machine for development: database, server and desktop app, with no cloud keys.
#
#   scripts/dev-local.sh            start everything; closing the app stops the server
#   scripts/dev-local.sh --server   start database and server only (Ctrl+C stops the server)
#   scripts/dev-local.sh --stop     stop the database container
#   scripts/dev-local.sh --reset    delete the local database, so the next start is a first start
#
# The server runs with the FinPay demo data and the fake AI. Log in as admin / dorai-local-dev.
# Local development only: these fixed values are not secrets and must never be used on a real server.
# Needs Docker, JDK 21 and the Android SDK (ANDROID_HOME or local.properties). Works in Git Bash, macOS and Linux.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DB_CONTAINER=dorai-dev-db
DB_VOLUME=dorai-dev-db
DB_IMAGE=pgvector/pgvector:0.8.7-pg16
DB_PORT=5433
DB_PASSWORD=dorai-local-dev-db
PORT=8080
ADMIN_USER=admin
ADMIN_PASSWORD=dorai-local-dev
LOG_DIR="$ROOT/build/dev-local"
LOG="$LOG_DIR/server.log"
SERVER_PID=""

say() { printf '\033[1m[dev-local]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[dev-local] %s\033[0m\n' "$*" >&2; exit 1; }

stop_server() {
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        say "Stopping the server"
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}

case "${1:-}" in
    --stop)
        docker stop "$DB_CONTAINER" >/dev/null 2>&1 && say "Database stopped" || say "Database was not running"
        exit 0 ;;
    --reset)
        docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
        docker volume rm "$DB_VOLUME" >/dev/null 2>&1 || true
        say "Local database deleted; the next start creates the admin again"
        exit 0 ;;
    ""|--server) ;;
    *) fail "Unknown option '$1'. Use --server, --stop or --reset." ;;
esac

# 1. Checks
docker info >/dev/null 2>&1 || fail "Docker is not running. Start Docker Desktop and run this again."
command -v curl >/dev/null || fail "curl is missing."
if curl -s -o /dev/null "http://localhost:$PORT/api/health"; then
    fail "Something already answers on port $PORT. Stop it first (the app always connects to localhost:$PORT)."
fi

# 2. Database: its own container and volume, on 127.0.0.1:$DB_PORT so it never clashes with another Postgres
if [[ -z "$(docker ps -aq -f name="^${DB_CONTAINER}$")" ]]; then
    say "Creating the database container $DB_CONTAINER"
    docker run -d --name "$DB_CONTAINER" -p "127.0.0.1:$DB_PORT:5432" \
        -e POSTGRES_DB=dorai -e POSTGRES_USER=dorai -e POSTGRES_PASSWORD="$DB_PASSWORD" \
        -v "$DB_VOLUME:/var/lib/postgresql/data" "$DB_IMAGE" >/dev/null
else
    docker start "$DB_CONTAINER" >/dev/null
fi
say "Waiting for the database"
for _ in $(seq 1 60); do
    docker exec "$DB_CONTAINER" pg_isready -U dorai -d dorai >/dev/null 2>&1 && break
    sleep 1
done
docker exec "$DB_CONTAINER" pg_isready -U dorai -d dorai >/dev/null 2>&1 || fail "The database did not start. See: docker logs $DB_CONTAINER"

# 3. Server: demo data, fake AI, plain HTTP
say "Building the server"
./gradlew :server:installDist -q --console=plain
mkdir -p "$LOG_DIR"
say "Starting the server (log: build/dev-local/server.log)"
(
    unset DORAI_DATA_SOURCE DORAI_CLOUDS DORAI_TLS_KEYSTORE DORAI_AI_OPENAI_KEY DORAI_AI_OPENAI_BASE_URL
    export DORAI_DB_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/dorai"
    export DORAI_DB_USER=dorai DORAI_DB_PASSWORD="$DB_PASSWORD"
    # A new signing key each start: sessions end when the server restarts, which is fine locally.
    export DORAI_JWT_SECRET="dev-local-$(date +%s)-$RANDOM$RANDOM$RANDOM$RANDOM-not-a-secret"
    export DORAI_PORT="$PORT" DORAI_AI_PROVIDER=fake DORAI_ADMIN_USERNAME="$ADMIN_USER"
    exec server/build/install/server/bin/server
) >"$LOG" 2>&1 &
SERVER_PID=$!
trap stop_server EXIT INT TERM

for _ in $(seq 1 90); do
    curl -s -o /dev/null "http://localhost:$PORT/api/health" && break
    kill -0 "$SERVER_PID" 2>/dev/null || fail "The server stopped while starting. Last lines of the log:
$(tail -n 15 "$LOG")"
    sleep 1
done
curl -s -o /dev/null "http://localhost:$PORT/api/health" || fail "The server did not answer on port $PORT. See $LOG"

# 4. Admin: on a first start, swap the temporary password for the fixed local one
login() {
    curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$PORT/api/auth/login" \
        -H 'Content-Type: application/json' -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$1\"}"
}
TEMP=$(grep -o 'Temporary password (shown once): [^ ]*' "$LOG" | head -n 1 | sed 's/.*: //' || true)
if [[ -n "$TEMP" ]]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://localhost:$PORT/api/auth/password" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$ADMIN_USER\",\"currentPassword\":\"$TEMP\",\"newPassword\":\"$ADMIN_PASSWORD\"}")
    [[ "$code" == 200 ]] || fail "Could not set the admin password (HTTP $code). Try: scripts/dev-local.sh --reset"
    say "First start: admin password set"
elif [[ "$(login "$ADMIN_PASSWORD")" != 200 ]]; then
    fail "The admin password is not $ADMIN_PASSWORD on this database. Start fresh with: scripts/dev-local.sh --reset"
fi

say "Server ready on http://localhost:$PORT  -  log in as $ADMIN_USER / $ADMIN_PASSWORD"

# 5. App, or keep the server in the foreground
if [[ "${1:-}" == "--server" ]]; then
    say "Press Ctrl+C to stop the server"
    wait "$SERVER_PID"
else
    say "Starting the desktop app; closing it stops the server"
    ./gradlew :app:run -q --console=plain
fi
