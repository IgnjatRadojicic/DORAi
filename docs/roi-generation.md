# How DORAi generates the Register of Information

As of 2026-11-22. What DORAi generates for each Register of Information (RoI) template and how, the trade-offs
behind the values it derives, and what a person checks and writes. Common questions are answered in [faq.md](faq.md).

DORAi builds all 15 DORA Register of Information templates (B_01.01 to B_07.01 plus B_99.01) from the firm's
register and its last cloud scan, then exports them as an EBA xBRL-CSV package. Deterministic rules fill and
check every cell; the AI only drafts four judgement columns, and a person accepts or rejects each draft.

Column codes are from CIR (EU) 2024/2956 Annex I; code lists are from the EBA DORA taxonomy, reporting framework 4.0 (module 1.1.0). Source of truth in
the repo: [`RoiTables.kt`](../shared/src/commonMain/kotlin/dorai/shared/roi/RoiTables.kt) (how cells are
filled), [`RoiReadiness.kt`](../shared/src/commonMain/kotlin/dorai/shared/roi/RoiReadiness.kt) (what is
checked), [`RoiSpec.kt`](../shared/src/commonMain/kotlin/dorai/shared/roi/RoiSpec.kt) (columns and option
lists), also as a table in [roi-spec.md](roi-spec.md).

## How each table is generated

One pure function, `RoiTables.build(register, lastScan, referenceDate)`, turns the register plus the last scan
into all 15 templates. Same input always gives the same output; no AI call happens here. Priority for every
cell: a value a person typed or accepted wins, then a scanned value, then a value we derive (sum, join, CIR
default).

**Where the data comes from**

- **Register** (PostgreSQL): entity, providers, contracts, services per contract, business functions, and any
  RoI column a person set on them (`roi` map, each value with provenance).
- **Cloud scan** (Azure Resource Graph, AWS, Terraform plan, SaaS apps from the identity provider): per
  resource its type, region and country, replica countries, monthly cost and currency, backup interval,
  redundancy, security alerts, and the tags `dora-function` and `dora-contract` that link it to a function and
  a contract.
- **GLEIF**: LEI lookup for providers (offered as a suggestion, a person accepts).

**Table by table**

| Template | One row per | What DORAi fills itself | How |
| --- | --- | --- | --- |
| B_01.01 Entity maintaining the register | the entity | LEI, name, country (0010-0030); reporting date (0060) | Entity settings; 0060 = the reference date of the export |
| B_01.02 Entities in scope | the entity (plus extra rows for a group) | LEI, name, country; type (0040) copied from B_01.01; last update (0070); deletion date (0090) | 0070 = newest edit timestamp anywhere in the register; 0090 = `9999-12-31` (still in scope) |
| B_01.03 Branches | branch | nothing | entered by a person as extra rows |
| B_02.01 Contracts, general | contract | reference (0010), currency (0040), annual cost (0050) | Cost = cost a person entered, else the scanned average monthly cost of the contract's resources x 12; currency from the contract, else from the scan |
| B_02.02 Contracts, specific | service x storage/processing country | contract ref, user entity LEI, provider code and code type, function id, service type, start/end date, country of provision (0130), stores data (0140), storage countries (0150), processing countries (0160) | Resources are matched to a service by explicit id or by tags (contract + function + service type). 0140 = yes if any matched resource is a data store; 0150 = countries of data stores and replicas; 0160 = countries of all matched resources; 0130 = most common country; no end date gives `9999-12-31`. Several countries give several rows |
| B_02.03 Intra-group contracts | link | Link column = `true` | rows entered by a person (groups only) |
| B_03.01 Entities signing (receiving) | contract | contract ref, signing LEI | LEI defaults to our entity |
| B_03.02 Providers signing | contract | contract ref, provider code and code type | from the contract's provider |
| B_03.03 Entities signing (providing) | link | Link column = `true` | intra-group only, entered by a person |
| B_04.01 Entities using the services | contract x using entity | contract ref, LEI, nature (0030), branch (0040) | Using entity defaults to our entity; nature = 1 (branch) if a branch code is set, else 2 (not a branch) |
| B_05.01 Providers | provider | code (0010) and type (0020), legal name (0050), Latin name (0060), HQ country (0080), total annual cost (0100) and currency (0090) | Code type = `LEI` when the code is an LEI; Latin name copied when the name is already Latin script; 0100 = sum of that provider's contract costs (only when every contract has a cost); ultimate parent left blank when it equals the provider itself |
| B_05.02 Supply chain | contract x service type x provider x rank | all rank 1 rows | Rank 1 = the direct provider for each service type of each contract. Rank 2+ (subcontractors) are entered by a person; their code types are filled from the provider |
| B_06.01 Functions | function | id (0010), name (0030), entity LEI (0040), criticality (0050) | 0050 = 1 (yes) or 2 (no) from the function's critical flag unless a person picked; "not assessed" (3) sets the last assessment date to `9999-12-31` |
| B_07.01 Assessment of ICT services | contract x service type that supports a critical function | contract ref, provider code and type, service type | Which rows exist is derived; every assessment value (0050-0120) comes from a person or an accepted AI draft |
| B_99.01 Definitions | option of a defined list | column code, name, option, description | Until the admin writes the firm's own definitions, we prefill the CIR option labels and raise a warning |

