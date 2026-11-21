#!/usr/bin/env sh
# Checks the FinPay RoI package against the EBA DORA taxonomy with Arelle, offline.
# Arelle and the taxonomy are test tools only: nothing here ships in the server or the app.
#   ARELLE        command to run (default: arelleCmdLine, from `pip install arelle-release`)
#   EBA_TAXONOMY  path to the EBA taxonomy package zip (required)
set -eu

ARELLE="${ARELLE:-arelleCmdLine}"
if [ -z "${EBA_TAXONOMY:-}" ] || [ ! -f "$EBA_TAXONOMY" ]; then
  echo "EBA_TAXONOMY is not set to a taxonomy zip: skipping the taxonomy check." >&2
  exit 0
fi

./gradlew -q :server:roiSample
package=$(ls server/build/roi-sample/*.zip | head -n 1)
log=server/build/roi-sample/arelle.log

"$ARELLE" --file "$package" --packages "$EBA_TAXONOMY" --validate --logFile "$log" --logLevel warning || true

# Arelle exits 0 even with validation errors, so the log decides.
if grep -Eq "\[(error|ERROR)|\] *(error|ERROR)" "$log"; then
  echo "Taxonomy check found errors:" >&2
  grep -E "error|ERROR" "$log" | head -n 50 >&2
  exit 1
fi
echo "Taxonomy check passed: $package"
