---
title: "I built (another) Elm-style useEffectReducer hook for React"
tags: [architecture, React, Elm, Redux]
---

**GitHub:** [bolasblack/react-components/tree/develop/packages/useEffectReducer](https://github.com/bolasblack/react-components/tree/develop/packages/useEffectReducer)

## What is "Elm-style"?

Elm popularized the **Model-View-Update** pattern, where each update returns both the next model and the commands to run:

```elm
update : Msg -> Model -> ( Model, Cmd Msg )
```

This design keeps all logic about _"what happens when event X occurs"_ in **one place** — the update function — instead of scattering side effects across random `useEffect`s.

It also keeps reducers pure while making **when and what side effects happen** explicit and interpretable by a runtime.

React's docs echo this philosophy: effects should be an _escape hatch_, not the default — see [You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect).

> If you want to see how this kind of modeling makes UI logic elegant and maintainable, check out David Khourshid's classic post [**"No, disabling a button is not app logic."**](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i)

## So why _another_ Elm-style reducer?

The author wanted a variant with different trade-offs:

- **Some are archived.** For example, [`useEffectReducer`](https://github.com/davidkpiano/useEffectReducer) and [`react-use-bireducer`](https://github.com/soywod/react-use-bireducer) are now read-only.

- **Keep it tiny.** Fits in one file with almost no dependencies — copy, tweak, or delete it whenever you want.

- **Effects as plain objects + separate interpreter.** Returns serializable effect descriptors and implements the actual effect logic in one dedicated place. It's easier to test reducers (assert on descriptors) without invoking real side effects.

- **Lower the barrier.** The goal is to make the Elm-style approach approachable without requiring deep knowledge of Elm's `Cmd Msg` system or learning a full-blown state machine library like [XState](https://xstate.js.org/).

## Example usage

The article reimplements the example from David Khourshid's article [**"No, disabling a button is not app logic."**](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i) using `useEffectReducer`.

Both implementations are available on GitHub:

- **useEffectReducer version:** [useEffectReducer.stories.tsx → L121–203](https://github.com/bolasblack/react-components/blob/eb47a2416e8cc95bb4fa7e6fbf776ac2432a2468/packages/useEffectReducer/src/useEffectReducer.stories.tsx#L121-L203)
- **useReducer version:** [useEffectReducer.stories.tsx → L24–119](https://github.com/bolasblack/react-components/blob/eb47a2416e8cc95bb4fa7e6fbf776ac2432a2468/packages/useEffectReducer/src/useEffectReducer.stories.tsx#L24-L119)

## Related work

Excellent existing takes on bringing **Elm-style "state + effects" reducers** into React:

- **[`davidkpiano/useEffectReducer`](https://github.com/davidkpiano/useEffectReducer)** — by _David Khourshid_; archived
- **[`soywod/react-use-bireducer`](https://github.com/soywod/react-use-bireducer)** — returns `[state, effects]` and processes effects through a separate effect reducer; archived
- **[`ncthbrt/react-use-elmish`](https://github.com/ncthbrt/react-use-elmish)** — Elmish-style hook combining reducer logic with async helpers
- **[`redux-loop`](https://github.com/redux-loop/redux-loop)** — Redux enhancer adding Elm-like effect tuples
- **[`dai-shi/use-reducer-async`](https://github.com/dai-shi/use-reducer-async)** — extends `dispatch` for async actions
- **[`useReducerWithEmitEffect`](https://gist.github.com/sophiebits/145c47544430c82abd617c9cdebefee8)** — Sophie Alpert's gist that inspired much of this work

## Good Articles

- Christian Ekrem - "[Chapter 2, Take 2: Why I Changed Course](https://cekrem.github.io/posts/chapter-2-take-2/)"
- David Khourshid – "[Redux is half of a pattern (1/2)](https://dev.to/davidkpiano/redux-is-half-of-a-pattern-1-2-1hd7)"
- David Khourshid – "[There are so many fundamental misunderstandings about XState (and state machines in general)](https://medium.com/@DavidKPiano/there-are-so-many-fundamental-misunderstandings-about-xstate-and-state-machines-in-general-in-13aec57d2f85)"
- David Khourshid – "[No, disabling a button is not app logic.](https://dev.to/davidkpiano/no-disabling-a-button-is-not-app-logic-598i)"
- React docs – "[You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect)"
