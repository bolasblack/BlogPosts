---
title: TypeScript 如何真正 keyof 一个枚举
category: [TypeScript]
tags: [TypeScript, 枚举]
---

有一个朋友问我怎么才能 `keyof` 一个 TypeScript 里的枚举，因为如果你直接 `keyof Enum` 的话，得到的结果是类似 `keyof number` ，而不是得到枚举的所有可能的 key 。

于是我简单写了一下跟他解释，顺手发上来：

在 TypeScript 里，你在声明一个枚举的时候，其实声明了两个类型，一个是枚举容器的类型（是一个对象），一个是枚举成员的类型（是数字/字符串或者其他类型的子类型）。 

TypeScript 的 `keyof` 操作符预设后面跟着的是类型，所以我们 `keyof Enum` 时，其实相当于是在 `keyof (作为 number 的子类型的 Enum)` ，也就是相当于 `keyof number` ，所以会得到 `number` 的属性和方法名。

而 `typeof` 操作符预设后面跟的是一个值，所以当我们这么写 `keyof typeof Enum` 的时候，我们是把 `Enum` 作为一个值（也就是上文提到的“对象”），展开来以后就是 `keyof (作为类型的 Enum)` ，所以能够得到枚举的 key 。

相关资料：

* [keyof Enum - microsoft/TypeScript#14106 - GitHub](https://github.com/microsoft/TypeScript/issues/14106)
