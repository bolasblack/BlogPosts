---
title: "我搞了一个 Elm 风格的 useEffectReducer hook"
tags: [架构, React, Elm, Redux]
---

**GitHub:** [bolasblack/react-components/tree/develop/packages/useEffectReducer](https://github.com/bolasblack/react-components/tree/develop/packages/useEffectReducer)

## 什么是 "Elm 风格"？

Elm 有一个 **Model-View-Update** 模式，其中每次更新都会返回下一个 model 和要执行的命令：

```elm
update : Msg -> Model -> ( Model, Cmd Msg )
```

这种设计将所有关于 _"事件 X 发生时会发生什么"_ 的逻辑集中在 **一个地方** —— update 函数 —— 而不是将副作用分散在各种 `useEffect` 中。

它还保持了 reducer 的纯洁性，同时让 **副作用何时发生、发生什么** 变得明确，并可被运行时解释执行。

React 官方文档也呼应了这一理念：effect 应该是一个 _逃生舱_，而不是默认选择 —— 参见 [You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect)。

> 如果你想了解这种建模方式如何让 UI 逻辑变得优雅且易于维护，可以看看 David Khourshid 的经典文章 [**"No, disabling a button is not app logic."**](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i)

## 为什么 _又_ 造一个 Elm 风格的 reducer？

因为没有完全满足我的需求的版本：

- **有些已经归档了。** 例如，[`useEffectReducer`](https://github.com/davidkpiano/useEffectReducer) 和 [`react-use-bireducer`](https://github.com/soywod/react-use-bireducer) 现在都是只读状态。

- **保持精简。** 只需一个文件，几乎没有依赖 —— 随时可以复制、修改或删除。

- **Effect 作为普通对象 + 独立的解释器。** 返回可序列化的 effect descriptors，然后在一个专门的地方实现实际的 effect 逻辑。这样更容易测试 reducer（只需断言描述符），而无需真正执行副作用。

- **降低门槛。** 目标是让 Elm 风格的方法变得易于上手，而不需要深入了解 Elm 的 `Cmd Msg` 系统，也不需要学习像 [XState](https://xstate.js.org/) 这样的完整状态机库。

## 来点代码

我们用 `useEffectReducer` 重新实现一下 David Khourshid 文章 [**"No, disabling a button is not app logic."**](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i) 中的示例。

两种实现都可以在 GitHub 上找到：

- **useEffectReducer 版本:** [useEffectReducer.stories.tsx → L121–203](https://github.com/bolasblack/react-components/blob/eb47a2416e8cc95bb4fa7e6fbf776ac2432a2468/packages/useEffectReducer/src/useEffectReducer.stories.tsx#L121-L203)
- **useReducer 版本:** [useEffectReducer.stories.tsx → L24–119](https://github.com/bolasblack/react-components/blob/eb47a2416e8cc95bb4fa7e6fbf776ac2432a2468/packages/useEffectReducer/src/useEffectReducer.stories.tsx#L24-L119)

## 其他人的工作

将 **Elm 风格的 "状态 + 副作用" reducer** 引入 React 的优秀现有实现：

- **[`davidkpiano/useEffectReducer`](https://github.com/davidkpiano/useEffectReducer)** — 作者 _David Khourshid_；已归档
- **[`soywod/react-use-bireducer`](https://github.com/soywod/react-use-bireducer)** — 返回 `[state, effects]` 并通过独立的 effect reducer 处理副作用；已归档
- **[`ncthbrt/react-use-elmish`](https://github.com/ncthbrt/react-use-elmish)** — Elmish 风格的 hook，结合了 reducer 逻辑和异步辅助函数
- **[`redux-loop`](https://github.com/redux-loop/redux-loop)** — Redux 增强器，添加类似 Elm 的 effect 元组
- **[`dai-shi/use-reducer-async`](https://github.com/dai-shi/use-reducer-async)** — 扩展 `dispatch` 以支持异步 action
- **[`useReducerWithEmitEffect`](https://gist.github.com/sophiebits/145c47544430c82abd617c9cdebefee8)** — Sophie Alpert 的 gist，启发了本工作的大部分内容

## 好文章

- Christian Ekrem - "[Chapter 2, Take 2: Why I Changed Course](https://cekrem.github.io/posts/chapter-2-take-2/)"
- David Khourshid – "[Redux is half of a pattern (1/2)](https://dev.to/davidkpiano/redux-is-half-of-a-pattern-1-2-1hd7)"
- David Khourshid – "[There are so many fundamental misunderstandings about XState (and state machines in general)](https://medium.com/@DavidKPiano/there-are-so-many-fundamental-misunderstandings-about-xstate-and-state-machines-in-general-in-13aec57d2f85)"
- David Khourshid – "[No, disabling a button is not app logic.](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i)"
- React docs – "[You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect)"
