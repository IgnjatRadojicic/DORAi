# Decisions

One line each: date, who, decision, why. Newest at the bottom.

- 2026-11-21, team: Desktop and Android clients only, both with Compose Multiplatform. Why: one UI codebase, the two platforms the firms asked for.
- 2026-11-21, team: Server on Ktor with Exposed, PostgreSQL with pgvector, Flyway migrations. Why: Kotlin end to end, one database for register and regulation search.
- 2026-11-21, team: AI through Koog. Why: Kotlin-native agents and tool calling.
- 2026-11-21, team: Self-hosted server; invite link carries server address, one-time code and certificate fingerprint, never a password. Why: firms keep data on their own infrastructure; pinning makes self-signed certificates safe.
- 2026-11-21, team: Admin sets a temporary password that must be changed at first login; roles admin, reviewer, viewer. Why: simple, no external identity provider needed.
- 2026-11-21, Ignjat: Rules, validator and Terraform plan reader live in `shared` (`dorai.shared.rules`). Why: server and CLI must produce identical findings.
- 2026-11-21, Ignjat: compileSdk 37 (Compose 1.12 requires it), targetSdk 36, minSdk 26. Why: library requirement; target stays on the stable runtime behaviour.
