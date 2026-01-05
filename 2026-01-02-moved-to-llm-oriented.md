---
title: 我把我的配置共享仓库转移到了 LLM-oriented 模式
tags: [LLM, 工具配置, ESLint, npm]
---

## 啥是 "LLM-oriented 的配置共享方案"

简单来说，就是让配置共享项目提供 [llms.txt](https://llmstxt.org/) ，使用方依此让 LLM 去下载/更新项目的配置文件，以保持配置的更新。而不是通过 npm registry 来分发配置。

## 那为不用 npm 了？

传统的工具配置共享方式，无论是通过 `extends` 继承还是 preset 预设，都面临一个根本性的矛盾：**便利性与灵活性的两难选择**。

### 继承模式的优点

当你使用类似 `extends: "@company/eslint-config"` 的方式时，好处显而易见：

- **升级简单**：只需 `npm update`，所有使用该配置的项目都能获得更新
- **一致性保证**：团队中所有项目使用相同的规则
- **维护集中**：配置的改进和 bug 修复只需要在一个地方进行

### 但现实很骨感

问题出现在你需要做任何"非标准"操作的时候：

1. **自定义需求**：项目有特殊需求，需要覆盖某些规则
2. **依赖版本冲突**：你需要升级 ESLint，但上游配置包还没有跟进（比如 ESLint 更新到 flat mode config 了，你需要用到最新版的 ESLint 了，但是我懒，我还没更新 🤪）
3. **规则分歧**：上游更新了某个规则，但你的项目不适用（问题是你一开始可能不知道，等出问题了才发现"卧槽我被坑了"）

这时候，你有两个选择：

**选择 A：在继承基础上打补丁**

```javascript
// eslint.config.js
export default [
  ...baseConfig,
  {
    rules: {
      // 覆盖一个规则
      "some-rule": "off",
      // 再覆盖一个
      "another-rule": ["error", { different: "options" }],
    },
  },
  // 还要处理插件版本冲突...
];
```

补丁越打越多，最终你的"继承"变成了一层又一层的覆盖，反而比从头写还难维护。

ESLint 其实已经还好了，Prettier 和 lint-staged 这种没有 extends/merge 机制的就更麻烦了，我之前写了一个超级麻烦的 lint-staged 配置（看这里 [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.config.js) 和 [`lint-staged.helpers.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.helpers.js)，看起来挺 decent 的（好吧至少我自己这么觉得），其实超级难用，还把一个非常简单的事情搞的极其复杂

**选择 B：Eject（弹出）**

很多脚手架工具提供了 `eject` 命令，把所有配置文件复制到你的项目里，让你获得完全控制权。

但 eject 之后：

- 你失去了自动升级的能力，你得自己去更新，没办法享受上游已经测试过的插件之间的兼容性
- 当上游有重要更新时，你需要手动 diff 和合并

**这就是两难困境**：继承模式下你受制于上游，eject 之后你要承担维护成本。

## LLM 是个好东西

现在我们有了 LLM 了，时代变了

### 新的工作流

1. **获取最新配置**：LLM 直接从 llms.md/llms.txt 获取最新的推荐配置
2. **智能合并**：LLM 理解你的项目上下文，把新配置合并到现有配置中
3. **保留自定义**：你的自定义修改会被识别并保留
4. **按需更新**：当你需要升级时，让 LLM 帮你处理合并冲突

### 为什么这比传统方式更好？

**所有配置都在你的代码库中**

没有隐藏的 `node_modules` 深处的配置文件，没有"这个规则是从哪里来的"的困惑。每一行配置都清清楚楚地在你的项目里。

**升级变得可控**

升级不再是一个"要么全要，要么不要"的决定。LLM 可以帮你：

- 查看上游有什么变化
- 理解每个变化的影响
- 选择性地应用更新
- 自动处理合并冲突

**自定义是一等公民**

你的自定义不再是"补丁"，而是配置的一部分。LLM 在更新时会理解并尊重这些自定义。

**依赖版本自由**

你可以随时升级任何依赖，不需要等待上游配置包更新。遇到问题？还能让 LLM 帮你调整配置（他们读起配置来也变简单了）。

**上游的配置文件变的更简单了**

还是看我的 lint-staged 配置文件

以前的版本：

- [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.config.js)
- 为了方便下游能复用配置，还能自定义 lint-staged 的配置，不得不写的帮助函数 [`lint-staged.helpers.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.helpers.js)

现在的版本：

- [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/25d4495adcba67bda9daf65b04a204acc01cb795/packages/toolconfs/lint-staged.config.js)
  算了，这个实在是太简单了，我直接 inline 了得了：
  ```javascript
  module.exports = {
    "*.{ts,tsx,js,jsx}": ["prettier --write", "eslint"],
    "*.{css,scss,sass,less,md,mdx}": ["prettier --write"],
  };
  ```
- `lint-staged.helpers.js` 已经不存在了，因为没必要

## 你说了这么多，但这看起来不还是倒退吗？

是，也不是。

- **是**：配置文件确实在每个项目中独立存在，和最早的时候一样
- **不是**：因为维护成本发生了本质变化：
  - **以前**：人工去看 CHANGELOG 或者看和上游配置文件的 diff -> 搞明白啥变了 -> 手动修改 -> 测试验证
  - **现在**：告诉 LLM "升级到最新推荐配置" -> 审查 LLM 的改动 -> 完成

维护的认知负担从"我需要理解所有这些配置"变成了"我需要审查 LLM 的建议"。这是和最早的时候不同的地方。

## 总结（AI 版，我自己编不出来）

传统的配置共享是在"一刀切的便利"和"完全自主的复杂"之间做选择。LLM-oriented 的方式让我们可以两者兼得：

- 保持配置的完全所有权和透明度
- 同时享受智能辅助的升级和维护

这不是技术的倒退，而是利用新工具重新定义了最佳实践。
