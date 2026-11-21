# PDF register report

The server produces a review report from the current register snapshot and deterministic rule findings.
The desktop downloads the finished document; it does not render a substitute report locally.

## Request and save

1. `GET /api/export/pdf` reads the current register and checks the server's configured plan.
2. `RegisterDocumentExporter` delegates to `RegisterPdfReport`, using the shared validator and configured
   `ReportDrafter`. Production wiring binds the drafter after AI configuration, so PostgreSQL installs use
   the same service as the rest of the server. Fixtures work without a database or model key.
3. OpenPDF produces an A4 report with embedded fonts, repeated page headers, table headers and page numbers.
4. The response is `application/pdf`, attachment `dorai-report.pdf`.
5. `ApiExportSource` calls `DoraiApi.exportPdf`. The existing ViewModel checks the signature, opens the
   platform save dialog and records success only after saving. The default filename is `compliance-report.pdf`.

When auth is enabled, the existing reviewer/admin requirement still applies. Viewers cannot export.
Rendering errors produce problem details before any attachment header is set.

## Contents and limits

- Entity, snapshot and preparation dates; provider, contract and business-function counts.
- Validation errors and warnings. A diagnostic PDF remains downloadable when CSV export returns 422.
- Annual contract amounts and provenance; complete overall totals and provider cost shares only when
  every amount is confirmed or collected, non-negative, references are valid and one currency is recorded.
  Planned amounts remain marked as planned. There is no currency conversion, partial overall total,
  cost allocation to functions or operational risk score inferred from cost share.
- Every supplied rule finding, including its original rule, severity, resource, detail and register templates.
- The colleague's cited AI summary and finding notes, explicitly marked as suggestions awaiting human
  confirmation. Malformed notes that add, drop or repeat findings are rejected. All rule findings remain
  in the report, including those beyond the drafting service's 50-finding limit.
- Field source records and stored regulatory citations. Unconfirmed field values and their reasons are
  omitted, including values that would otherwise appear in validation messages.

AI failure, insufficient evidence, an exception or a 10-second drafting timeout leaves the factual report
available with a neutral unavailable note. Parent coroutine cancellation still cancels the export.
No model error details or invented citations are printed.

The report is a draft, not a regulatory filing or a certificate of DORA compliance. The configured plan
is the same one used by the no-upload check endpoint. The export does not retain a user's previous upload
and does not perform a new live cloud scan. PDF prose is English; stored names and quotations retain their
original text. The desktop controls remain available in all four interface languages.

## Local verification

Start the fixture server with `:server:run` and open the desktop preview described in `matija-frontend.md`.
Use the same `DORAI_SERVER_URL` as the server port. In **Validation & export**, select **Save PDF report**,
choose a destination and open the saved file. Both save buttons are enabled in the preview.

```powershell
.\gradlew.bat :server:test :app:desktopTest :shared:jvmTest :cli:test
```

Tests parse real PDFs using PDFBox, check embedded fonts and Unicode, pagination, unchanged findings,
citations, missing and mixed-currency amounts, zero totals, AI fallback and cancellation. Endpoint tests
exercise PDF responses through the shared client, diagnostic reports on invalid registers, configured
drafting and error headers. Desktop tests verify the shared API-to-file-saver path for both formats.
Tests use fixtures and local fakes, without live clouds or model calls. Android compilation and picker
testing still require the Android SDK and a device or emulator.

Verified after integrating the latest shell on JDK 21: 290 tests pass, with 32 environment-dependent tests skipped. A real fixture-server
download returns HTTP 200 and a readable PDF; rendered pages were also inspected locally.
