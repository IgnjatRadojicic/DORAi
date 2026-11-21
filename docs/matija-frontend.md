# Matija frontend integration

The frontend is in `dorai.app.theme`, `components`, `findings`, `validation` and `export`.
It uses the existing Compose dependencies and provides light, dark and system appearances.
The app entry point and production navigation remain with Luka. Server exports use the existing shared contract.

## Desktop presentation

The shared frame uses a 244 dp navigation rail on wide windows and compact navigation on narrower
windows. Neutral surfaces follow the JetBrains direction while retaining the product's blue accent
and semantic severity colors. The visual references were [Linear's UI refresh](https://linear.app/changelog/2026-03-12-ui-refresh),
[Raycast's desktop controls](https://www.raycast.com/blog/a-fresh-look-and-feel), and
[JetBrains' new UI](https://www.jetbrains.com/help/idea/new-ui.html).

Feedback transitions last 120 ms, section and detail transitions 200 ms, and theme transitions 260 ms.
There are no perpetual decorative animations. The theme remains an icon-only toggle. Finding count
cards also filter the list; keyboard focus and pointer feedback are visible. Section scroll positions
are retained when switching pages.

`DesktopTitleBar` provides draggable window chrome with minimize, maximize/restore and close buttons.
It follows the same animated palette, including light mode. The preview uses an undecorated,
resizable Compose window; Luka can use the same component in the production entry point.
The preview opens with labeled example data. Keyboard controls: Ctrl+O opens a plan, Ctrl+Enter checks
it, and Ctrl+3 / Ctrl+4 switch between the assigned sections. Shared screens remain state-and-callback
components, and application navigation is still supplied by the host.

The header language picker provides English, Español, Français and Deutsch. Pass `language` to
`DoraiTheme` and `onLanguage` to `ControlCenterFrame`. It updates navigation, actions, headings,
filters, validation labels, frontend errors and accessibility names without replacing ViewModels.
Desktop file-dialog titles and replacement prompts use the current language too. Native operating
system picker controls follow the OS language. Example data is translated for the demo; live server
values, resource IDs, filenames and regulatory quotations are preserved. The catalog is offline and
has tests for translation completeness, unique keys and matching format arguments.

## Desktop preview

Run `dorai.app.findings.MatijaPreviewKt.main()` from the IDE. This standalone window shows sample
findings and validation, with a brief demo status and a theme toggle. Checks of selected
files and exports require the real shared API adapter. No sample export is presented as real data.
The normal `:app:run` still uses the existing application entry point.

The feature layout retains the supplied palette, tables and finding counters with compact desktop
headings and controls. It includes a plan selector,
a horizontally scrollable findings table, expandable evidence, and a combined validation and export
page. The window starts maximized so desktop scaling does not place controls outside the screen.
The theme button shows the current appearance: moon for dark, sun for light. A click switches to the
other appearance immediately, with no menu or visible label. The shared theme still accepts system
appearance when selected by the host. Fonts are bundled for offline use, with
their redistribution licenses. Only sections 03 and 04 are active in this scoped preview.

To explore the offline workflow, choose `Sample plan`, then `Check plan`. The bundled example
returns four illustrative findings. A copy of that plan can also be chosen or dropped into the upload
area on desktop. Other plans require the shared API adapter. Removing the selection resets results.
CSV and PDF downloads use the local server through `ApiExportSource`, including in fixture mode.
The validation view reads the same server's register report. Real downloads save only after the shared
API adapter supplies the document. See `csv-export.md` and `pdf-export.md` for the full flow.

From the repository root, the preview can also be launched from PowerShell after setting up JDK 21:

```powershell
New-Item -ItemType Directory -Force .gradle | Out-Null
@'
allprojects {
    tasks.withType(JavaExec).configureEach {
        if (path == ':app:run') {
            doFirst { mainClass.set('dorai.app.findings.MatijaPreviewKt') }
        }
    }
}
'@ | Set-Content .gradle/matija-preview.init.gradle -Encoding ascii
.\gradlew.bat :app:run --init-script .gradle/matija-preview.init.gradle
```

The generated local init script changes the launch class immediately before execution, after Compose
configures its normal entry point. Add `--offline` when the dependencies are already cached.

The feature window title is `DORAi`. A centered product name belongs to the
original app entry point. The local init script is not committed; use the IDE preview entry point
on other workstations, or recreate this local launch override.

## Shell integration for Luka

Wrap the existing shell in `DoraiTheme(mode)`. The shell owns the theme selection and persistence.
Obtain platform file access with `rememberWorkspaceFiles()` inside composition.
Create the following ViewModels using the shell's lifecycle owner, scoped to the connected account:

- `FindingsViewModel(findingsSource)` then `FindingsRoute(model, files, canCheck = reviewer)`.
- `ValidationViewModel(validationSource)` then `ValidationRoute(model)` for a standalone validation view.
- `ExportViewModel(exportSource, files, canExport = { currentRoleCanExport })` then
  `ExportRoute(model, canExport = reviewer)`.

For the combined fourth section, use `ValidationExportRoute(validationModel, exportModel,
canExport = reviewer)`. `ControlCenterFrame` supplies reusable visual chrome, while the host owns
navigation callbacks. Its section list is provided by the host, allowing Luka to wire all four sections.

Screens also accept state and callbacks directly. They never call an HTTP client.
Dispose account-scoped models on logout or server change so results are not shared across accounts.
The server must enforce roles; frontend role controls are a usability feature.

## Shared API integration for Ignjat

The shared DTOs and client are now available in `shared`. The frontend interfaces remain presentation
boundaries, not substitute wire DTOs. Wire them in the composition layer by delegating to
`dorai.shared.api`; keep HTTP, authentication, serialization and status handling there.

| Frontend boundary | Shared client behavior required |
| --- | --- |
| `FindingsSource.checkPlan` | POST `/api/check`, multipart part `plan`, original filename and JSON bytes; map the returned findings without changing rule, severity or resource |
| `ValidationSource.validateRegister` | GET `/api/validation`; preserve validity, counts, issue codes, template, field, message and entity reference |
| `ExportSource.download` | `ApiExportSource` calls GET `/api/export/csv` or `/api/export/pdf` and preserves the returned bytes |

Map wire severities `High`, `Medium`, `Low` to the presentation enum. Map `Error` and `Warning`
to validation presentation values. Carry provenance and all citation information through the adapter.
No new JSON encoding is defined in the UI. Return `FrontendFailure` only for messages safe to display;
unexpected exceptions get a generic message. Preserve coroutine cancellation.

The file picker accepts JSON plans up to 10,000,000 bytes. The server validates JSON and decides findings.
An explanation without a citation is withheld. A failed check or validation refresh clears stale results.
CSV exports are ZIP archives, not single CSV files. Empty, unexpected media types and invalid file
signatures are rejected before saving. Cancellation is separate from failure and success.

Desktop saving uses a native destination dialog, replacement confirmation and a temporary sibling file
before replacement. Android uses the system document provider. Platform integration requires no new
Gradle dependency or storage permission. Android picker callbacks should be smoke-tested on a device,
including rotation and cancellation.

## Remaining shared dependency

The deterministic rules and plan reader are now available in `shared`. The frontend on-device adapter
still needs to be connected. The frontend does not implement a competing rule engine.
Server-side document generation is outside this frontend task.

## Verification

Run `./gradlew :app:desktopTest :app:compileKotlinDesktop` and the Android compilation with SDK 37.
ViewModel tests use immediate offline fakes. They cover input validation, sorting and filtering,
stale results, cancellation, role restrictions, document rejection and save failures.

Verified on 2026-11-21 with JDK 21 after integration with the current main branch: all 18 frontend
tests pass. The desktop, shared, server and CLI suites report 111 passed tests and 20 database tests
skipped because Docker is unavailable. The full `build` task stops at the missing Android SDK
location, so Android compilation is still unverified locally. The IDE does not currently index these
Gradle source roots; Gradle results are the validation evidence.
