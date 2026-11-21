# Register CSV export

CSV generation runs on the Kotlin server. The desktop client requests the archive through `DoraiApi`
and saves the returned bytes using the platform file picker. No document is generated in the UI.

## Request flow

1. The client requests `GET /api/export/csv`.
2. The existing register repository supplies the current snapshot, from fixtures or PostgreSQL.
3. `RegisterCsvExporter` runs the shared register validator. Errors return HTTP 422 problem details;
   warnings are allowed. Existing authentication requires a reviewer or admin when auth is enabled.
4. Apache Commons CSV writes the five tables, using UTF-8 without a BOM, comma separators and CRLF records.
5. The server returns `application/zip` with the attachment name `dorai-register.zip`.
6. The client checks the file signature, opens a destination dialog and reports success after saving.

## Archive contents

| Entry | Data |
| --- | --- |
| `B_01.01.csv` | Financial entity, 6 columns |
| `B_02.01.csv` | Contractual arrangements, 5 columns |
| `B_02.02.csv` | Contract services and related identifiers, 18 columns |
| `B_05.01.csv` | Providers and known single-currency cost totals, 12 columns |
| `B_06.01.csv` | Business functions and criticality, 10 columns |
| `manifest.json` | Snapshot time, row counts, unsupported columns, missing cells and omitted AI fields |
| `README.txt` | Package scope and review notes |

Column identifiers and ordering follow the five selected templates in
[CIR (EU) 2024/2956](https://eur-lex.europa.eu/eli/reg_impl/2024/2956/oj/eng).
The simplified domain cannot fill all regulatory columns. The manifest sets `submissionReady` to
`false`; this is a draft review package, not a complete regulatory filing or xBRL-CSV submission.

Unconfirmed `ai-suggested` values are blank in every table and excluded from derived totals.
The manifest records their field paths without exposing the omitted values. Missing provider LEIs
leave the LEI and identifier-type cells empty; internal provider IDs never replace LEIs.
Unavailable reporting dates, classifications and risk assessments are not inferred.

Amounts use exact decimal arithmetic and a decimal point, independent of the app language or server
locale. Provider totals are blank if any amount is unavailable or currencies differ. Text that could
be interpreted as a spreadsheet formula is prefixed with an apostrophe. Commas, quotes, Unicode and
embedded line breaks are handled by the CSV library.

Rows are sorted by identifiers. ZIP entries have a fixed timestamp, making the archive reproducible
for the same snapshot regardless of input collection order or the machine's timezone.

## Local use

Use JDK 21 and the Gradle setup in the repository README. Start the server with fixture defaults:

```powershell
.\gradlew.bat :server:run
```

Start the desktop feature client using the launch instructions in `matija-frontend.md`. Open
`Validation & export`, refresh validation if needed, and select `Save CSV package`.
The client defaults to `http://localhost:8080`; `DORAI_SERVER_URL` changes that address for the feature
launcher. Authenticated production composition should pass its authenticated `DoraiApi` to
`ApiExportSource` and `ApiRegisterValidation`.

The validation view uses the server's actual report. No local server means a connection error, not a
successful sample download. The fixture server is useful for checking the CSV workflow without a
database or cloud credentials. PostgreSQL mode uses the same exporter through the repository interface.

PDF export is also available; see `pdf-export.md` for its contents and diagnostic behavior.
`availableFormats` separates supported downloads from fixture/demo labeling.

## Tests

```powershell
.\gradlew.bat :server:test :app:desktopTest :shared:jvmTest :cli:test
```

Exporter tests cover exact headers and row counts, relationships, decimal totals, provenance,
missing fields, quoting, Unicode, formula-like text, empty collections and deterministic archives.
Endpoint tests exercise real ZIP responses and 422 problem details through the shared client.
Desktop integration tests use a loopback-only fake HTTP server and verify that the returned archive
reaches the file saver. No live cloud, model, or external service is used by these tests.

Verified with the updated main on JDK 21: 245 tests pass and 32 environment-dependent tests are skipped.
Android compilation still requires the Android SDK described in the repository README.