**Export package**: `META-INF/reportPackage.json`, `reports/report.json` (points to the EBA DORA module),
`parameters.csv`, `FilingIndicators.csv` (all 15 templates), one CSV per template, UTF-8. File name follows
the EBA filing rules: `<LEI>.CON_<country>_DORA010100_DORA_<refdate>_<timestamp>`. Closed-list values are
written as EBA taxonomy members (for example `eba_TA:S17`, `eba_GA:IE`). Amounts in units with 2 decimals. A
PDF report is generated alongside for people. Details: [csv-export.md](csv-export.md), [pdf-export.md](pdf-export.md).

## What a person checks: derived values and their trade-offs

These cells are filled by deterministic rules, so they are reproducible, but they rest on an assumption that
may not hold for every firm. Each shows its provenance in the app (`scanned`, or "derived" with no label), and
a person can overwrite any of them; a human value always wins over a derived one.

| Cell | What we do | Assumption that can be wrong |
| --- | --- | --- |
| B_02.01.0050 annual cost | Scanned average monthly cost x 12 (Azure: actual cost over the last 12 months; AWS: Cost Explorer, last 14 days) | CIR asks for the past year's expense. An average extrapolated from AWS's 14 days misses growth and seasonality; neither cloud bill covers support fees or contracts paid outside the cloud; a cost call that fails contributes nothing (with a warning) |
| B_05.01.0100 provider total | Sum of its contract costs | Only filled when every contract of that provider has a cost; currency conversion uses fixed rates from `DORAI_FX_<CODE>` |
| B_02.02.0140 stores data | Yes if any linked resource is a data store (storage, databases, queues, caches, key vaults, logs, backups) | Type-fragment list in [`ResourceKinds`](../shared/src/commonMain/kotlin/dorai/shared/roi/ResourceKinds.kt); a new store type not on the list reads as "no" |
| B_02.02.0150 storage location | Countries of data-store resources plus their replica countries | Region-to-country map in [`Regions`](../shared/src/commonMain/kotlin/dorai/shared/rules/Regions.kt); an unknown region gives no country, never assumed EU |
| B_02.02.0160 processing location | Countries where linked resources run | Covers where our workloads run, not where the provider's staff manage or support the data (for example follow-the-sun support from the US) |
| B_02.02.0130 country of provision | Most common country among linked resources | A heuristic; the contract may name a different country |
| B_02.02.0080 end date | `9999-12-31` when no end date is set | Correct for open-ended contracts; wrong if someone just forgot the date |
| B_02.02.0090 "terminated" fact | End date before the reference date | Drives whether a termination reason is required |
| B_02.02.0020, B_03.01.0020, B_04.01.0020, B_06.01.0040 entity LEI | Defaults to our own entity | Correct for a solo entity; groups must set the real user entity |
| B_04.01.0030 nature | 2 (not a branch) unless a branch code is set | Same, solo-entity default |
| B_05.01.0020 code type | `LEI` when the provider code is its LEI | Fine; other code types need a person |
| B_05.01.0060 Latin name | Copies the legal name if it is Latin script | Transliteration is not done |
| B_05.02 rank 1 rows | Direct provider per contract and service type | Subcontractors are not discovered |
| B_06.01.0050 criticality | From the function's critical flag (yes/no) | The flag is the firm's earlier call; a fresh assessment may differ |
| B_07.01 which rows exist | One row per contract and service type that supports a critical function | Follows criticality, so a wrong flag adds or drops rows |
| B_01.02.0070 last update | Newest edit timestamp in the register | Fine |
| B_99.01 definitions | CIR labels until the admin writes them | Always raises the `default-definitions` warning |

