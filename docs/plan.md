# DORAi plan

## 1. What we build
A firm installs the DORAi server on its own infrastructure. The server scans the firm's cloud, keeps
the DORA Register of Information, runs the rules and the AI, and exports the register. Staff use the
desktop or Android app; CI uses the `dorai` CLI.

Demo story (3 minutes): admin invites a reviewer, reviewer connects with the link and sets a password,
runs a scan, accepts AI suggestions with citations, sees a HIGH finding from a Terraform plan that moves
data to `eastus`, exports the register.

## 2. Architecture

```
 Desktop app ┐                          ┌ PostgreSQL + pgvector
 Android app ┼── HTTPS (pinned cert) ── DORAi server ─┼ Cloud APIs (Azure, AWS)
 dorai CLI ──┘                          └ LLM (via Koog)
```

| Module | Packages | Content |
|---|---|---|
| `shared` (KMP: JVM, Android) | `dorai.shared.model` | DTOs, the JSON contract |
| | `dorai.shared.api` | Ktor client for every endpoint, used by apps and CLI |
| | `dorai.shared.rules` | Rules, register validator, Terraform plan reader: pure Kotlin, so server and CLI run the same code |
| `server` (JVM) | `dorai.server` | Ktor setup, routing, config |
| | `dorai.server.auth` | Users, roles, invites, passwords, JWT |
| | `dorai.server.db` | Exposed tables, Flyway migrations, seed |
| | `dorai.server.scan` | Collectors (Azure, AWS, fixtures), cost, concentration |
| | `dorai.server.register` | Register read/update, validation |
| | `dorai.server.ai` | Koog agents: classifier, suggester, explainer, report drafter, regulation search |
| | `dorai.server.export` | EBA CSV, PDF report |
| `app` (Compose: desktop, Android) | `dorai.app.*` | Screens and ViewModels, see lanes in `CLAUDE.md` |
| `androidApp` | `dorai.android` | Activity, manifest, invite deep link |
| `cli` (JVM) | `dorai.cli` | `dorai check plan.json`, output for PR comments |

## 3. Domain
- **Resource**: id, name, type, provider, region, tags, source (`scanned` or `planned` with a change action).
- **Register**: financial entity (LEI), ICT providers, contracts, ICT services, business functions
  (critical or important), links between them. Follows the EBA templates used for the CSV export.
- **Finding**: rule id, severity (HIGH, MEDIUM, LOW), resource, message. Produced only by rules.
- **Suggestion**: AI proposal (service classification or business function) with citations and a status
  (`pending`, `accepted`, `rejected`). Accepting turns the value into `human-confirmed`.
- **Provenance** on every value: `scanned`, `planned`, `ai-suggested`, `human-confirmed`.

Rules (each with tests):

| Rule | Severity |
|---|---|
| New ICT provider not in the register | HIGH |
| Data location outside EU/EEA, or different from the register | HIGH (unknown location: MEDIUM) |
| Change touches a resource that supports a critical function | HIGH |
| New service type for an existing provider | MEDIUM |
| Resource without `dora-function` / `dora-contract` tags | MEDIUM |

## 4. API (all under `/api`, JSON)

| Method | Path | Role |
|---|---|---|
| GET | `/health` | none |
| POST | `/auth/redeem` | invite code: returns server info and the username it is for |
| POST | `/auth/login` | username + password: tokens, or "password change required" |
| POST | `/auth/password` | change password (required after first login) |
| POST | `/auth/refresh` | new access token |
| GET, POST | `/admin/users`, `/admin/invites` | admin |
| POST | `/scan` | reviewer |
| GET | `/resources` | viewer |
| GET | `/register`, `/validation`, `/concentration` | viewer |
| GET | `/suggestions` | viewer |
| POST | `/suggestions/generate`, `/suggestions/{id}/accept`, `/suggestions/{id}/reject` | reviewer |
| POST | `/check` (plan JSON in, findings out) | reviewer |
| GET | `/export/csv`, `/export/pdf` | reviewer |

Errors: RFC 9457 problem details. Exact DTOs live in `dorai.shared.model` and are the source of truth.

## 5. Auth and connecting
1. Admin creates a user (username, role) and sets a temporary password.
2. Server creates an invite: one-time code, valid 72 hours. The link:
   `dorai://connect?server=https://dorai.firm.local:8443&code=K7Q2-9XPA&fp=sha256:AB12...`
   The admin sends the link; the temporary password goes through a different channel.
3. User opens the link (Android deep link; on desktop, paste it). The app connects only if the server's
   certificate matches `fp` (self-signed certificates work this way), then redeems the code.
4. User logs in with username and temporary password, and must set a new one before anything else.
5. Server returns a short-lived JWT access token and a refresh token. Passwords are hashed (Argon2 or bcrypt).
   Invite codes are single use and stored hashed.
6. Roles are checked on the server for every route. The app only hides what the role can't use.

Where the app stores tokens and the pinned fingerprint is a decision for the app lane.

## 6. Build phases

| Phase | Done means | Who |
|---|---|---|
| 0. Skeleton | `./gradlew build` green locally and in CI, every module runs | Ignjat (done) |
| 1. Contract | DTOs and API client in `shared`; server returns fixture data on every endpoint | Ignjat |
| 2. Slices in parallel | Server: DB, scan with fixtures, rules, register, validation. AI: Koog services with a fake model in tests. App: every screen against the fixture server | all |
| 3. Auth | Users, invites, login, forced password change, roles, TLS with fingerprint; app connect flow | Ignjat + Luka |
| 4. Real data | Live Azure and AWS collectors, real LLM, regulation ingestion into pgvector | Ignjat + David |
| 5. Export and CI | CSV and PDF export, `dorai check`, PR comment in GitHub Actions | Matija + Ignjat |
| 6. Demo | Demo data, rehearsed script, packaged desktop app and APK | all |

Until phase 3 lands, the app's server address is a setting (default `http://localhost:8080`)
and the server runs without auth in development.

## 7. Local development
- Server: `./gradlew :server:run`. Config through environment variables `DORAI_*`
  (port, database URL, data source fixtures or live, AI fake or real). Defaults are fixtures and a fake AI,
  so nobody needs cloud or model keys to work.
- Database: PostgreSQL 16 with pgvector in Docker: `docker compose up -d db` in `deploy/`
  (copy `.env.example` to `.env` first). Running the whole server for a firm: `docs/deploy.md`.
- Android emulator reaches the host machine at `http://10.0.2.2:8080`.
