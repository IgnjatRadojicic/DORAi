# Plan: a complete, submittable Register of Information

2026-11-21. The choices in section 9 were taken on 2026-11-21 (the recommended options) and are recorded in
`docs/decisions.md`. All lanes are built on one branch, `ignjat/extra-compliance`.

Sources: CIR (EU) 2024/2956 Annex I (bundled, `server/src/main/resources/ai/regulations/cir-2024-2956.pdf`),
cited below by column code; DORA (EU) 2022/2554 (bundled); EBA material on the reporting package (links in section 10).

## 1. What "full compliance" means here

DORAi produces, for a reference date, a report package that:

1. contains all 15 templates (B_01.01 to B_07.01 and B_99.01), each listed in the filing indicators;
2. has every mandatory column filled, and every conditional column filled whenever its condition holds
   (most of B_02.02 only applies to services that support a critical or important function);
3. uses the exact column codes, value codes and file layout of the current EBA DORA taxonomy;
4. passes the taxonomy checks and the EBA validation rules in an offline validator;
5. exports only values with provenance `scanned` or `human-confirmed`, or values DORAi computes from them (shown as "derived"). A pending AI suggestion
   never reaches the export; it shows as "needs input".

Out of scope: submitting to the national authority's portal (the firm does that), the NCA's own Excel templates.

## 2. Where we stand

- Model covers parts of 5 templates (B_01.01, B_02.01, B_02.02, B_05.01, B_06.01).
- No exporter: `RegisterExporter` is an interface only, fixture wiring passes `exporter = null`.
- Register is read only: `GET /register`, data from `finpay-register.json`.
- Validator checks what the model has; it does not know which columns the RoI needs or when.

## 3. Phase 0: the spec in the repo (Ignjat, first, blocks everything)

Nothing below is built from memory.