**AI-suggested values** (never exported until a person accepts):

- Service type (B_02.02.0060) for resource types not in our fixed table
  ([`ServiceTypeTable`](../shared/src/commonMain/kotlin/dorai/shared/rules/ServiceTypeTable.kt)); the table
  fixes storage accounts and S3 as S17 (IaaS), PostgreSQL, DynamoDB, Lambda and SQS as S18 (PaaS).
- Business function a resource supports (B_02.02.0050) when the `dora-function` tag is missing.
- Data sensitivity (B_02.02.0170), level of reliance (B_02.02.0180), licensed activity (B_06.01.0020),
  substitutability (B_07.01.0050).
- Provider identity from GLEIF (LEI, legal name, HQ country, ultimate parent): a deterministic lookup, but
  still a suggestion a person accepts.

Each suggestion has a reason, a confidence (0 to 1) and at least one citation, or it says "unknown" /
"insufficient evidence". Pending suggestions raise a `roi-unconfirmed` warning and the cell is exported blank.

## What a person writes

Anything that lives in a contract, a board decision or a business impact analysis cannot be seen in the cloud,
so a person enters it. The readiness check (`RoiReadiness`) lists every such cell that is still empty, but only
when the CIR's condition applies (for example notice periods only for services that support a critical
function). The final package is refused while errors remain; a `DRAFT_` package can always be exported to show
progress.

| Template | Columns a person fills | Why we cannot know it |
| --- | --- | --- |
| B_01.01 / B_01.02 Entity | Type of entity (0040), competent authority (0050), hierarchy in group (0050), parent LEI (0060), date of integration (0080), total assets and currency (0110, 0100) | Legal and accounting facts about the firm |
| B_01.03 Branches | all columns | Corporate structure |
| B_02.01 Contracts | Type of arrangement (0020), overarching contract ref (0030); annual cost when the provider is not a scanned cloud | Contract paperwork; cost only comes from cloud bills |
| B_02.02 Contracts, specific | Termination reason (0090), notice periods for us and for the provider (0100, 0110), governing law country (0120); sensitivity (0170) and reliance (0180) are AI-drafted; locations (0130-0160) for non-cloud services | Contract clauses and risk judgement |
| B_02.03, B_03.03 Intra-group | all rows | Only for groups; not derivable |
| B_05.01 Providers | Type of person (0070), additional code (0030, 0040), Latin name if the legal name is not Latin script (0060), code when there is no LEI (EUID, CRN, VAT); ultimate parent (0110, 0120) is suggested from GLEIF | Legal identity of the provider |
| B_05.02 Supply chain | All subcontractor rows, rank 2 and up | Subprocessor lists are in contracts, not in the cloud API |
| B_06.01 Functions | Licensed activity (0020, AI-drafted), reasons for criticality (0060), date of last criticality assessment (0070), RTO (0080), RPO (0090), impact of discontinuing (0100) | Business impact analysis |
| B_07.01 Assessment | Substitutability (0050, AI-drafted), reason if hard to substitute (0060), date of last audit (0070), exit plan exists (0080), reintegration (0090), impact (0100), alternatives identified (0110), names of alternatives (0120) | Third-party risk assessment by the firm |
| B_99.01 Definitions | The firm's own meaning of each option for 6 lists (arrangement type, sensitivity, function impact, substitutability, reintegration, service impact) | Firm policy; we prefill CIR labels and warn |

The register also needs two tags on cloud resources, `dora-function` and `dora-contract`. Without them a
resource cannot be linked to a service row, and the `untagged` rule flags it.

## Trade-offs and limitations

What the generated register cannot cover yet, by design or by what the clouds expose.

- Annual cost is extrapolated from cloud cost: Azure uses the last 12 months, but AWS Cost Explorer keeps
  per-resource cost only 14 days, and only once resource-level data is turned on. Non-cloud contracts need a
  cost typed in.
- Processing location is where our resources run, not where the provider's support staff access data.
- Subcontractors (rank 2+) are not discovered; a person adds them from the provider's subprocessor list.
- AWS resources that were never tagged are found only with a Resource Explorer index; without tags a resource
  cannot be linked to a contract and function.
- Service types are fixed only for common resource types; the rest are AI-classified and need acceptance.
- Cost conversion uses fixed FX rates from the environment, not live rates.
- One server per install; login lockout counters live in memory and reset on restart.

