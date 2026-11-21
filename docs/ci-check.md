# Checking Terraform changes in CI

`dorai check` runs the server's rules on a Terraform plan, offline, against a copy of the register.
It needs no server, no network and no login, so any CI runner can use it.

## The command
```
dorai check plan.json --register register.json [--format text|markdown|json] [--output file]
```
- `plan.json`: `terraform plan -out plan.bin`, then `terraform show -json plan.bin > plan.json`.
- `register.json`: the register as `GET /api/register` returns it (an admin or reviewer exports it from the
  app, or with `curl -H "Authorization: Bearer <token>" https://<server>/api/register`). Keep it in the
  infrastructure repo and refresh it when the register changes.
- `markdown` is the pull request comment; `json` has a summary and every finding.

| Exit code | Meaning |
|---|---|
| 0 | No High findings |
| 1 | At least one High finding |
| 2 | Bad input: missing file, not a Terraform plan, invalid register, wrong option |

Build it with `./gradlew :cli:installDist`; the command is then `cli/build/install/dorai/bin/dorai`.

## The GitHub Action
```yaml
on: pull_request
permissions:
  contents: read
  pull-requests: write
jobs:
  dorai:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      # terraform init / plan / show -json here, writing plan.json
      - uses: IgnjatRadojicic/DORAi@v0.1.0
        with:
          plan: plan.json
          register: dora/register.json
```
The action builds the CLI from its own source (Java 21 and Gradle, cached), checks the plan, writes the
report to the job summary, and posts one comment on the pull request that it updates on every push.
The job fails on High findings and on bad input.

Inputs: `plan`, `register`, `comment` (default `true`) and `github-token` (default the workflow token).
Outputs: `exit-code` and `report` (path of the Markdown file).

Pull requests from forks get a read-only token: the comment is skipped with a warning, the job summary
still shows the report.

While this repository is private, other repositories can only use the action if they belong to the same
owner and the repository allows access in Settings, Actions, General.

## In this repository
`.github/workflows/dorai-check.yml` runs the action on `examples/finpay`: the FinPay register and a plan
with no changes, so it passes on main. For the demo, a pull request replaces `examples/finpay/plan.json`
with `server/src/main/resources/fixtures/plan-eastus.json`; the comment then shows two High and two Medium
findings and the check fails.

`ExamplesTest` keeps `examples/finpay/register.json` equal to the fixture register; after changing the
fixture, run the server tests with `DORAI_UPDATE_EXAMPLES=1`.
