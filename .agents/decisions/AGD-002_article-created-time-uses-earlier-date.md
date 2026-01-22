---
title: "Article Created Time Uses Earlier of Filename Date and Git First Commit"
description: "Determine article creation time by taking the earlier of the date in filename and git first commit date"
tags: core
---

## Context

Each blog post needs a `created-at` timestamp. There are two potential sources:
1. **Filename date** - Posts follow `YYYY-MM-DD-title.md` naming convention
2. **Git history** - The first commit date of the file

Problem: These dates can differ. For example:
- An article written on 2024-01-15 might be committed to git on 2024-01-20
- Or a file might be renamed/moved, making git history unreliable

We want `created-at` to represent when the article was actually written, not when it was committed.

## Decision

Take the **earlier** of:
1. The date extracted from filename (`YYYY-MM-DD` prefix)
2. The git first commit date (from `git log --follow`)

Implementation in `generate_files.clj`:
```clojure
(defn earlier-date [date1 date2]
  (cond
    (nil? date1) date2
    (nil? date2) date1
    :else (if (.isBefore (parse date1) (parse date2)) date1 date2)))

;; In build-post-metadata:
:created-at (earlier-date default-date (format-date (:created-at git-dates)))
```

## Consequences

- Article creation time is more accurate and closer to actual writing time
- Filename date serves as a reliable fallback when git history is incomplete
- Git history is still used when it provides an earlier date (e.g., file renamed with new date prefix)
- **Do not "simplify" this logic** to use only one source - both are needed for accuracy
