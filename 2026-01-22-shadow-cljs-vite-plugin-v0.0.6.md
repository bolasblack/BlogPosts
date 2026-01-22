---
title: "shadow-cljs-vite-plugin v0.0.6: 修复 HMR 和 ES Module 兼容性问题"
tags: [ClojureScript, shadow-cljs, Vite, HMR, Cloudflare Workers]
---

[shadow-cljs-vite-plugin](https://github.com/bolasblack/shadow-cljs-vite-plugin) 首次发布两周后，v0.0.6 带来了一些重要的修复，让开发体验更加顺滑。

## 问题一：ES Module 导入

首次发布时，ClojureScript 代码在 Vite 的 ESM 环境中存在一些导入问题，在部署到 Cloudflare Workers 时尤为明显。

这个问题需要上游配合修复，我们向 shadow-cljs 提交了一个 [PR #1249](https://github.com/thheller/shadow-cljs/pull/1249)，已在 v3.3.5 合并。感谢 [@thheller](https://github.com/thheller) 一起解决这个问题！

## 问题二：HMR 时 "Namespace already declared" 错误

这个问题比较恼人。在热更新时，Vite 会重新执行模块代码，但全局状态（比如 `goog.loadedModules_`）会跨 HMR 更新持久化。Google Closure Library 假设模块只会被加载一次，遇到重复注册就会抛错。

解决方案是让 `goog.provide` 和 `goog.module` 变成幂等操作：

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

## 更好的 HMR 批处理

之前如果你快速保存多个文件（或者编辑器保存时自动格式化），每次变更都会触发一次 HMR 更新。现在插件会使用防抖机制将多个文件变更合并为单次更新。

另外，修改 `shadow-cljs.edn` 后 Vite 会自动重启，不用再手动重启了。

## Tailwind CSS 支持

插件和 Tailwind CSS 配合良好，我已经在[我的博客](https://blog.c4605.com/)前端使用这个组合，没有任何问题。

## 试试看

```bash
npm install shadow-cljs-vite-plugin
```

插件已经稳定，我正在生产环境使用。欢迎在 [GitHub](https://github.com/bolasblack/shadow-cljs-vite-plugin) 上反馈和贡献。
