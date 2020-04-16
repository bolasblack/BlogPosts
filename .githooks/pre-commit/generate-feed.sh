#!/usr/bin/env bash

set -euo pipefail

echo "Generating feed.xml"

cd "$(git rev-parse --show-toplevel)"
git stash save --keep-index --include-untracked -m 'generate_feed.rb generated'
cd _meta
bundler exec ruby generate_feed.rb
git add -A
git stash pop
