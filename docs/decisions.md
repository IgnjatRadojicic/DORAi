# Decisions

One line each: date, who, decision, why. Newest at the bottom.

- 2026-11-21, team: Desktop and Android clients only, both with Compose Multiplatform. Why: one UI codebase, the two platforms the firms asked for.
- 2026-11-21, team: Server on Ktor with Exposed, PostgreSQL with pgvector, Flyway migrations. Why: Kotlin end to end, one database for register and regulation search.
- 2026-11-21, team: AI through Koog. Why: Kotlin-native agents and tool calling.
- 2026-11-21, team: Self-hosted server; invite link carries server address, one-time code and certificate fingerprint, never a password. Why: firms keep data on their own infrastructure; pinning makes self-signed certificates safe.
- 2026-11-21, team: Admin sets a temporary password that must be changed at first login; roles admin, reviewer, viewer. Why: simple, no external identity provider needed.
- 2026-11-21, Ignjat: Rules, validator and Terraform plan reader live in `shared` (`dorai.shared.rules`). Why: server and CLI must produce identical findings.
- 2026-11-21, Ignjat: compileSdk 37 (Compose 1.12 requires it), targetSdk 36, minSdk 26. Why: library requirement; target stays on the stable runtime behaviour.
- 2026-11-21, Ignjat: Contract keeps the established field names and JSON values; Kotlin types drop any suffix (`Finding`, `Resource`). Why: fixtures parse as they are and every lane works from the same shapes.
- 2026-11-21, Ignjat: Dates use `kotlinx-datetime` (`LocalDate`) and `kotlin.time.Instant`; money is `Double`, the server rounds to 2 decimals before sending. Why: plain JSON numbers, and `BigDecimal` is not available in common code.
- 2026-11-21, Ignjat: `DoraiApi` returns a sealed `ApiResult` (success or problem details) and never throws for HTTP or network errors. Why: screens must handle the error case explicitly.
- 2026-11-21, Ignjat: Phase 1 server serves the FinPay demo data (a fictional firm) from `server/src/main/resources/fixtures`; suggestions are decided in memory. Why: the apps and AI can start before the database exists.
- 2026-11-21, Ignjat: Password hashing with password4j (Argon2id). Why: pure Java, sane defaults, no native library to ship.
- 2026-11-21, Ignjat: AWS collectors use the AWS SDK for Kotlin. Why: coroutine-native, fits Ktor and Koog.
- 2026-11-21, Ignjat: Exposed DSL only, no DAO. Why: fewer moving parts.
- 2026-11-21, Ignjat: PDF report with OpenPDF. Why: high-level tables and paragraphs make the register report short to write.
- 2026-11-21, Ignjat: EBA CSV written with Apache Commons CSV. Why: correct quoting of commas and line breaks without custom code.
- 2026-11-21, Ignjat: App navigation with JetBrains Navigation Compose. Why: official multiplatform port, type-safe routes, per-screen ViewModels.
- 2026-11-21, Ignjat: App settings in DataStore Preferences; tokens encrypted with Android Keystore on Android and kept in the OS keychain (java-keyring) on desktop. Why: tokens stay secret at rest.
- 2026-11-21, Ignjat: File dialogs with FileKit. Why: one Compose API for desktop and Android, no platform code.
- 2026-11-21, Ignjat: Server generates a self-signed TLS keystore on first start (ktor-network-tls-certificates) unless one is configured. Why: zero setup for firms and the demo; the fingerprint goes in the invite link.
- 2026-11-21, Ignjat: ViewModel and Flow tests use Turbine. Why: short, readable assertions on StateFlow emissions.
