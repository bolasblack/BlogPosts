---
title: "Use Babashka as Build Tool"
description: "Choose Babashka (bb) for blog generation scripts instead of JVM Clojure, Node.js, or Python"
tags: tooling
---

## Context

The blog system needs a build script to:
- Parse markdown posts with YAML frontmatter
- Generate JSON data files for pagination and tags
- Generate Atom feed XML
- Convert markdown to HTML via pandoc

The frontend is written in ClojureScript. We want consistency in the tech stack while avoiding heavy runtime dependencies.

## Decision

Use Babashka (bb) as the build tool.

Reasons:
1. **Language consistency** - Frontend uses ClojureScript, so using Clojure for build scripts keeps the codebase in one language family
2. **Fast startup** - JVM Clojure has slow startup time (several seconds), making it unsuitable for quick build tasks
3. **Low memory footprint** - JVM is memory-heavy; Babashka uses GraalVM native image
4. **Rich ecosystem** - Babashka includes commonly needed libraries (fs, process, json, yaml, http) out of the box

## Consequences

- Build scripts are in `_meta/*.clj` and run with `bb`
- Dependencies declared in `_meta/bb.edn`
- Developers/AI modifying build scripts should know Clojure syntax
- Cannot use JVM-only Clojure libraries (but babashka pods can bridge some gaps)
