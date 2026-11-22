## Server

For the IT team that runs DORAi for the firm. The server runs in Docker.

| Download | What it is |
|---|---|
| `dorai-server-docker-compose.zip` | Docker Compose setup: `compose.yaml`, `.env.example`, `backup.sh` and the install guide, set to this release |
| `ghcr.io/ignjatradojicic/dorai-server:{{VERSION}}` | Server image for Linux amd64 and arm64, pulled by the Compose setup |

Install: unzip, copy `.env.example` to `.env` and fill it in, then run `docker compose up -d`.
Updating an existing server: set `DORAI_VERSION={{VERSION}}` in `.env`, then `docker compose pull` and `docker compose up -d`.

## Apps

For everyone who works on the register. Each app connects to your firm's server with an invite link.

| Platform | Download | Format |
|---|---|---|
| Windows, Intel and AMD | `dorai-app-windows-amd64.msi` | Windows Installer (.msi) |
| Windows on ARM | `dorai-app-windows-arm64.msi` | Windows Installer (.msi) |
| macOS, Apple Silicon | `dorai-app-darwin-arm64.dmg` | Disk image (.dmg) |
| macOS, Intel | `dorai-app-darwin-amd64.dmg` | Disk image (.dmg) |
| Linux (Debian, Ubuntu), x86-64 | `dorai-app-linux-amd64.deb` | Debian package (.deb) |
| Linux (Debian, Ubuntu), ARM | `dorai-app-linux-arm64.deb` | Debian package (.deb) |
| Android 8.0 or later | `dorai-app-android-universal.apk` | Android package (.apk) |

## Command line

| Download | What it is |
|---|---|
| `dorai-cli-jvm.zip` | `dorai` command for CI pipelines and scripts, any system with Java 21 (.zip) |

## Verify your download

`SHA256SUMS` holds the SHA-256 checksum of every file above. GitHub also shows each file's checksum next to it.
