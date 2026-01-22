#!/usr/bin/env python3
"""
Generate index files for the Agent Centric framework.

DO NOT MODIFY THIS FILE - it will be automatically updated from the skill directory.
To disable auto-update, add this filename to disableAutoUpdateScripts in config.json.

Usage:
    python3 generate-index.py <project_dir>

Generates:
    - INDEX-TAGS.md: Files with their tags
    - INDEX-AGD-RELATIONS.md: AGD obsoletes/updates relationships
"""

import sys
from pathlib import Path

from utils import (
    AGD_PATTERN,
    DECISIONS_DIR,
    find_agd_file,
    get_agd_sort_key,
    get_agents_dir,
    get_decisions_dir,
    parse_frontmatter,
)


def collect_agd_data(decisions_dir: Path) -> tuple[list, list]:
    """Collect tags and relations data from all AGD files."""
    tags_data = []       # [(relative_path, [tags])]
    relations_data = []  # [(source_path, target_path, relation_type)]

    for agd_file in sorted(decisions_dir.glob(AGD_PATTERN), key=lambda f: get_agd_sort_key(f.name)):
        try:
            content = agd_file.read_text()
        except IOError:
            continue

        frontmatter = parse_frontmatter(content)
        relative_path = f"{DECISIONS_DIR}/{agd_file.name}"

        # Collect tags
        if 'tags' in frontmatter and frontmatter['tags']:
            tags = [f"#{t.strip()}" for t in frontmatter['tags'].split(',') if t.strip()]
            if tags:
                tags_data.append((relative_path, tags))

        # Collect relationships
        for field, rel_type in [('obsoletes', 'o'), ('updates', 'u')]:
            if field in frontmatter and frontmatter[field]:
                refs = [r.strip() for r in frontmatter[field].split(',') if r.strip()]
                for ref in refs:
                    target_file = find_agd_file(decisions_dir, ref)
                    if target_file:
                        target_path = f"{DECISIONS_DIR}/{target_file.name}"
                        relations_data.append((relative_path, target_path, rel_type))

    return tags_data, relations_data


def write_tags_index(agents_dir: Path, tags_data: list) -> None:
    """Write INDEX-TAGS.md file."""
    content = "# Tags Index\n\n"
    content += "<!-- AUTO-GENERATED - DO NOT EDIT -->\n"
    content += "<!-- Search with: grep \"#tagname\" INDEX-TAGS.md -->\n\n"

    for path, tags in sorted(tags_data, key=lambda x: get_agd_sort_key(x[0])):
        content += f"{path}: {', '.join(tags)}\n"

    (agents_dir / 'INDEX-TAGS.md').write_text(content)


def write_relations_index(agents_dir: Path, relations_data: list) -> None:
    """Write INDEX-AGD-RELATIONS.md file."""
    content = "# AGD Relations Index\n\n"
    content += "<!-- AUTO-GENERATED - DO NOT EDIT -->\n"
    content += "<!-- -(o)-> : obsoletes, -(u)-> : updates -->\n"
    content += "<!-- Search with: grep \"AGD-001\" INDEX-AGD-RELATIONS.md -->\n\n"

    for source, target, rel_type in sorted(relations_data, key=lambda x: get_agd_sort_key(x[0])):
        content += f"{source} -({rel_type})-> {target}\n"

    (agents_dir / 'INDEX-AGD-RELATIONS.md').write_text(content)


def generate_indexes(project_dir: Path) -> None:
    """Generate all index files."""
    agents_dir = get_agents_dir(project_dir)
    decisions_dir = get_decisions_dir(project_dir)

    if not decisions_dir.exists():
        return

    tags_data, relations_data = collect_agd_data(decisions_dir)
    write_tags_index(agents_dir, tags_data)
    write_relations_index(agents_dir, relations_data)


def main():
    if len(sys.argv) < 2:
        print("Usage: generate-index.py <project_dir>", file=sys.stderr)
        sys.exit(1)

    project_dir = Path(sys.argv[1])
    generate_indexes(project_dir)
    sys.exit(0)


if __name__ == '__main__':
    main()
