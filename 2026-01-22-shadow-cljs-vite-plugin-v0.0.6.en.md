---
title: "shadow-cljs-vite-plugin v0.0.6: Fixing HMR and ES Module Compatibility"
tags: [ClojureScript, shadow-cljs, Vite, HMR, Cloudflare Workers]
---

Two weeks after the initial release of [shadow-cljs-vite-plugin](https://github.com/bolasblack/shadow-cljs-vite-plugin), v0.0.6 brings some important fixes that make the development experience much smoother.

## Problem 1: ES Module Imports

When I first released the plugin, there were edge cases where ClojureScript code wouldn't import correctly in Vite's ESM environment. This was particularly problematic for users deploying to Cloudflare Workers.

The fix required coordinating with shadow-cljs upstream. We submitted a [PR #1249](https://github.com/thheller/shadow-cljs/pull/1249) to address the root cause, which was merged in v3.3.5. Thanks to [@thheller](https://github.com/thheller) for working through it with me!

## Problem 2: HMR "Namespace already declared" Error

This was a frustrating one. During hot module replacement, Vite re-executes module code, but global state (like `goog.loadedModules_`) persists across HMR updates. Google Closure Library assumes modules are loaded exactly once and throws errors on duplicate registration.

The solution was to patch `goog.provide` and `goog.module` to be idempotent:

```javascript
goog.provide = function(name) {
  return goog.isProvided_(name) ? undefined : goog.constructNamespace_.call(this, name);
};

goog.module = Object.assign(function(name) {
  if (name in goog.loadedModules_) {
    goog.moduleLoaderState_.moduleName = name;
    return;
  }
  return origModule.call(this, name);
}, origModule);
```

## Better HMR Batching

Previously, if you saved multiple files quickly (or your editor formatted on save), each change triggered a separate HMR update. Now the plugin batches multiple file changes into a single update using a debounce mechanism.

Additionally, changes to `shadow-cljs.edn` now automatically restart Vite, so you don't have to manually restart when modifying your build configuration.

## Tailwind CSS Support

The plugin works great with Tailwind CSS. I've been using this combination on [my blog](https://blog.c4605.com/en/) frontend with no issues.

## Try It Out

```bash
npm install shadow-cljs-vite-plugin
```

The plugin is stable and I'm using it in production. Feedback and contributions are always welcome on [GitHub](https://github.com/bolasblack/shadow-cljs-vite-plugin).
