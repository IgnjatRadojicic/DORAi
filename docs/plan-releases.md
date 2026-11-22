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
- **Versions are plain `X.Y.Z`**: major 1 to 255 (macOS refuses `0.x`, Windows MSI stops at 255), minor and
  patch 0 to 99 (so Android's `versionCode` always goes up). No `-beta` suffix: a beta is a GitHub
  pre-release instead.
- **Not every merge is a release.** Whoever cuts a release bumps the version in a PR, then tags `main`.
  The first app release is `1.0.0`.

## Phase 1: one version, releases for every platform

### 1a. One source for the version (PR `david/version-source`)

1. `gradle.properties` gets `dorai.version=1.0.0`. It is the only place the version is written.
   It is readable locally, and a version bump shows up as a one-line PR.
2. The root build reads it, checks the format (`X.Y.Z`, the limits above) and sets it as every module's
   version; a bad value fails the build.
3. A small Gradle task generates `dorai.shared.DoraiBuild.VERSION` into `shared` (common code), so the server,
   the apps and the CLI all know their own version at runtime. Same idea as Android's `BuildConfig`, no plugin.
4. `app/build.gradle.kts`: `packageVersion` comes from the property, and `windows { upgradeUuid = ... }`
   gets a fixed UUID, so installing `1.2.0` upgrades `1.1.0` instead of installing next to it.
5. `androidApp/build.gradle.kts`: `versionName` from the property, `versionCode` worked out as
   `major * 10000 + minor * 100 + patch` (`1.2.3` gives `10203`), so it always goes up.
6. `dorai --version` prints the version; the desktop and Android apps show it somewhere small (Luka
   picks where, the value comes from `DoraiBuild.VERSION`).
7. Tests: `dorai --version` prints the product version. A bad version fails the build itself.

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
- **Desktop signing, when the certificates are there.** Windows: a code-signing certificate (from a
  certificate authority such as DigiCert or Sectigo) as `WINDOWS_CERTIFICATE_PFX_BASE64` and
  `WINDOWS_CERTIFICATE_PASSWORD`; `signtool` signs the MSI with a timestamp. macOS: an Apple Developer ID
  Application certificate as `MACOS_CERTIFICATE_P12_BASE64` and `MACOS_CERTIFICATE_PASSWORD`, its name as
  `MACOS_SIGN_IDENTITY`, and for Apple's notarization `MACOS_NOTARY_APPLE_ID`, `MACOS_NOTARY_PASSWORD` (an
  app-specific password) and `MACOS_TEAM_ID`. Without them the installer is built unsigned and the run shows a
  warning: Windows SmartScreen and macOS Gatekeeper then warn on first open. The Linux package is not signed;
  `SHA256SUMS` lets users check it.
  `macos-latest` builds for Apple Silicon only.
- Uses only `gh` (already on the runners) and the actions the repo already uses; no new third-party action.

### Android release key (once)

Made once by whoever holds it, never committed (`*.jks` is ignored), backed up outside the repo:

```bash
keytool -genkeypair -v -keystore dorai-release.jks -alias dorai -keyalg RSA -keysize 4096 -validity 10000
```

Then a repository admin adds four secrets under Settings, Secrets and variables, Actions:
`ANDROID_KEYSTORE_BASE64` (output of `base64 -w0 dorai-release.jks`), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` (`dorai`) and `ANDROID_KEY_PASSWORD`. Without them the `android` job fails with
a message naming the missing secret. The build reads them as `DORAI_ANDROID_*` environment variables.

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
- A request without the header is blocked too (`400`, problem `client-version-missing`): every caller of the
  API states its version. `/api/health` stays open, and scripts that call the API (for example
  `scripts/dev-local.sh`) send the header.

### Clients

- `DoraiApi` adds the header to every request and exposes the server version it last saw.
- `dorai.shared` gets a `Version` type with `isCompatibleWith(other)` (same major). It is comparable, so a
  feature that needs a newer server within the major can check `api.serverVersion >= Version(1, 7, 0)`.
  No feature uses that yet.
- Apps: the problem's title and detail already say who must update ("Update DORAi" or "Server needs an
  update", with both versions), and every call fails, so the app is blocked from the first call (login).
  A dedicated screen, and a warning on the login page from `Health.version`, are Luka's to add.
- CLI: nothing to do. `dorai check` runs the rules offline and never calls the server.

### Tests

- Server: same version, different minor, client major lower, client major higher, missing or malformed header,
  `/api/health` never blocked, response header always present.
- Client: header sent on every call, server version read from the response, both problem types mapped.

### Done when

- An app built as `2.0.0` against a `1.x` server is blocked with the right message, on login and on every call.
- `1.6` against `1.9` works with no warning.
- `./gradlew build` passes offline.

## Choices made

| Choice | Decided |
|---|---|
| Where the version lives | `gradle.properties`; the release fails if the tag does not match it |
| Release goes out as | A draft; a person checks it and publishes it |
| Request without a version header | Blocked; only `/api/health` stays open |
| Status for a major mismatch | `426 Upgrade Required` |
