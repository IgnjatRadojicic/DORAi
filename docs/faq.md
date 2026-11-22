# DORAi FAQ

As of 2026-11-22. Frequently asked questions about DORAi and the Register of Information it generates.
The full description of what is generated, how, and what a person checks and writes is in
[roi-generation.md](roi-generation.md).

## Can the AI put a wrong value in the regulator's report?

No. AI values are `ai-suggested` and excluded from
the export until a reviewer accepts them; accepted values are recorded with the reviewer's name and time.

## Where do findings come from, then?

Deterministic rules in `shared` (see [Rules and findings](roi-generation.md#rules-and-findings)). The same code runs in
the server, the app and the CLI, and each rule has a unit test with fixtures.

## What if the AI has no source?

It says "insufficient evidence" and the model is not even called. Every
answer cites a passage of DORA or the CIR, and the citation id is checked against what we gave it.

## Is the output really accepted by the EBA?

The package uses the EBA DORA taxonomy 4.0 (module 1.1.0)
xBRL-CSV layout. Arelle 2.47.5 validated the demo package offline with no errors, running 93 EBA validation
rules. Submitting to the national authority's portal stays with the firm.

## How much of the RoI do you fill automatically?

All identifiers, joins and cloud facts: references,
provider codes, service types for known resource types, dates, data locations, data storage flag, costs, rank 1
supply chain, the B_07.01 row set. Contract and risk judgement (notice periods, governing law, RTO/RPO,
substitutability, exit plans) is entered by a person or drafted by the AI and accepted.

## What happens when the cloud changes?

Two paths. Before: the CLI checks a Terraform plan in the pull
request and fails on High findings (new provider, data outside EU/EEA, critical function at risk). After:
scheduled scans compare the live cloud with the register and update scanned values and warnings.

## Why self-hosted?

The register and cloud credentials are sensitive and supervised; the firm keeps them
inside its own perimeter. Invites carry a pinned certificate fingerprint (unless the server runs behind a Cloudflare Tunnel), never a password.

## Why does PostgreSQL hold the regulation?

One database for register and search (pgvector), no extra service
to run.

## Do you support groups?

The model is group-ready (entities, branches, intra-group arrangements as rows,
decision of 2026-11-21); forms and automatic filling target a single entity first.

## What can't it do yet?

Mainly: annual cost is extrapolated from cloud bills, processing location means where our resources run (not
where provider staff access data), and subcontractors are not discovered. The full list is in
[Trade-offs and limitations](roi-generation.md#trade-offs-and-limitations).
