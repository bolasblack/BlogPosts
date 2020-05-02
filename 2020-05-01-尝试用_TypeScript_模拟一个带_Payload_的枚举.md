---
title: 尝试用 TypeScript 模拟一个带 Payload 的枚举
category: []
tags: []
---

这两天逛 GitHub 时突然又注意到了 [folktale](https://github.com/origamitower/folktale) ，发现它现在带了一个 [adt/union](https://folktale.origamitower.com/api/v2.3.0/en/folktale.adt.union.union.union.html) 。看起来 folktale 现在的 `Maybe`, `Result`, `Validation` 都是从 union 里派生出来的，使用方法和 Swift/Rust 里的枚举非常相似：

```javascript
const Either = union('Either', {
  Left: (value) => ({ value }),
  Right: (value) => ({ value }),
})

console.log(
  Either.Right(1).matchWith({
    Left: ({ value }) => `left ${value}`,
    Right: ({ value }) => `right ${value}`,
  })
)
```

正好我想在 TypeScript 里模拟一个带 Payload 的枚举机制已经想了很久了，觉得这个 API 设计得不错，可惜的是没有 TypeScript 的类型定义，在各种抓耳挠腮之下写了一个不完美的版本（下面解释）：

```typescript
// 这是一个假的函数，只是做个占位而已
export function union<P extends BaseUnionPatterns>(
  typeId: string,
  patterns: P,
): UnionTypes<P> {
  return null as any
}

export type UnionModelMatchPatterns<P extends BaseUnionPatterns, R> = {
  [K in keyof P]: (patternFnReturn: ReturnType<P[K]>) => R
}

export type UnionModelMatchPatternsWithAny<P extends BaseUnionPatterns, R> = {
  [K in keyof P]?: (patternFnReturn: ReturnType<P[K]>) => R
} & {
  $$any: (patternFnReturn: ReturnType<P[keyof P]>) => R
}

export type UnionInstance<P extends BaseUnionPatterns, Type extends keyof P> = {
  equals: (unionInstance: UnionInstance<P, any>) => boolean
  matchWith: <R>(
    branches:
      | UnionModelMatchPatterns<P, R>
      | UnionModelMatchPatternsWithAny<P, R>,
  ) => R
}

export interface UnionType<P extends BaseUnionPatterns, K extends keyof P> {
  (...args: Parameters<P[K]>): UnionInstance<P, K>
  hasInstance: (model: UnionInstance<P, keyof P>) => boolean
}

export type UnionTypes<P extends BaseUnionPatterns> = {
  [K in keyof P]: UnionType<P, K>
}

export interface BaseUnionPatterns {
  [type: string]: (...args: any) => any
}
```

事情并不如愿，总归还是没有办法完全实现我想要的效果，最终的成效是：

```typescript
// 在这段代码里

// Maybe 变量的类型是对的
const Maybe = union('Maybe', { // patterns 参数的类型约束是对的
  Nothing: () => null,
  Just: <T>(value: T) => ({ value }),
  Other: <T>(arg1: T, rest: T[]) => [arg1].concat(rest),
})

// a 变量的类型是对的
const a = Maybe.Just(1)
// b 变量的类型被成功推断
const b = a.matchWith({ // branches 的类型约束是对的
  Nothing: (arg) => 'no value', // arg 的类型确实是 null
  Just: (arg) => `value: ${arg.value}`, // arg 的类型是 { value: unknown } （问题一）
  Other: (arg) => `values: ${arg.join(',')}`, // arg 的类型是 unknown[] （问题二）
})
a.matchWith({
  Just: (arg) => `value: ${arg.value}`,
  // TypeScript 里指定的 Symbol 没办法作为 key 的类型，所以只能用特殊 key $$any
  // 来替代 folktale 里的 any Symbol （问题三）
  $$any: (arg) => `values: ${arg}`,
})

console.log(b)
```

我们来一个个看这些问题：

* 关于问题一，让人比较不爽，毕竟 `Maybe.Just` 的参数类型是出现过的，只是因为 `patterns` 参数里的 `Just` 函数是一个泛型函数，而我们在做类型转换的时候又没办法传递类型参数，所以没办法推断出 `{ value: T }` 里 `T` 的类型；
* 关于问题二，其实可以理解的，毕竟整个 `a` 变量的生命周期里从来没提到过 `Other` 的 Payload 类型；
* 关于问题三，和问题一一样，都是属于语言设施功能上的缺位，大概只能等 TypeScript 支持相关的特性。

后来结合问题一问题二一想，其实这两个问题可以尝试一起解决，我们只要在声明变量 `a` 的时候给它传递一个更完整的类型信息就可以了，于是又一阵抓耳挠腮之后，终于写出来一个粗糙的方案：

```typescript
export type UnionV<
  T extends UnionTypes<any>,
  WrappedValues extends T extends UnionTypes<infer P>
    ? { [K in keyof P]: ReturnType<P[K]> }
    : never
> = UnionInstance<
  { [K in keyof T]: (...args: Parameters<T[K]>) => WrappedValues[K] },
  keyof T
>

const Maybe = union('Maybe', {
  Nothing: () => null,
  Just: <T>(value: T) => ({ value }),
  Others: <T>(con: T, rest: T[]) => [con].concat(rest),
})

type MaybeNumber = UnionV<
  typeof Maybe,
  { // 这里填进去 patterns 各个函数的返回值类型
    Nothing: null
    Just: { value: number }
    Others: number[]
  }
>
const a = Maybe.Just('a') as MaybeNumber
// 这回 b 的类型被成功推断为 number | null
const b = a.matchWith({
  Nothing: (arg) => arg,
  Just: (arg) => arg.value, // arg 的类型为 { value: number }
  Others: (items) => items.length, // arg 的类型为 number[]
})
console.log(b)
```

目前这套方案在 `patterns` 的参数都是具体的类型时，不需要 `UnionV` 就能够工作得很好；在出现泛型函数时，我只能想到通过 `UnionV` 来为 tsc 提供额外的类型信息。

那么，我们这算是大功告成了吗？并没有，因为整套方案是真的不够优雅，`UnionV` 的第二个类型参数实在是让人倒胃口。而且如果你仔细看的话，会发现我上面这段代码里 `Maybe.Just` 的参数是一个字符串，而 tsc 并没有报错。我知道是因为我用了转型，但是如果我这么写 `a: MaybeNumber =` ，tsc 会报错说 `Type '{ value: unknown; }' is not assignable to type '{ value: number; }'.` ，所以……这就是为什么这个方案不够优雅的原因之二了。

目前还想不出来更好的方案，只能再等等了，看看 TypeScript 接下来会不会提供这套方案需要的东西吧。
