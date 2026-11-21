#!/usr/bin/env sh
# Checks the FinPay RoI package against the EBA DORA taxonomy with Arelle.
# Arelle and the taxonomy are test tools only: nothing here ships in the server or the app.
#   ARELLE        command to run (default: arelleCmdLine, from `pip install arelle-release`)
#   EBA_TAXONOMY  path to the EBA taxonomy zip (required). DORA is in reporting framework 4.0 errata 5;
#                 its zip holds the Dictionary, Reporting_Frameworks and Severity packages, which are
#                 unpacked and passed to Arelle together.
#   ARELLE_FLAGS  extra Arelle options, for example "--internetConnectivity offline" when the Eurofiling
#                 base schemas the module imports are also given as packages in EBA_EXTRA_PACKAGES
#   EBA_EXTRA_PACKAGES  more package zips, joined with "|"
set -eu

ARELLE="${ARELLE:-arelleCmdLine}"
if [ -z "${EBA_TAXONOMY:-}" ] || [ ! -f "$EBA_TAXONOMY" ]; then
  echo "EBA_TAXONOMY is not set to a taxonomy zip: skipping the taxonomy check." >&2
  exit 0
fi

./gradlew -q :server:roiSample
package=$(ls server/build/roi-sample/*.zip | head -n 1)
log=server/build/roi-sample/arelle.log
work=server/build/roi-sample/taxonomy
rm -rf "$work" "$log"
mkdir -p "$work"

# The errata zip nests the real taxonomy packages; a plain taxonomy package is used as it is.
unzip -q -o "$EBA_TAXONOMY" '*.zip' -d "$work" 2>/dev/null || true
packages=$(find "$work" -name '*.zip' | sort | paste -sd '|' -)
[ -n "$packages" ] || packages="$EBA_TAXONOMY"
[ -z "${EBA_EXTRA_PACKAGES:-}" ] || packages="$packages|$EBA_EXTRA_PACKAGES"

# shellcheck disable=SC2086
"$ARELLE" --file "$package" --packages "$packages" --validate ${ARELLE_FLAGS:-} \
  --logFile "$log" --logLevel warning --logFormat "[%(levelname)s] [%(messageCode)s] %(message)s" || true

# Arelle exits 0 even with validation errors, so the log decides.
if grep -Eq "^\[(ERROR|CRITICAL)\]" "$log"; then
  echo "Taxonomy check found errors:" >&2
  grep -E "^\[(ERROR|CRITICAL)\]" "$log" | head -n 50 >&2
  exit 1
fi
echo "Taxonomy check passed: $package"
