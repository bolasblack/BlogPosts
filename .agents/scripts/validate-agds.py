#!/usr/bin/env python3
"""
Validate AGD (Agent-centric Governance Decision) files.

DO NOT MODIFY THIS FILE - it will be automatically updated from the skill directory.
To disable auto-update, add this filename to disableAutoUpdateScripts in config.json.

Usage:
    python3 validate-agds.py <project_dir>

Called by PostToolUse hook after Write/Edit operations.
Reads hook input from stdin to determine if validation is needed.
"""

import json
import os
import re
import sys
from pathlib import Path

from utils import (
    AGENTS_DIR,
    AGD_PATTERN,
    REF_FIELDS,
    find_agd_file,
    get_agents_dir,
    get_decisions_dir,
    parse_frontmatter,
)


def validate_tags(tags_str: str, allowed_tags: list[str], filename: str) -> list[str]:
    """Validate that all tags are in the allowed list."""
    if not tags_str:
        return []

    errors = []
    tags = [t.strip() for t in tags_str.split(',') if t.strip()]
    for tag in tags:
        if tag not in allowed_tags:
            errors.append(f"{filename}: invalid tag '{tag}' (not in config.tags)")
    return errors


def validate_references(frontmatter: dict, decisions_dir: Path, filename: str) -> list[str]:
    """Validate that all AGD references point to existing files."""
    errors = []

    for field in REF_FIELDS:
        if field not in frontmatter or not frontmatter[field]:
            continue

        refs = [r.strip() for r in frontmatter[field].split(',') if r.strip()]
        for ref in refs:
            ref_match = re.match(r'(AGD-\d+)', ref)
            if not ref_match:
                errors.append(f"{filename}: invalid reference format '{ref}' in {field}")
                continue

            if not find_agd_file(decisions_dir, ref):
                errors.append(f"{filename}: {field} references non-existent {ref_match.group(1)}")

    return errors


def check_script_updates(project_dir: Path, skill_dir: Path | None) -> None:
    """Check and update scripts if needed."""
    if not skill_dir or not skill_dir.exists():
        return

    config_path = get_agents_dir(project_dir) / 'config.json'
    if not config_path.exists():
        return

    try:
        with open(config_path) as f:
            config = json.load(f)
    except (json.JSONDecodeError, IOError):
        return

    disable_config = config.get('disableAutoUpdateScripts', [])
    if disable_config is True:
        return

    scripts_dir = get_agents_dir(project_dir) / 'scripts'
    skill_scripts_dir = skill_dir / 'scripts'

    if not skill_scripts_dir.exists():
        return

    for script_file in skill_scripts_dir.glob('*'):
        if script_file.name in disable_config:
            continue

        target_file = scripts_dir / script_file.name

        try:
            skill_content = script_file.read_text()
            if target_file.exists():
                if skill_content == target_file.read_text():
                    continue

            target_file.write_text(skill_content)
            if os.access(script_file, os.X_OK):
                target_file.chmod(target_file.stat().st_mode | 0o111)
        except IOError:
            pass


def validate_all_decisions(project_dir: Path) -> list[str]:
    """Validate all AGD files in the decisions directory."""
    errors = []
    decisions_dir = get_decisions_dir(project_dir)
    config_path = get_agents_dir(project_dir) / 'config.json'

    if not decisions_dir.exists():
        return errors

    allowed_tags = []
    if config_path.exists():
        try:
            with open(config_path) as f:
                allowed_tags = json.load(f).get('tags', [])
        except (json.JSONDecodeError, IOError):
            pass

    for agd_file in decisions_dir.glob(AGD_PATTERN):
        try:
            content = agd_file.read_text()
        except IOError as e:
            errors.append(f"{agd_file.name}: cannot read file - {e}")
            continue

        frontmatter = parse_frontmatter(content)

        if 'tags' in frontmatter:
            errors.extend(validate_tags(frontmatter['tags'], allowed_tags, agd_file.name))

        errors.extend(validate_references(frontmatter, decisions_dir, agd_file.name))

    return errors


def main():
    if len(sys.argv) < 2:
        print("Usage: validate-agds.py <project_dir>", file=sys.stderr)
        sys.exit(1)

    project_dir = Path(sys.argv[1])

    try:
        hook_input = json.load(sys.stdin)
    except (json.JSONDecodeError, IOError):
        hook_input = {}

    tool_input = hook_input.get('tool_input', {})
    file_path = tool_input.get('file_path', '')

    if file_path:
        decisions_path = str(get_decisions_dir(project_dir))
        if decisions_path not in file_path:
            sys.exit(0)

    skill_dir = os.environ.get('CLAUDE_SKILL_DIR')
    if skill_dir:
        check_script_updates(project_dir, Path(skill_dir))

    errors = validate_all_decisions(project_dir)

    if errors:
        print("AGD Validation Errors:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        sys.exit(1)

    generate_index_script = get_agents_dir(project_dir) / 'scripts' / 'generate-index.py'
    if generate_index_script.exists():
        os.system(f'python3 "{generate_index_script}" "{project_dir}"')

    sys.exit(0)


if __name__ == '__main__':
    main()
