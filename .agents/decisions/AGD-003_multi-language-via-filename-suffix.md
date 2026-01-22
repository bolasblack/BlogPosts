---
title: "Multi-language Articles Distinguished by Filename Suffix"
description: "Use filename suffix pattern (.en.md, .zh.md) to identify language versions of the same article"
tags: i18n, convention
---

## Context

The blog supports multiple languages. We need a way to:
1. Identify which language a post is written in
2. Link translations of the same article together
3. Keep the system simple without complex directory structures

## Decision

Use filename suffix to indicate language:
- Default language (Chinese): `2024-01-15-my-post.md`
- English version: `2024-01-15-my-post.en.md`
- Other languages: `2024-01-15-my-post.{lang}.md`

Articles with the **same base name** (after removing date and language suffix) are considered translations of each other.

Examples:
```
2024-01-15-hello-world.md      -> id: "2024-01-15-hello-world", lang: nil (default)
2024-01-15-hello-world.en.md   -> id: "2024-01-15-hello-world", lang: "en"
```

These two files are automatically linked as translations.

## Consequences

- Simple flat file structure, no language subdirectories needed
- Translation linking is automatic based on filename matching
- Generated JSON includes `translations` array for each post with links to other language versions
- Language suffix must be exactly 2 lowercase letters before `.md`
- Default language (no suffix) is configured in `settings.yml` as `lang: "zh"`