- Download (with the team's go-ahead) the current EBA DORA taxonomy package and the EBA validation rules
  for the RoI. Note the release used in `docs/decisions.md`.
- Extract once into:
  - `docs/roi-spec.md`: per template, each column: code, name, type, mandatory condition, closed list;
  - `shared/.../roi/RoiSpec.kt`: the same as data (template code, column code, type, value codes).
    Exporter, readiness check and app labels all read from this one table.
- Closed lists must use the taxonomy's value codes, not the CIR's numbered text ("1. standalone arrangement").

Done when: every column in the CIR has a row in `RoiSpec`, with its condition, and a test checks the count per template.

## 4. Phase 1: contract and database (Ignjat)

### 4.1 Model changes (`dorai.shared.model`)

| Change | Why (column) |
|---|---|
| `Register.entities: List<Entity>` instead of one `FinancialEntity`; `Register.branches` | B_01.02, B_01.03; a solo firm is one entity with hierarchy "not part of a group" (B_01.02.0050 option 4) |
| `Entity`: type, competent authority, hierarchy, parent LEI, dates of integration and deletion, currency, total assets | B_01.01.0040 to 0050, B_01.02.0040 to 0110 |
| `Contract`: type (standalone, overarching, subsequent), overarching reference, signing entities, termination reason, status active or ended | B_02.01.0020, 0030; B_03.01; B_02.02.0090 |
| Ended contracts are kept, never deleted | B_02.02.0080 and 0090 report them with the end or termination date |
| Start and end date move to `ContractService`; null end exports as `9999-12-31` | B_02.02.0070, 0080 |
| `ContractService`: user entity (and branch), notice periods, governing law, country of provision, stores data, storage countries (list), processing countries (list), sensitivity, reliance | B_02.02.0020, 0100 to 0180; B_04.01. Several countries mean several rows (0150, 0160) |
| `Provider`: code and code type (LEI, EUID, or country + CRN/VAT/PNR/NIN), extra code, legal name, Latin name, type of person, ultimate parent code and type | B_05.01.0010 to 0120 |
| `SupplyChainLink`: contract, service type, provider, rank, recipient provider | B_05.02 |
| `BusinessFunction`: licensed activity, criticality (yes, no, not assessed), reasons, last assessment date, RTO, RPO, impact | B_06.01. Replaces `isCritical: Boolean` |
| `ServiceAssessment`: per contract and service type that supports a critical function: substitutability, reason, last audit date, exit plan, reintegration, impact, alternatives | B_07.01 |
| `TermDefinition`: column, option, the firm's own meaning | B_99.01, seeded with defaults the admin edits |
| `ReportingParameters`: reference date, entity, currency | `parameters.csv`; replaces using `Register.asOf` |

Every value a person or the AI can set is a `RegisterField`, so it keeps its provenance. Decision D2 covers typed values.

### 4.2 Database and fixtures

- Flyway V4: new tables and columns, data moved from the old columns. One migration test on a copy of the V3 schema.
- `finpay-register.json` filled for every template, including one ended contract, one subcontractor chain,
  one non-critical function, so every rule and writer has a case.

### 4.3 Editing API

The register stops being read only. Thin routes, logic in a `RegisterEditService`.

- `PUT`/`DELETE` per item: entities, branches, providers, contracts, services, supply chain, functions,
  assessments, definitions. Role `reviewer`; entity settings and definitions `admin`.
- A person's edit sets provenance `human-confirmed` with who and when. Accepting a suggestion does the same.
- Each route gets a test, including the role check.

Done when: API client in `dorai.shared.api` has the new calls, `./gradlew build` green, app lanes pull `main`.
This is the only step the other lanes must wait for.

## 5. Phase 2: four lanes in parallel

### 5.1 Rules and readiness (Ignjat, `shared/rules`)

- `ExportReadiness`: for each template, row and column in `RoiSpec`, report "missing" only when the column's
  condition applies (for example B_02.02.0170 only when the service stores data and supports a critical function).
- Format checks: ISO 8601 dates, ISO 3166-1 alpha-2, ISO 4217, LEI checksum, function id `F` + number (B_06.01.0010).
- Code rules: legal persons only LEI or EUID, outside the EU only LEI (B_05.01.0020). `missing-lei` becomes an error for legal persons.
- Cross references: every contract, provider, function, branch and entity referenced exists; B_05.02 rank 2+ points at a provider one rank up.
- Then the EBA validation rules from Phase 0, one check and one test each.

### 5.2 Scan and derived values (Ignjat, collectors)

| Column | From |
|---|---|
| B_02.02.0130, 0150, 0160 | resource `country`, per service via `contractId` |
| B_02.02.0140 | resource type table: storage, databases, queues mean "yes" |
| B_02.01.0050, B_05.01.0100 | `monthlyCost` x 12, summed per contract and per provider |
| B_01.02, B_03.01, B_03.02, B_04.01 (solo) | the one entity and `contract.providerId` |
| B_05.02 rank 1 | direct provider per contract and service type |

Provenance `scanned` or `derived`, with the rule as the reason.

### 5.3 AI suggestions (David)

New suggestion kinds (the enum is in the contract, so Ignjat adds them first):
sensitivity (B_02.02.0170), reliance (0180), licensed activity (B_06.01.0020), ultimate parent (B_05.01.0110),
subcontractor candidates (B_05.02, from a fixture of published subprocessor lists), B_07.01 drafts.
Each carries a citation or says "insufficient evidence". A person accepts or rejects every one.

### 5.4 Export (Matija, `dorai.server.export`)

- One writer per template: `Register -> rows`, columns from `RoiSpec`. One golden-file test per template.
- Package writer: `META-INF/reportPackage.json`, `reports/report.json`, `parameters.csv`,
  `FilingIndicators.csv` (all 15, capitals), one CSV per template, UTF-8.
- Refuses to build when `ExportReadiness` has errors, unless the caller asks for a draft (marked as draft).
- PDF report gets the readiness summary.

### 5.5 App (Luka and Matija; split proposed in D5)

- Entity settings (admin), providers, contracts, services, supply chain, functions, assessments, definitions.
- Plain forms. Each field shows its provenance. Closed lists as dropdowns from `RoiSpec`.
- Validation screen shows readiness per template and column, with a link to the form that fixes it.
- Review screen handles the new suggestion kinds.

## 6. Phase 3: proof

- Golden package from the full fixture, checked into tests.
- Offline validation of that package against the taxonomy (candidate: Arelle with the EBA taxonomy package,
  run in CI with no network). If that is not workable, a manual check per taxonomy release.
- A dry run with the demo firm's NCA test portal, if it has one. The firm submits, not us.

## 7. Groups (only if D1 says so)

Many entities and branches in forms, intra-group contracts (B_02.03, B_03.03), first extra-group
subcontractor rule (B_05.02 scope (d)), sub-consolidated registers. The Phase 1 model already allows it;
this phase adds the forms, rules and tests.

## 8. Order and size

| Step | Who | Waits for | Size with Claude |
|---|---|---|---|
| 0. Spec | Ignjat | taxonomy download | half a day, plus review |
| 1. Contract, DB, edit API | Ignjat | 0 | 1 to 2 days, plus review |
| 2a. Rules, readiness | Ignjat | 1 | 1 to 2 days (EBA rules are most of it) |
| 2b. Scan derivations | Ignjat | 1 | half a day |
| 2c. AI kinds | David | 1 | 1 day |
| 2d. Export | Matija | 0, 1 | 1 to 2 days |
| 2e. App forms, validation screen | Luka, Matija | 1 | 2 to 3 days |
| 3. Proof | Ignjat, Matija | 2a, 2d | 1 day, more if the validator finds problems |

Review and merging by Ignjat come on top. The real risk is Phase 0 and Phase 3, not the code.

## 9. Decisions

| # | Owner | Question | Options | Taken |
|---|---|---|---|---|
| D1 | team | Scope | (a) solo entities only; (b) group-ready model, solo forms first; (c) full groups now | (b): the model change is cheap now, a second migration later is not |
| D2 | Ignjat | Typed values | (a) `RegisterField` stays string, validator parses; (b) typed fields (`DateField`, `IntField`, enums) with provenance | (a): one shape, export is text anyway; parsing errors become readiness issues |
| D3 | Ignjat | Edit API | (a) one route per item; (b) one `PATCH /register` document | (a): small routes, easy role checks and tests |
| D4 | Matija | Export | (a) full xBRL-CSV package; (b) plain zip of CSVs | (a): xBRL-CSV is required since 31/03/2026 per the EBA |
| D5 | Luka, Matija | Who builds which forms | (a) Luka: entity, providers, contracts, services, supply chain; Matija: functions, assessments, definitions, validation, export; (b) by screen as today | (a): follows inventory vs register quality |
| D6 | Ignjat | Proof | (a) offline validator in CI; (b) manual check per release | (a) if the validator runs offline, else (b) |
| D7 | team | Unconfirmed AI values at export | (a) block the export; (b) export the column blank and flag it | (b), as a draft package only |

## 10. Sources

- CIR (EU) 2024/2956, Annex I and Annex III (bundled).
- DORA (EU) 2022/2554: Art. 28(3) register, 28(8) exit strategies (bundled).
- EBA, Preparing plain CSV reporting package for DORA:
  https://www.eba.europa.eu/sites/default/files/2025-01/1e5ffe22-64b2-4260-8fbf-8dcf095dfe4b/Preparing%20Plain%20csv%20reporting%20package%20for%20DORA.pdf
- EBA, Observations from RoI reporting testing, April 2025:
  https://eba.europa.eu/sites/default/files/2025-04/717e7e5f-14ac-45c6-b336-b0aac6a9062a/Observations%20from%20RoI%20reporting%20testing%20-%20common%20issues%20-%20April%202025%20%281%29.pdf
- EBA, Reporting framework 4.2 (xBRL-CSV date): https://www.eba.europa.eu/node/17002
