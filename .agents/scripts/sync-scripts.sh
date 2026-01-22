#!/usr/bin/env bash
#
# Sync scripts from skill directory to project .agents/scripts/
#
# Usage:
#     bash sync-scripts.sh <project_dir>
#     CLAUDE_SKILL_DIR must be set
#

PROJECT_DIR="${1:-.}"
SKILL_DIR="$CLAUDE_SKILL_DIR"
AGENTS_DIR="$PROJECT_DIR/.agents"
CONFIG_FILE="$AGENTS_DIR/config.json"

[ -d "$AGENTS_DIR" ] || exit 0
[ -d "$SKILL_DIR" ] || exit 0

# Check if all updates disabled
if [ -f "$CONFIG_FILE" ]; then
    DISABLE=$(python3 -c "import json; c=json.load(open('$CONFIG_FILE')); print('true' if c.get('disableAutoUpdateScripts') is True else 'false')" 2>/dev/null || echo "false")
    [ "$DISABLE" = "true" ] && exit 0
fi

UPDATED=""

for SCRIPT in "$SKILL_DIR/scripts/"*.py "$SKILL_DIR/scripts/"*.sh; do
    [ -f "$SCRIPT" ] || continue
    BASENAME=$(basename "$SCRIPT")
    TARGET="$AGENTS_DIR/scripts/$BASENAME"
    [ -f "$TARGET" ] || continue

    # Check if disabled
    if [ -f "$CONFIG_FILE" ]; then
        SKIP=$(python3 -c "import json; c=json.load(open('$CONFIG_FILE')); d=c.get('disableAutoUpdateScripts',[]); print('true' if isinstance(d,list) and '$BASENAME' in d else 'false')" 2>/dev/null || echo "false")
        [ "$SKIP" = "true" ] && continue
    fi

    SRC_MD5=$(md5 -q "$SCRIPT" 2>/dev/null || md5sum "$SCRIPT" | cut -d' ' -f1)
    TGT_MD5=$(md5 -q "$TARGET" 2>/dev/null || md5sum "$TARGET" | cut -d' ' -f1)
    if [ "$SRC_MD5" != "$TGT_MD5" ]; then
        cp "$SCRIPT" "$TARGET"
        UPDATED="$UPDATED $BASENAME"
    fi
done

# Sync CLAUDE.md (preserve user content after marker)
TEMPLATE="$SKILL_DIR/templates/CLAUDE.md"
TARGET_MD="$AGENTS_DIR/CLAUDE.md"
USER_MARKER="<!-- USER CONTENT BELOW"
if [ -f "$TEMPLATE" ] && [ -f "$TARGET_MD" ]; then
    # Extract template portion (before marker) from both files
    SRC_TEMPLATE=$(sed -n "1,/$USER_MARKER/p" "$TEMPLATE" | head -n -1)
    TGT_TEMPLATE=$(sed -n "1,/$USER_MARKER/p" "$TARGET_MD" | head -n -1)

    SRC_MD5=$(echo "$SRC_TEMPLATE" | md5 2>/dev/null || echo "$SRC_TEMPLATE" | md5sum | cut -d' ' -f1)
    TGT_MD5=$(echo "$TGT_TEMPLATE" | md5 2>/dev/null || echo "$TGT_TEMPLATE" | md5sum | cut -d' ' -f1)

    if [ "$SRC_MD5" != "$TGT_MD5" ]; then
        # Extract user content from target (after marker line)
        USER_CONTENT=$(sed -n "/$USER_MARKER/,\$p" "$TARGET_MD" | tail -n +2)
        # Write template + preserved user content
        cat "$TEMPLATE" > "$TARGET_MD"
        [ -n "$USER_CONTENT" ] && echo "$USER_CONTENT" >> "$TARGET_MD"
        UPDATED="$UPDATED CLAUDE.md"
    fi
fi

[ -n "$UPDATED" ] && echo "SYNC:$UPDATED"
exit 0