## Annex codes and code lists

The app and register use the CIR option keys ("1", "S17"); only the export turns them into EBA taxonomy
members. The mapping is generated by `scripts/taxonomy-codes.py` from the EBA taxonomy (framework 4.0, errata 5)
into [`TaxonomyCodeLists.kt`](../shared/src/commonMain/kotlin/dorai/shared/roi/TaxonomyCodeLists.kt), so a new
taxonomy release changes two files. Arelle 2.47.5 validated the demo package offline with no errors on
2026-11-21 (93 EBA validation rules ran).

**Annex III: type of ICT services** (B_02.02.0060, B_05.02.0020, B_07.01.0040; exported as `eba_TA:Sxx`)

| Code | Service type | Code | Service type |
| --- | --- | --- | --- |
| S01 | ICT project management | S11 | Network infrastructure |
| S02 | ICT development | S12 | Hardware and physical devices |
| S03 | ICT help desk and first level support | S13 | Software licensing (excluding SaaS) |
| S04 | ICT security management services | S14 | ICT operation management |
| S05 | Provision of data | S15 | ICT consulting |
| S06 | Data analysis | S16 | ICT risk management |
| S07 | ICT, facilities and hosting (excluding cloud) | S17 | Cloud services: IaaS |
| S08 | Computation | S18 | Cloud services: PaaS |
| S09 | Non-cloud data storage | S19 | Cloud services: SaaS |
| S10 | Telecom carrier | | |

We treat S05, S06, S07, S08, S09, S14, S17, S18 and S19 as data-handling services: they require a processing
location (B_02.02.0160) even when the scan saw no resource.

**Other closed lists**

| Column | Options (key: label) | EBA members |
| --- | --- | --- |
| B_01.01.0040 / B_01.02.0040 Type of entity | 24 options: 1 credit institution, 2 payment institution, 4 e-money institution, 5 investment firm, 6 CASP, 15 insurance ... 22 other financial entity, 23 non-financial ICT intra-group provider, 24 non-financial other | `eba_CT:x12` ... `x318` |
| B_01.02.0050 Hierarchy in group | 1 ultimate parent, 2 parent of sub-consolidated part, 3 subsidiary, 4 not part of a group, 5 provider to which all operations are outsourced | `eba_RP:x53, x551, x56, x21, x210` |
| B_02.01.0020 Arrangement type | 1 standalone, 2 overarching or master, 3 subsequent or associated | `eba_CO:x1-x3` |
| B_02.02.0090 Termination reason | 1 expired, not renewed; 2 provider breach; 3 provider impediments; 4 data security weaknesses; 5 competent authority request; 6 other | `eba_CO:x4-x9` |
| B_02.02.0140, B_07.01.0080 Yes/No | true / false | `eba_BT:x28` / `x29` |
| B_02.02.0170 Sensitivity | 1 low, 2 medium, 3 high | `eba_ZZ:x791-x793` |
| B_02.02.0180 Reliance | 1 not significant, 2 low, 3 material, 4 full | `eba_ZZ:x794-x797` |
| B_04.01.0030 Nature of entity | 1 branch of a financial entity, 2 not a branch | `eba_ZZ:x838, x839` |
| B_05.01.0070 Type of person | 1 legal person, 2 individual in business capacity | `eba_CT:x212, x213` |
| B_06.01.0020 Licensed activity | about 150 activities per entity type (CRD, PSD2, MiFID, Solvency II ...) | `eba_TA:x104 ... x276, qx277, qx278` |
| B_06.01.0050 Criticality | 1 yes, 2 no, 3 assessment not performed | `eba_BT:x28, x29, x21` |
| B_06.01.0100, B_07.01.0100 Impact of discontinuing | 1 low, 2 medium, 3 high, 4 not assessed | `eba_ZZ:x791-x793, x799` |
| B_07.01.0050 Substitutability | 1 not substitutable, 2 highly complex, 3 medium, 4 easily | `eba_ZZ:x959-x962` |
| B_07.01.0060 Reason hard to substitute | 1 no real alternatives, 2 data/workload migration difficult, 3 both | `eba_ZZ:x963-x965` |
| B_07.01.0090 Reintegration | 1 easy, 2 difficult, 3 highly complex | `eba_ZZ:x798, x966, x967` |
| B_07.01.0110 Alternatives identified | 1 yes, 2 no, 7 not assessed | `eba_BT:x28, x29, x21` |

