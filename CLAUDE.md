# DORAi

DORAi keeps a financial entity's DORA Register of Information in step with its real cloud.
Self-hosted Ktor server, Compose clients for desktop and Android, a CLI for CI.
Architecture, auth and build phases: `docs/plan.md`. Decisions: `docs/decisions.md`.
Read the relevant section of the plan before a task; don't re-read it whole every time.

Team of four, each person in their own lane. Your personal brief (sent separately) says which lane
is yours. If you don't know whose session this is, ask: "Are you Ignjat, David, Luka or Matija?" and stay in that lane.

## Product rules (never break these)
- **Rules decide findings.** Findings come from deterministic rules in code. The AI only classifies,
  suggests, explains and cites. A person accepts or rejects every AI suggestion.
- Every AI answer carries a citation from the regulation, or says "insufficient evidence". Never an uncited claim.
- Every value shows where it came from: `scanned`, `planned`, `ai-suggested` or `human-confirmed`.
- The server is self-hosted by the firm. An invite link carries the server address, a one-time code and
  the certificate fingerprint. **Never a password.**
- Roles: `admin` (users, invites, settings), `reviewer` (scan, accept or reject, export), `viewer` (read only).
- The admin sets a temporary password; the user must change it at first login.

## Lanes and packages

| Person | Owns | Packages / paths |
|---|---|---|
| Ignjat (integration owner) | Contract (DTOs, API client), server core, auth, DB and migrations, collectors, rules, CLI, CI, merges | `shared/**`, `server/**` except `dorai.server.ai` and `dorai.server.export`, `cli/**`, `.github/**`, Gradle files |
| David | AI with Koog: classifier, function suggester, finding explainer, report drafter, regulation ingestion and search (pgvector) | `server/src/*/kotlin/dorai/server/ai/**`, prompts under `server/src/main/resources/ai/` |
| Luka | App part 1: shell and navigation, connect via invite, login and password change, inventory (scan, resources), AI review | `dorai.app.shell`, `dorai.app.connect`, `dorai.app.auth`, `dorai.app.inventory`, `dorai.app.review`, `androidApp/**` |
| Matija | App part 2: theme and shared components, findings, validation, export; later the server CSV/PDF export | `dorai.app.theme`, `dorai.app.components`, `dorai.app.findings`, `dorai.app.validation`, `dorai.app.export`, later `dorai.server.export` |

- Don't edit another lane's files. Say what you need from that person, or stub it on your side.
- **The contract belongs to Ignjat.** DTOs in `dorai.shared.model` and the client in `dorai.shared.api`
  change only through him. If you need a field, write a short note or a PR comment for Ignjat; until it lands, use a local stub.
- Gradle files and `libs.versions.toml`: propose the dependency, Ignjat adds it.

## Decisions are the person's
- Every design or library choice belongs to the person in that lane. The AI gives 2 or 3 options with a
  recommendation and the trade-off, then **waits** for the person to choose. Settled facts (a version, an API) are just stated.
- Record each decision in `docs/decisions.md`: one line with the date, who, what, why.
- Explain what you are about to do before writing larger pieces of code.

## Git
- One branch per task: `<name>/<topic>` (for example `luka/connect-screen`, `david/koog-explainer`).
- The person commits their own work and opens a PR to `main`. **Ignjat reviews and merges.** Never push to `main`, never force-push.
- Pull `main` before starting and before opening a PR.
- Commit messages say why. **No `Co-Authored-By` trailer, no "Generated with" line, no AI attribution
  of any kind** in commits, PRs or code comments.
- No long dash or en dash anywhere (commits, docs, comments, UI text). Use a colon, comma or a plain hyphen.

## Code
- Kotlin, official style. Packages start with `dorai.`. Small files, clear names.
- JSON: camelCase fields, enums as strings, errors as RFC 9457 problem details (`application/problem+json`).
- Server: Ktor routes stay thin; logic in services; Exposed for data; Flyway for migrations.
- App: Compose Multiplatform in `app/commonMain`; platform code only in `desktopMain` / `androidMain` (`expect`/`actual` when needed).
  State in ViewModels; screens get state and callbacks, not the API client.
- AI: through Koog, behind interfaces the server calls. Tests use a fake model, never the network.
- Keep it simple: no abstraction or config beyond what the feature needs.

## Tests
- `./gradlew build` must pass before every PR (CI runs it too).
- Tests run offline: no cloud, no real AI, no network. Use fixtures and fakes.
- Each rule, validator check and endpoint gets a test. UI logic lives in ViewModels so it can be tested.

## Secrets
- Never commit secrets, keys, tokens, passwords, tenant or subscription IDs, `.env` files or `local.properties`.
- Secrets come from environment variables on the server (`DORAI_*`). Never paste them into chat.
- Never create or delete cloud resources, or run `terraform apply/destroy`, without an explicit go-ahead.

## Token hygiene
Keep tool output small: `git status -sb`, `git diff --stat`, `./gradlew build -q`, tests filtered to failures.
