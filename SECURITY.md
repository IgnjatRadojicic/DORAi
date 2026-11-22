# Security policy

DORAi holds a financial entity's Register of Information and reads its cloud, so we treat security reports as a
priority.

## Supported versions

| Version | Supported |
| --- | --- |
| Latest release (1.1.x) | Yes |
| Older releases | No: update to the latest release |

## Reporting a vulnerability

Please do not open a public issue, discussion or pull request for a vulnerability.

Report it privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**.
Include what you found, how to reproduce it, the version, and the impact you expect. We will confirm we received it
and tell you when a fix is released.

Never include real secrets, register data or cloud identifiers from a firm in a report.

## Security model in short

- **Self-hosted.** The firm runs the server; register data, cloud credentials and the AI key stay on its
  infrastructure. Outbound calls are read-only (clouds, identity providers), on request (GLEIF), or to the AI
  provider the firm chooses; the default AI is an offline fake model.
- **Connecting.** An invite link carries the server address, a single-use code (valid 72 hours, stored as a hash) and
  the SHA-256 fingerprint of the server's self-signed certificate. The apps trust only that certificate. Behind a
  Cloudflare Tunnel (`DORAI_PROXY=cloudflare`) the link carries no pin and the system's certificate authorities apply.
- **Accounts.** Argon2id password hashes; a temporary password must be changed at first login; repeated wrong
  passwords lock the username; rate limits on redeem, login and password change.
- **Sessions.** Access tokens are signed JWTs valid 15 minutes; refresh tokens are stored as hashes and rotated, and
  reusing an old one ends the session. Disabling a user or changing a password ends their sessions at once.
- **Roles.** `admin`, `reviewer`, `viewer`, checked on every route; new write routes are closed by default.
- **Tokens on devices.** Android Keystore on Android, the operating system keychain on desktop.

More detail: [docs/architecture.md](docs/architecture.md#connecting-without-a-password).

## Running it safely

- Give the server read-only cloud credentials: an Azure service principal with Reader, AWS read-only access to tags
  and cost data.
- Keep `.env` readable only by the admin (`chmod 600 .env`). `DORAI_JWT_SECRET` must be at least 32 characters.
- Keep plain HTTP (8080) on the host only; the apps use HTTPS on 8443.
- Copy the nightly backups offsite; a backup on the same disk does not survive a disk failure.

Setup details: [docs/deploy.md](docs/deploy.md).
