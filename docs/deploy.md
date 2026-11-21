# Running the DORAi server

The firm runs DORAi on its own host with Docker. Everything needed is in `deploy/`:
`compose.yaml`, `.env.example` and `backup.sh`. No source code or JDK is needed on the host.
What the deployment still lacks is tracked in `docs/docker-missing.yml`.

## What runs

| Service | Image | Purpose |
|---|---|---|
| `server` | `ghcr.io/ignjatradojicic/dorai-server` | API for the apps and the CLI. HTTPS on 8443, plain HTTP on 8080 for this host only |
| `db` | `pgvector/pgvector:0.8.7-pg16` | PostgreSQL 16 with pgvector: register, users, regulation search. Port 5432 for this host only |
| `backup` | same as `db` | `pg_dump` every night into `deploy/backups`, keeps 14 days |

Data lives in two Docker volumes: `db-data` (database) and `server-data` (TLS keystore and server files).

## Requirements
- Linux host (amd64 or arm64) with Docker Engine and the Compose plugin, 2 CPU, 4 GB RAM, 20 GB disk.
- A DNS name the staff can reach, for example `dorai.firm.local`, and port 8443 open to them.
- Outbound HTTPS from the host to the cloud APIs and the LLM endpoint, when live scans or the real AI are turned on.

## Install
1. Copy the `deploy/` folder to the host.
2. `cp .env.example .env` and fill it in. `DORAI_DB_PASSWORD`, `DORAI_JWT_SECRET` and
   `DORAI_TLS_KEYSTORE_PASSWORD` should be long random values, for example from `openssl rand -base64 32`.
   Keep `.env` readable only by the admin: `chmod 600 .env`.
3. Pin a release: set `DORAI_VERSION` to a version such as `1.0.0` rather than `latest`.
4. `docker compose up -d`, then check `docker compose ps` until all services are healthy.
5. `curl http://localhost:8080/api/health` on the host returns `{"status":"ok",...}`.

Cloud credentials for live scans should be read-only: an Azure service principal with Reader
on the subscriptions, an AWS IAM user or role with read-only access to tagging and cost data.

## Update
```
docker compose pull
docker compose up -d
```
The database migrates itself on start. Take a backup first (below).

## Backups and restore
Backups are written as `deploy/backups/dorai-<date>.dump`. Copy that folder to the firm's
offsite backup storage; a backup on the same disk does not survive a disk failure.

Backup now: `docker compose exec backup sh -c 'pg_dump -Fc -f /backups/dorai-manual.dump'`

Restore into an empty database:
```
docker compose stop server
docker compose exec -T db pg_restore -U dorai -d dorai --clean --if-exists < backups/dorai-<date>.dump
docker compose start server
```
Test a restore at least once after installing.

## Logs
`docker compose logs -f server`. Each service keeps at most 5 files of 10 MB.

## For developers
Run only the database and start the server with Gradle:
```
cd deploy
cp .env.example .env    # set DORAI_DB_PASSWORD
docker compose up -d db
```
Then point the server at it (the password is the one from `.env`):
```
DORAI_DB_URL=jdbc:postgresql://localhost:5432/dorai DORAI_DB_PASSWORD=... ./gradlew :server:run
```
On start the server runs the Flyway migrations in `server/src/main/resources/db/migration`, and
loads the FinPay demo register when the database holds no register yet. Without `DORAI_DB_URL` it runs
on the fixtures in memory and stores nothing.
Build the image locally from the repository root: `docker build -t ghcr.io/ignjatradojicic/dorai-server:dev .`
and run it with `DORAI_VERSION=dev`.

## Releases
Pushing a tag `v1.2.3` runs `.github/workflows/server-image.yml`, which publishes the image
for amd64 and arm64 with the tags `1.2.3`, `1.2` and `latest`.
