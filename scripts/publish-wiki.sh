#!/usr/bin/env bash
# Publishes the docs in this repository as GitHub wiki pages, so the wiki is a generated copy and never edited by hand.
#
#   scripts/publish-wiki.sh               clone the wiki, regenerate every page, commit and push
#   scripts/publish-wiki.sh --dry-run DIR  only write the pages into DIR, to look at them first
#
# The wiki must be turned on (Settings > Features > Wikis) and have a first page created in the browser,
# otherwise GitHub has no wiki repository to clone. Pages link back to the file they come from on main.
set -euo pipefail

REPO="IgnjatRadojicic/DORAi"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ "${1:-}" == "--dry-run" ]]; then
  OUT="${2:?usage: --dry-run DIR}"
  mkdir -p "$OUT"
  PUSH=false
else
  OUT="$(mktemp -d)"
  trap 'rm -rf "$OUT"' EXIT
  git clone --quiet "https://github.com/$REPO.wiki.git" "$OUT"
  PUSH=true
fi

python3 - "$ROOT" "$OUT" "$REPO" <<'PY'
import os, posixpath, re, sys

root, out, repo = sys.argv[1:4]
blob = f"https://github.com/{repo}/blob/main/"

# Repository file -> wiki page name, in sidebar order.
pages = {
    "docs/architecture.md": "Architecture",
    "docs/roi-generation.md": "RoI-generation",
    "docs/faq.md": "FAQ",
    "docs/deploy.md": "Deployment",
    "docs/decisions.md": "Decisions",
    "CONTRIBUTING.md": "Contributing",
    "SECURITY.md": "Security",
}
titles = {
    "Architecture": "Architecture",
    "RoI-generation": "How the RoI is generated",
    "FAQ": "FAQ",
    "Deployment": "Deployment",
    "Decisions": "Decisions",
    "Contributing": "Contributing",
    "Security": "Security",
}
link = re.compile(r"(?<!!)\[([^\]]*)\]\(([^)\s]+)\)")

def rewrite(text, source):
    """Links to published files become wiki links; other relative links point at the file on main."""
    base = posixpath.dirname(source)
    def fix(m):
        label, target = m.group(1), m.group(2)
        if re.match(r"[a-z]+:", target) or target.startswith("#"):
            return m.group(0)
        path, _, anchor = target.partition("#")
        resolved = posixpath.normpath(posixpath.join(base, path))
        if resolved in pages:
            return f"[{label}]({pages[resolved]}{'#' + anchor if anchor else ''})"
        return f"[{label}]({blob}{resolved}{'#' + anchor if anchor else ''})"
    return link.sub(fix, text)

for source, page in pages.items():
    path = os.path.join(root, source)
    if not os.path.exists(path):
        continue
    text = open(path, encoding="utf-8").read()
    note = f"> Generated from [`{source}`]({blob}{source}) on `main`. Edit that file, not this page.\n\n"
    open(os.path.join(out, page + ".md"), "w", encoding="utf-8").write(note + rewrite(text, source))

readme = open(os.path.join(root, "README.md"), encoding="utf-8").read()
intro = readme.split("\n## ", 1)[0].split("\n", 1)[1].strip()
home = "# DORAi\n\n" + rewrite(intro, "README.md") + "\n\n## Pages\n\n" + "".join(
    f"- [{titles[p]}]({p})\n" for p in pages.values()
) + f"\nSource code, build and run instructions: [the repository]({blob.replace('/blob/main/', '')}).\n"
open(os.path.join(out, "Home.md"), "w", encoding="utf-8").write(home)

sidebar = "**DORAi**\n\n- [Home](Home)\n" + "".join(f"- [{titles[p]}]({p})\n" for p in pages.values())
open(os.path.join(out, "_Sidebar.md"), "w", encoding="utf-8").write(sidebar)
print(f"Wrote {len(pages) + 2} pages to {out}")
PY

if $PUSH; then
  cd "$OUT"
  git add -A
  if git diff --cached --quiet; then
    echo "Wiki already up to date."
  else
    git commit --quiet -m "Regenerate wiki from docs at $(git -C "$ROOT" rev-parse --short HEAD)"
    git push --quiet
    echo "Wiki updated: https://github.com/$REPO/wiki"
  fi
fi
