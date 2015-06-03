---
title: "关于 React 和 Angular 的看法"
category: ["JavaScript"]
tags: ["reactjs", "angularjs"]
---

我最近也用了一段时间的 reactjs ，所以我想有还是稍微有点资格来谈论 angularjs 和 reactjs 的。

有一些文章会提到 angularjs 的两个很关键的问题：controller 拥有了自己的状态，和声明依赖比较容易出现循环依赖。

Facebook 确实提出了一个非常不错的解决方案，但这方案与 Virtual DOM 没有直接的关系（一些文章总是提这东西，我承认这是一个很重要的特性，但和解决 angularjs 的问题关系不大）。

这个方案是 [Flux](http://facebook.github.io/flux/) ：通过发布事件的方式来提醒模型更新状态，进而更新所有相关 DOM ，一个很巧妙的解决方案。而 Virtual DOM 只是这个解决方案里保证性能的一环而已。

而 data-binding ，实话说看到一些 reactjs 的拥趸一天到晚扯 data-binding 是邪恶的让我觉得很莫名其妙。data-binding 只是一种模式而已，reactjs 内置了数据的单向绑定难道这就不是 data-binding ？而且 reactjs 还带了一个扩展 [ReactLink](http://facebook.github.io/react/docs/two-way-binding-helpers.html) 用来实现数据的双向绑定又是什么意思？难道在 Vitrual DOM 的 `onChange` 属性里传一个回调函数然后更新 `state` 或者发出事件更新模型就不是数据绑定了？那么其他框架实现双向绑定的方法 “监听 DOM 事件，更新 ViewModel” 也不能算是双向绑定咯？因为这其实和在 reactjs 里做的没有任何区别。

我的观点就是，reactjs 是一个不错的东西，Flux 解决了不少其他 MVVM 框架的问题，而 data-binding 绝对是一种进步，因为它大量的减少了开发者的代码量（早就有人 [实现](https://github.com/spoike/refluxjs#using-refluxconnect) 了一个使用 flux 模式的双向绑定扩展了）。
