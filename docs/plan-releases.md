# Versions and releases

How DORAi gets version numbers, how one tag ships every platform, and how the apps and the server
refuse to talk across incompatible versions. Two phases, each its own PR.

These files are in Ignjat's lane (Gradle, `.github/**`, `shared/**`, `server/**`). David implements them
with Ignjat's agreement; Ignjat reviews and merges as usual.

## Rules

**One version for everything.** A tag `v1.7.0` releases the server image, the desktop installers, the
Android APK and the CLI, all as `1.7.0`. An app release can be identical to the one before it when only
the server changed; nobody has to install it, because same major means compatible.

| Part | Goes up when | Example |
|---|---|---|
| Major | A breaking contract change: a field or endpoint removed or renamed, a meaning changed | `1.9.0` to `2.0.0` |
| Minor | New features, including additive contract changes (new optional field, new endpoint) | `1.6.0` to `1.7.0` |
| Patch | Bug fixes only, no contract change | `1.7.0` to `1.7.1` |

- **Compatible means same major.** Client `1.6` and server `1.9` work together; `1.6` and `2.0` do not.
- **Hard block across majors.** The server answers every API call from a client with another major with a
  problem, and the client shows who must update. No "continue anyway".
- **Within a major, both sides tolerate the other.** `DoraiJson` already ignores unknown fields; every new
  field gets a default, so an older server that does not send it does not break a newer client.
- **Versions are plain `X.Y.Z`**, major at least 1 (macOS refuses `0.x`), major and minor at most 255
  (Windows MSI). No `-beta` suffix: a beta is a GitHub pre-release instead.
- **Not every merge is a release.** Whoever cuts a release bumps the version in a PR, then tags `main`.
  The first app release is `1.0.0`.

## Phase 1: one version, releases for every platform

### 1a. One source for the version (PR `david/version-source`)

1. `gradle.properties` gets `dorai.version=1.0.0`. It is the only place the version is written.
   It is readable locally, and a version bump shows up as a one-line PR.
2. The root build reads it and checks the format (`X.Y.Z`, the limits above); a bad value fails the build.
3. A small Gradle task generates `dorai.shared.DoraiVersion` into `shared` (common code), so the server,
   the apps and the CLI all know their own version at runtime. Same idea as Android's `BuildConfig`, no plugin.
4. `app/build.gradle.kts`: `packageVersion` comes from the property, and `windows { upgradeUuid = ... }`
   gets a fixed UUID, so installing `1.2.0` upgrades `1.1.0` instead of installing next to it.
5. `androidApp/build.gradle.kts`: `versionName` from the property, `versionCode` worked out as
   `major * 10000 + minor * 100 + patch` (`1.2.3` gives `10203`), so it always goes up.
6. `dorai --version` prints the version; the desktop and Android apps show it somewhere small (Luka
   picks where, the value comes from `DoraiVersion`).
7. Tests: version parsing and limits, the `versionCode` formula.

### 1b. Release workflow (PR `david/release-workflow`)

New `.github/workflows/release.yml`, started by a `v*` tag, next to the existing `server-image.yml`:

| Job | Runner | Builds | Asset name |
|---|---|---|---|
| `check` | ubuntu | Fails if the tag is not `v` + `dorai.version` | |
| `desktop` (matrix) | windows-latest | `:app:packageMsi` | `DORAi-windows.msi` |
| | macos-latest | `:app:packageDmg` | `DORAi-macos.dmg` |
| | ubuntu-latest | `:app:packageDeb` | `DORAi-linux.deb` |
| `android` | ubuntu | `:androidApp:assembleRelease`, signed | `DORAi-android.apk` |
| `cli` | ubuntu | `:cli:distZip` | `dorai-cli.zip` |
| `release` | ubuntu | Collects all assets, writes `SHA256SUMS`, runs `gh release create --draft` | |

- **Asset names carry no version**, so the website can link to
  `https://github.com/<owner>/<repo>/releases/latest/download/DORAi-windows.msi` and never change.
  The version is in the release title and in the app itself.
- **The release starts as a draft.** A person checks it and publishes it, ticking "pre-release" for a beta.
  `latest/download` only ever points at the newest published, non-pre-release version.
- **Android signing.** One release key, made once with `keytool`, kept out of the repo. Four repository
  secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`), which Ignjat adds as repository admin. The key is backed up somewhere safe:
  if it is lost, users must uninstall before the next update installs. Local builds keep debug signing.
- **Not signed or notarized for desktop.** Windows shows SmartScreen and macOS Gatekeeper warns on first
  open (right-click, Open). Fine for the hackathon; code signing certificates are a later decision.
  `macos-latest` builds for Apple Silicon only.
- Uses only `gh` (already on the runners) and the actions the repo already uses; no new third-party action.

### Cutting a release

1. PR that bumps `dorai.version` (and nothing else), merged to `main`.
2. `git tag v1.2.0 <merge commit>` and `git push origin v1.2.0`.
3. `server-image` publishes the image; `release` builds the draft.
4. Check the draft (all six files there, the APK installs over the previous one), then publish it.

### Done when

- A test tag on a fork produces a draft release with all six files and the server image.
- The MSI upgrades an older install in place; the APK installs over the previous release.
- `./gradlew build` passes; versions in the server, apps and CLI all print the same number.

## Phase 2: versions on every API call (PR `david/api-versions`)

### Wire format

- Every request from `DoraiApi` carries `Dorai-Client-Version: 1.7.0`.
- Every server response carries `Dorai-Server-Version: 1.9.0`.
- `Health` gets `version` (with a default, so a client can still read an older server's health).
  `/api/health` is never blocked: the login page's connection check uses it to show a mismatch before
  anyone logs in.

### Server

- A small Ktor plugin on `/api/**` (except `/api/health`) compares the majors.
- Client major lower: problem `client-outdated`, the user must update the app.
- Client major higher: problem `server-outdated`, the admin must update the server.
- Both as `application/problem+json`, same style as the existing types in `Problems.kt`, status
  `426 Upgrade Required`, the server version in the response header.
- A request without the header passes (curl, scripts, health checks). Every DORAi client sends it.

### Clients

- `DoraiApi` adds the header to every request and exposes the server version it last saw.
- `dorai.shared` gets `DoraiVersion.isCompatible(other)` (same major) and, for later, a helper to check
  that a server's minor is high enough for a given feature. No feature uses it yet.
- Apps: both problem types become a clear blocking message ("Update DORAi to version 2" or "Your
  server runs 1.9; this app needs 2.x, ask your admin"). The login page shows the same from `/api/health`.
  The screens are Luka's; this phase supplies the state, Luka places the message.
- CLI: a mismatch prints the same message and exits with its own exit code, so a CI run fails clearly.

### Tests

- Server: same version, different minor, client major lower, client major higher, missing header,
  `/api/health` never blocked, response header always present.
- Client: header sent on every call, server version read from the response, both problem types mapped.
- CLI: exit code on mismatch.

### Done when

- An app built as `2.0.0` against a `1.x` server is blocked with the right message, on login and on every call.
- `1.6` against `1.9` works with no warning.
- `./gradlew build` passes offline.

## Open choices (confirm before each phase)

| Choice | Recommendation | Alternative |
|---|---|---|
| Where the version lives | `gradle.properties`, tag must match it | CI passes it from the tag; local builds then need a placeholder version |
| Release goes out as | Draft, a person publishes | Published at once by the workflow |
| Request without a version header | Allowed | Blocked like a mismatch |
| Status for a mismatch | `426 Upgrade Required` | `409 Conflict` |
