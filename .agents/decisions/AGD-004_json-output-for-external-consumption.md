---
title: "JSON Output Structure for External System Consumption"
description: "Generate structured JSON files so external systems can render the blog without parsing markdown"
tags: core
---

## Context

Goal: Enable a **pure frontend SPA** to render the complete blog functionality without:
- Cloning the entire repository
- Parsing markdown files
- Analyzing file structure
- Running any build process

The frontend should only need to fetch JSON files via HTTP.

## Decision

Generate a comprehensive JSON data structure in `_meta/data/` (and `_meta/data.{lang}/` for other languages):

### Directory Structure
```
_meta/
├── data/                    # Default language (zh)
│   ├── index.json          # Page 1 with pagination info
│   ├── page-2.json         # Page 2, etc.
│   ├── tags.json           # Tag index with counts
│   └── tags/
│       ├── javascript.json # Posts for each tag
│       └── ...
├── data.en/                # English posts
│   └── (same structure)
├── feed.xml                # Atom feed (default lang)
└── feed.en.xml             # Atom feed (English)
```

### JSON Schema (index.json / page-N.json)
```json
{
  "meta": {
    "total-posts": 25,
    "total-pages": 3,
    "per-page": 10,
    "generated-at": "2024-01-15T10:00:00+08:00"
  },
  "pagination": {
    "current-page": 1,
    "has-prev": false,
    "has-next": true,
    "prev-file": null,
    "next-file": "page-2.json"
  },
  "posts": [
    {
      "id": "2024-01-15-my-post",
      "path": "2024-01-15-my-post.md",
      "title": "My Post",
      "date": "2024-01-15",
      "tags": ["javascript"],
      "created-at": "2024-01-15T00:00:00+08:00",
      "updated-at": "2024-01-20T10:00:00+08:00",
      "url": "https://github.com/.../2024-01-15-my-post.md",
      "translations": [
        {
          "lang": "en",
          "title": "My Post (EN)",
          "data-file": "data.en/index.json"
        }
      ]
    }
  ]
}
```

## Consequences

- Frontend SPA can render blog list, pagination, tag filtering with simple `fetch()` calls
- No server-side processing required for basic functionality
- Post content still requires fetching markdown from GitHub (or use the Atom feed for recent posts with HTML content)
- JSON files must be regenerated when posts change (handled by git hooks)
- File size grows with post count, but pagination keeps individual files small
