# Contributing to DORAi

DORAi is built by a team of four, each in their own lane. This page is the short version of how we work; the same
rules, plus the product rules, are in [CLAUDE.md](CLAUDE.md), which our AI assistants read too.

## Before you start

- Read the part of [docs/architecture.md](docs/architecture.md) and [docs/plan.md](docs/plan.md) that your task
  touches, and the recent lines of [docs/decisions.md](docs/decisions.md).
- Set up the prerequisites in the [README](README.md#prerequisites). `scripts/dev-local.sh` runs the whole app
  locally with demo data, a fake AI and no cloud keys.

## Lanes

| Person | Owns |
| --- | --- |
| Ignjat (integration owner) | Contract (DTOs, API client), server core, auth, database and migrations, collectors, rules, CLI, CI, Gradle files, merges |
| David | AI with Koog: classifier, function suggester, finding explainer, report drafter, regulation ingestion and search |
| Luka | App part 1: shell and navigation, connect, login and password change, inventory, AI review, Android app |
| Matija | App part 2: theme and shared components, findings, validation, export; the server CSV and PDF export |

- Don't edit another lane's files. Ask that person, or stub what you need on your side. Work outside your lane only
  when its owner agrees, and say so in the pull request.
- The contract (`dorai.shared.model`, `dorai.shared.api`) changes only through Ignjat. Need a field? Write him a note
  or a PR comment, and use a local stub until it lands.
- New dependencies: propose them; Ignjat adds them to Gradle and `libs.versions.toml`.

## Decisions

- Design and library choices belong to the person whose lane it is. Compare two or three options with their
  trade-offs, then choose.
- Record every decision as one line in [docs/decisions.md](docs/decisions.md): date, who, what, why.

## Branches and pull requests

1. Pull `main`, then create one branch per task: `<name>/<topic>`, for example `luka/connect-screen`.
2. Commit your own work. Commit messages say why, not only what.
3. Pull `main` again, run `./gradlew build`, and open a pull request to `main`.
4. Ignjat reviews and merges. Never push to `main`, never force-push.

No AI attribution in commits, pull requests or code comments (no `Co-Authored-By` trailer, no "Generated with" line).
No long dash or en dash anywhere: use a colon, a comma or a plain hyphen.

## Code

- Kotlin, official style; packages start with `dorai.`; small files, clear names.
- JSON: camelCase fields, enums as strings, errors as RFC 9457 problem details (`application/problem+json`).
- Server: Ktor routes stay thin, logic lives in services behind the interfaces in `dorai.server.core`; Exposed for
  data, Flyway for migrations.
- App: Compose Multiplatform in `app/commonMain`; platform code only in `desktopMain` / `androidMain`. State lives in
  ViewModels; screens get state and callbacks, never the API client.
- AI goes through Koog behind interfaces the server calls.
- Keep it simple: no abstraction or configuration beyond what the feature needs.

## Product rules

- Rules decide findings. The AI only classifies, suggests, explains and cites, and a person accepts or rejects every
  AI suggestion.
- Every AI answer carries a citation from the regulation, or says "insufficient evidence".
- Every value shows where it came from: `scanned`, `planned`, `ai-suggested` or `human-confirmed`.
- An invite link carries the server address, a one-time code and the certificate fingerprint, never a password.

## Tests

- `./gradlew build` must pass before every pull request; CI runs it too.
- Tests run offline: no cloud, no real AI, no network. Use fixtures and fakes.
- Each rule, validator check and endpoint gets a test. UI logic lives in ViewModels so it can be tested.

## Secrets and cloud resources

- Never commit secrets, keys, tokens, passwords, tenant or subscription IDs, `.env` files or `local.properties`.
- The server reads secrets from `DORAI_*` environment variables. Never paste them into a chat, an issue or a PR.
- Never create or delete cloud resources, or run `terraform apply` / `destroy`, without an explicit go-ahead.

Found a security problem? Don't open a public issue; see [SECURITY.md](SECURITY.md).
