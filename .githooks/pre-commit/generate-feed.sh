#!/usr/bin/env bash

set -euo pipefail

echo "Generating feed.xml"

cd "$(git rev-parse --show-toplevel)"
git stash save --keep-index --include-untracked -m 'generate_feed.clj generated'
cd _meta
mise exec -- bb generate_feed.clj
git add -A
git stash pop
