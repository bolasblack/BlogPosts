---
title: "shadow-cljs-vite-plugin v0.0.7–v0.0.9: CLJS + React 混合 HMR 与稳定性改进"
tags: [ClojureScript, shadow-cljs, Vite, HMR, React, Cloudflare Workers]
---

[shadow-cljs-vite-plugin](https://github.com/bolasblack/shadow-cljs-vite-plugin) 在 [v0.0.6](https://www.reddit.com/r/Clojure/comments/1qjtcka/ann_shadowcljsviteplugin_v006_better_hmr_es/) 之后又经历了几个版本的迭代，主要解决了 CLJS 与 React/TypeScript 混合项目的 HMR 问题。

## CLJS + React/TypeScript 混合 HMR

之前，纯 ClojureScript 项目的 HMR 通过 shadow-cljs 的原生 eval 工作正常。但如果 TypeScript/React 代码通过 `virtual:shadow-cljs/app` 导入 CLJS 函数，热更新后 ES module 的导出值不会刷新——编辑 `.cljs` 文件后，shadow-cljs 会 eval 新代码，但 React 仍然渲染旧值。

现在这个问题已经修复，导出值会自动刷新，React 也会自动重新渲染：

```tsx
import { greet } from "virtual:shadow-cljs/app";

export default function App() {
  return <p>{greet("World")}</p>;
}
```

不需要事件监听、hooks 或其他样板代码。

实现这个功能比预想中复杂。shadow-cljs 和 Vite 使用各自独立的 WebSocket 连接，两者之间的时序无法保证，不能简单地在 stdout 出现 "Build completed" 时发送 Vite HMR 更新（eval 可能还没完成）。此外，`import.meta.hot.invalidate()` 对虚拟模块会静默失败（虚拟模块没有磁盘文件，服务端无法解析 invalidation）。

最终方案：客户端在收到构建完成信号后轮询全局命名空间，检测 shadow-cljs eval 是否已实际修改全局变量，刷新 ES module 的 live bindings，再通知服务端触发 React Fast Refresh。确定性执行，没有固定延迟，没有 monkey-patching。

## 修复：Ctrl+C 后 shadow-cljs JVM 残留

按下 Ctrl+C 后，shadow-cljs JVM 进程有时不会退出，导致下次 `vite dev` 时报 "server already running" 错误。原因是 pnpm 在 Ctrl+C 后很快向 Vite 发送 SIGTERM，Vite 在来不及向 JVM 发送 SIGKILL 之前就被终止了。修复方式是在信号处理器中使用同步 SIGKILL，不用 `await`，不会被中断。

## 新增示例

- [cljs-react](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-react) — CLJS 业务逻辑 + React UI，支持自动 HMR
- [cljs-ts-mixed](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-ts-mixed) — CLJS + TypeScript 混合项目，支持 HMR
- [cljs-reagent](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-reagent) — 纯 Reagent (ClojureScript) 应用，支持 HMR
- [cljs-cloudflare-worker](https://github.com/bolasblack/shadow-cljs-vite-plugin/tree/master/examples/cljs-cloudflare-worker) — CLJS 同时用于浏览器 (React) 和 Cloudflare Worker (SSR + JSON API)，使用 `@cloudflare/vite-plugin`，参考了[我的博客](https://blog.c4605.com/en/)的架构

## 其他改进

- Vite 的文件监听现在会忽略 shadow-cljs 的输出目录，避免不必要的 HMR 处理

## 发现 Vite Bug 并提交 PR

开发过程中发现 `import.meta.hot.invalidate()` 对虚拟模块静默失败。客户端发送了 invalidation，服务端也收到了，但没有任何效果——因为浏览器端的 URL（`/@id/__x00__virtual:...`）和服务端的模块 URL（`virtual:...`）不匹配。已提交修复：[vitejs/vite#22098](https://github.com/vitejs/vite/pull/22098)。

合并后，插件的 HMR 实现可以大幅简化——当前的 client→server→client 往返将被替换为一次 `invalidate()` 调用，由 Vite 内部处理传播。

## 试试看

插件已在 [blog.c4605.com](https://blog.c4605.com/en/) 的 Cloudflare Workers 生产环境稳定运行。

```bash
npm install shadow-cljs-vite-plugin
```

欢迎在 [GitHub](https://github.com/bolasblack/shadow-cljs-vite-plugin) 上反馈和贡献。
