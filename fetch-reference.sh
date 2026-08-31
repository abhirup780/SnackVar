#!/usr/bin/env bash
#
# Restores or refreshes the RefSeq reference set in ./reference.
#
# The complete set (53,657 sequences, ~470 MB) is committed to this repository,
# so a normal clone already has everything and you do not need to run this.
# It is here for two cases:
#
#   * restoring reference/ if it was deleted or a clone was made without it
#   * refreshing against upstream to pick up sequences added since
#
# Existing files are never overwritten, so re-running only adds what is missing.
#
# Data source: the upstream project, Young-gon Kim's SnackVar
# https://github.com/Young-gonKim/SnackVar

set -euo pipefail

UPSTREAM="https://github.com/Young-gonKim/SnackVar.git"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${SNACKVAR_REFERENCE_DIR:-$ROOT/reference}"

usage() {
    cat <<USAGE
Usage: $(basename "$0") [--into DIR]

  --into DIR   Install to DIR instead of $ROOT/reference
  -h, --help   Show this message
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --into) DEST="${2:?--into needs a directory}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "error: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
    esac
done

if ! command -v git >/dev/null 2>&1; then
    echo "error: git is required but was not found on PATH." >&2
    exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Fetching the reference set from upstream (~470 MB)."
echo "Destination: $DEST"
echo

# Blobless + sparse: pulls only the reference directory, not the demo video
# or any of the source history.
git clone --quiet --depth 1 --filter=blob:none --sparse "$UPSTREAM" "$TMP/snackvar"
git -C "$TMP/snackvar" sparse-checkout set reference
git -C "$TMP/snackvar" checkout --quiet

SRC="$TMP/snackvar/reference"
if [ ! -d "$SRC" ]; then
    echo "error: the upstream checkout did not contain a reference/ directory." >&2
    exit 1
fi

mkdir -p "$DEST"
before=$(find "$DEST" -name '*.fasta' 2>/dev/null | wc -l | tr -d ' ')

# Never clobber what is already installed. GNU cp warns that -n is non-portable
# and prefers --update=none, so use that where it is supported.
if cp --help 2>/dev/null | grep -q -- '--update'; then
    cp -r --update=none "$SRC"/. "$DEST"/
else
    cp -rn "$SRC"/. "$DEST"/
fi

after=$(find "$DEST" -name '*.fasta' 2>/dev/null | wc -l | tr -d ' ')

echo
echo "Done."
echo "  sequences before: $before"
echo "  sequences now:    $after"
echo "  total size:       $(du -sh "$DEST" | cut -f1)"
echo
echo "Restart SnackVar to pick up any new sequences."