**Code types and formats**

- Provider code type: `LEI` (qx2000), `EUID` (qx2002), or country + `_CRN` (qx2003), `_VAT` (qx2004), `_PNR`
  (qx2005), `_NIN` (qx2001); exported as `eba_qCO:qx...`. Legal persons must use LEI or EUID; outside the EU
  only LEI.
- Countries ISO 3166-1 alpha-2 (`eba_GA:IE`), currencies ISO 4217 (`eba_CU:EUR`), dates ISO 8601, LEI checked
  with the ISO 17442 checksum, function id `F` + number (F1, F2).
- "No end date" is `9999-12-31`, as the EBA asks.

## Rules and findings

Every finding comes from a rule in Kotlin code in `shared`, so the server, the app and the CLI give identical
results; the AI may only explain a finding afterwards. There are three groups.

**1. Change rules on a Terraform plan** ([`DiffRules.kt`](../shared/src/commonMain/kotlin/dorai/shared/rules/DiffRules.kt);
CLI in CI and the server's check). The CLI exits 1 on any High finding, so the pipeline blocks until the
register is updated or the change is approved; exit 2 on bad input. See [ci-check.md](ci-check.md).

| Rule | Severity | Fires when | Template |
| --- | --- | --- | --- |
| `new-provider` | High | A resource comes from a provider not in the register (no Art. 30 contract, no Art. 28(4) assessment) | B_05.01, B_02.01 |
| `data-location` | High / Medium | High: country outside EU/EEA, or not one of the contract's declared locations. Medium: region cannot be resolved to a country | B_02.02 |
| `critical-function` | High | A resource of a critical function is deleted, loses multi-region redundancy, or moves outside EU/EEA | B_06.01 |
| `untagged` | Medium | New or changed resource lacks `dora-function` or `dora-contract` | B_02.02 |
| `new-service-type` | Medium | New resource has no matching service row (same contract, Annex III code and function) | B_02.02 |

**2. Readiness checks on the RoI** (errors block the final package).

- `roi-missing`: a required cell is empty, evaluated against the CIR condition for that row.
- `roi-format`: bad LEI or EUID, country, currency, date, amount, whole number, option key, code type or
  function id.
- `roi-unknown-reference`: a contract, provider, function, entity or branch is referenced but not reported.
- `roi-broken-chain`: a subcontractor's recipient is not at rank minus one of the same chain.
- `roi-legal-person-code`: a legal person not identified by LEI or EUID, or by EUID outside the EU.
- `roi-duplicate-function`, `roi-duplicate-key`: two rows share the taxonomy primary key with different values.
- Register basics: `invalid-date-range`, `unknown-provider`, `invalid-service-type`, `unknown-function`,
  `missing-lei`, `invalid-lei`, `duplicate-key`.

**3. Risk warnings** (point a reviewer at something to assess, never decide it).

| Warning | Basis | Fires when |
| --- | --- | --- |
| `art29-same-group` | DORA Art. 29 concentration | Two or more providers with the same ultimate parent support critical functions |
| `art29-dominant-group` | DORA Art. 29 | One group supports more than half of all critical functions |
| `art28-no-exit-plan` | DORA Art. 28(8) | A critical service has exit plan = no |
| `art28-no-alternative` | DORA Art. 28(8) | Hard to substitute and no alternative identified |
| `art12-no-backup` | DORA Art. 12 | Critical function declares an RPO but a data store has no backup |
| `art12-rpo-not-met` | DORA Art. 12 | Backup interval longer than the declared RPO |
| `art12-single-location` | DORA Art. 12 | Critical data in one location only, no replica or geo-backup |
| `ict-open-security-findings` | ICT security | Resource of a critical function has open High or Critical alerts (Defender, Security Hub) |
| `unregistered-vendor` | DORA Art. 28(3) | A seller on the cloud bill in the last 12 months is not a provider in the register |
| `unregistered-saas` | DORA Art. 28(3) | Staff sign in to a SaaS app whose publisher is not in the register |
| `roi-unconfirmed` | product rule | An AI suggestion is still pending |
| `default-definitions` | CIR B_99.01 | Definitions are still the CIR labels |

## The AI part

The AI never decides a finding and never writes to the register by itself: it drafts, explains and cites, and
a reviewer accepts or rejects each draft. Built with Koog behind a small `ChatModel` interface; default model
`gpt-4.1-mini` (set with `DORAI_AI_CHAT_MODEL`), or a built-in fake model so the server runs and tests pass with
no key and no network.

**What it does**

| Service | Output | Where it shows |
| --- | --- | --- |
| Resource classifier | Annex III service type for a resource type not in the fixed table | Review screen |
| Function suggester | Which business function a resource supports | Review screen |
| RoI value drafter | One option key for B_02.02.0170, B_02.02.0180, B_06.01.0020 or B_07.01.0050, or "unknown" | Review screen, per register item |
| Finding explainer | Plain-language reason a rule fired, with the article | Findings screen |
| Report drafter | Summary paragraph of the PDF report | PDF export |

**How every answer is grounded** ([`GroundedAnswer`](../server/src/main/kotlin/dorai/server/ai/services/GroundedAnswer.kt))

1. Search the bundled regulation texts (DORA 2022/2554 and CIR 2024/2956, chunked, embedded with
   `text-embedding-3-small`, stored in PostgreSQL with pgvector).
2. No passage with cosine similarity of at least 0.40: answer "insufficient evidence" without calling the model.
3. Send our instructions in one message and the data plus evidence excerpts in a separate message, so data
   cannot act as instructions.
4. Check the reply: every cited id must be one of the supplied passages, the value must be on the closed list,
   confidence 0 to 1. One retry on a bad reply, then failure. An outage is a failure, never stored as a
   suggestion.

**Human in the loop**: a suggestion has value, reason, confidence and citations. Accept writes it to the
register as `human-confirmed` with who and when; reject discards it. Until then it is `ai-suggested`, shown in
the app, and left out of the export.

## Security, self-hosting and access

The firm runs the server itself, so register data, cloud credentials and the AI key never leave its
infrastructure (except the AI calls it chooses to enable). Nothing is SaaS on our side. Setup: [deploy.md](deploy.md).

- **Deployment**: one Ktor server plus PostgreSQL with pgvector (Docker image or release download). Settings and
  secrets only from `DORAI_*` environment variables. Desktop app (Windows, macOS, Linux), Android app, and a CLI
  for CI.
- **Cloud access is read-only**: Azure service principal with Reader (plus `Application.Read.All` for SaaS
  discovery), AWS read-only access to tags and cost data, optional Okta read token. DORAi never creates or
  changes cloud resources. Scheduled scans with `DORAI_SCAN_EVERY` (minimum 1 hour).
- **Invite link instead of a password**: the admin creates an invite; the link carries the server address, a
  one-time code and the SHA-256 fingerprint of the server certificate. Code: 8 characters (about 39 bits),
  single use, valid 72 hours, stored only as a hash.
- **Certificate pinning**: the server uses a self-signed certificate; the app trusts only the exact certificate
  whose fingerprint came in the invite, so no certificate authority is involved and a man-in-the-middle fails
  the handshake. Exception: behind a Cloudflare Tunnel (`DORAI_PROXY=cloudflare`) the apps see Cloudflare's
  certificate, so invite links carry no pin and the apps check the certificate against the system's authorities.
- **Passwords**: the admin sets a temporary password, the user must change it at first login. Argon2id hashes
  (OWASP parameters). Lockout after repeated wrong passwords; unknown usernames take the same time and are
  tracked the same way, so guessing gives no signal. Rate limit per address on redeem, login and password
  change.
- **Sessions**: 15-minute signed access tokens (JWT, never stored) and 30-day refresh tokens stored as hashes
  and rotated on use; reuse of an old refresh token ends the session. Changing a password ends all other
  sessions. The role is read from the database on every call, so a role change applies immediately.
- **Roles**: `admin` (users, invites, entity settings, definitions), `reviewer` (scan, accept or reject, edit
  register, export), `viewer` (read only).
- **Audit trail**: every value carries provenance (`scanned`, `planned`, `ai-suggested`, `human-confirmed`),
  with who confirmed it and when; the PDF report lists every value still awaiting a person.
- **Errors**: RFC 9457 problem details (`application/problem+json`).

## Sources

- CIR (EU) 2024/2956 Annex I and III, and DORA (EU) 2022/2554, both bundled in
  [`server/src/main/resources/ai/regulations/`](../server/src/main/resources/ai/regulations/).
- EBA DORA taxonomy, reporting framework 4.0.
- [faq.md](faq.md), [decisions.md](decisions.md), [deploy.md](deploy.md), [roi-compliance-plan.md](roi-compliance-plan.md).
