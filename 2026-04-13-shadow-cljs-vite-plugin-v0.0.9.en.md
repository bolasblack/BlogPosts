---
title: "shadow-cljs-vite-plugin v0.0.7–v0.0.9: Mixed CLJS + React HMR and Stability Fixes"
tags: [ClojureScript, shadow-cljs, Vite, HMR, React, Cloudflare Workers]
---

[shadow-cljs-vite-plugin](https://github.com/bolasblack/shadow-cljs-vite-plugin) has gone through several iterations since [v0.0.6](https://www.reddit.com/r/Clojure/comments/1qjtcka/ann_shadowcljsviteplugin_v006_better_hmr_es/), primarily addressing HMR issues in mixed CLJS + React/TypeScript projects.

## Mixed CLJS + React/TypeScript HMR

Previously, HMR worked fine for pure ClojureScript apps via shadow-cljs's native eval. But if TypeScript/React code imported CLJS functions through `virtual:shadow-cljs/app`, ES module exports went stale after hot-reload — editing a `.cljs` file would trigger shadow-cljs eval, but React kept rendering old values.

This is now fixed. Exports stay fresh and React re-renders automatically:

```tsx
import { greet } from "virtual:shadow-cljs/app";

export default function App() {
  return <p>{greet("World")}</p>;
}
```

No event listeners, hooks, or boilerplate needed.

Getting this right was trickier than expected. shadow-cljs and Vite use separate WebSocket connections, with no guaranteed timing between them — we can't simply send a Vite HMR update when "Build completed" appears on stdout (eval might not have finished yet). On top of that, `import.meta.hot.invalidate()` silently fails for virtual modules (no file on disk, so the server can't resolve the invalidation).

The solution: the client polls the global namespace after receiving a build-complete signal, detects when shadow-cljs eval has actually mutated the globals, refreshes the ES module live bindings, then signals the server to trigger React Fast Refresh. Deterministic, no fixed delays, no monkey-patching.

## Fixed: shadow-cljs JVM Surviving Ctrl+C

After pressing Ctrl+C, the shadow-cljs JVM process sometimes stayed alive, causing "server already running" errors on the next `vite dev`. Root cause: pnpm sends SIGTERM to Vite shortly after Ctrl+C, killing Vite before it could send SIGKILL to the JVM. Fixed by using synchronous SIGKILL in signal handlers — no `await`, can't be interrupted.

## New Examples

- [cljs-react](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-react) — CLJS business logic + React UI with auto HMR
- [cljs-ts-mixed](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-ts-mixed) — CLJS + TypeScript mixed project with HMR
- [cljs-reagent](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-reagent) — Pure Reagent (ClojureScript) app with HMR
- [cljs-cloudflare-worker](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-cloudflare-worker) — CLJS shared between browser (React) and Cloudflare Worker (SSR + JSON API), using `@cloudflare/vite-plugin`. Modeled after [my blog](https://blog.c4605.com/en/)

## Other Improvements

- Vite's file watcher now ignores shadow-cljs output directories, preventing unnecessary HMR processing

## Vite Bug Found & PR Submitted

During development, we discovered that `import.meta.hot.invalidate()` silently fails for virtual modules. The client sends the invalidation, the server receives it, but nothing happens — because the browser-side URL (`/@id/__x00__virtual:...`) doesn't match the server-side module URL (`virtual:...`). A fix has been submitted: [vitejs/vite#22098](https://github.com/vitejs/vite/pull/22098).

Once merged, the plugin's HMR implementation can be simplified significantly — the current client→server→client round-trip would be replaced by a single `invalidate()` call, letting Vite handle propagation internally.

## Give It a Try

The plugin has been running stably in production on Cloudflare Workers at [blog.c4605.com](https://blog.c4605.com/en/).

```bash
npm install shadow-cljs-vite-plugin
```

Feedback and contributions are welcome on [GitHub](https://github.com/bolasblack/shadow-cljs-vite-plugin).
