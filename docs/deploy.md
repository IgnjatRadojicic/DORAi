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
6. First login. On its first start the server creates a self-signed certificate and the admin
   (`DORAI_ADMIN_USERNAME`, default `admin`), and logs both once:
   `docker compose logs server | grep -E "Certificate SHA-256|First start"`.
   The log line has a temporary password and an invite link. Open the link in the app, log in with the
   temporary password and set a new one (at least 12 characters). The temporary password is not shown again.

### Users
The admin creates users in the app (or `POST /api/admin/users`) with a role: viewer (read), reviewer
(scan, check, review suggestions, export) or admin (users and invites). Each new user gets a temporary
password and an invite link valid for 72 hours; send them through different channels. Access tokens
last 15 minutes, sessions 30 days. Disabling a user, or revoking their sessions
(`POST /api/admin/users/{username}/revoke`, which signs them out everywhere but lets them log in again),
takes effect on their next call. The server checks the session on every API call.

Login limits: five wrong passwords for one username within 15 minutes lock that username for 15 minutes,
the right password included (revoking the user's sessions lifts it early). Redeem, login and password
change are limited to 10 calls per minute from one address; behind a reverse proxy every user shares the
proxy's address, so keep the server reachable directly.

The invite link pins the certificate fingerprint. If `tls/keystore.p12` in the `server-data` volume is
deleted, the server makes a new certificate and every app must connect again with a new invite.

Cloud credentials for live scans should be read-only: an Azure service principal with Reader
on the subscriptions, an AWS IAM user or role with read-only access to tagging and cost data.

### What a live scan reads (`DORAI_DATA_SOURCE=live`)
- **Azure**: one Resource Graph query over every subscription the principal can read (or
  `DORAI_AZURE_SUBSCRIPTIONS` / `DORAI_AZURE_RESOURCE_GROUP`), and actual cost per resource per month
  from Cost Management for the last 12 months. Billing data lags a day or two.
- **AWS**: the Resource Groups Tagging API in each region of `DORAI_AWS_REGIONS`. Resources that were
  never tagged are not returned by that API. Cost comes from Cost Explorer, which keeps per-resource
  cost for 14 days only and only after "resource-level data" is turned on in the AWS billing preferences.
  Each Cost Explorer request costs USD 0.01.
- If a cloud cannot be scanned, the scan fails with 502 and the stored inventory stays as it was.
  If cost is unavailable, the scan continues without that cloud's cost.
- Cost of deleted resources is ignored in the concentration report.

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
on the fixtures in memory, stores nothing and has no auth (every route is open).
With a database, auth is on and the server needs `DORAI_JWT_SECRET`.

For the demo, AWS can come from LocalStack instead of a real account: set `DORAI_AWS_ENDPOINT`
(for example `http://localhost:4566`). The scan then lists S3, DynamoDB, Lambda and SQS one by one,
because free LocalStack returns nothing from the Tagging API, and takes AWS cost from the FinPay
fixture, because LocalStack has no billing. The scan reports the cost source as `Mixed`.

Against the real clouds with your own `az login` and AWS profile, read-only and without Cost Explorer:
`DORAI_LIVE_TESTS=1 ./gradlew :server:test --tests "dorai.server.collect.LiveSmokeTest"`.
Build the image locally from the repository root: `docker build -t ghcr.io/ignjatradojicic/dorai-server:dev .`
and run it with `DORAI_VERSION=dev`.

## Releases
Pushing a tag `v1.2.3` runs `.github/workflows/server-image.yml`, which publishes the image
for amd64 and arm64 with the tags `1.2.3`, `1.2` and `latest`.
Every pull request already builds the image (amd64 only, not pushed), so a tag only fails on publishing.
The same tag is what other repositories pin for the GitHub Action (`uses: IgnjatRadojicic/DORAi@v1.2.3`).

The server's heap is 75% of the container's memory (`JAVA_OPTS=-XX:MaxRAMPercentage=75` in the image).
To cap the server, add `mem_limit: 2g` to the `server` service; to change the share, set `JAVA_OPTS` there.
