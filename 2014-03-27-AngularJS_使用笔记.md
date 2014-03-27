---
title: "AngularJS 使用笔记"
category: ["JavaScript"]
---

编写一个 `appEval` 指令（类似早期版本的 `ngInit` ）能够很好的实现“表现与逻辑分离”的思想，比如要渲染出三个内容规则的按钮，就可以这样：

```jade
p.input-object.text-right(data-app-eval="view_avaliableSendDays = ['今天', '明天', '后天']")
  button.button(
    type="button"
    data-ng-repeat=“view_day in view_avaliableSendDays"
    data-ng-class="{'button-positive': startDayOffset = $index}"
    data-ng-click="startDayOffset = $index"
  ) {{view_day}}
```

很显然， `view_avaliableSendDays`  是不适合在控制器里进行定义的，在模板里定义则显得比较合理

但是在模板里定义也会引起一些问题，比如在没有意识到的情况下控制器里的变量污染了视图里的变量名，或者正好相反

这种时候比较合适的做法就是给视图专有的变量名加上特殊的前缀，比如 `view_` 这样子

附 `appEval` 的代码：

```coffeescript
module.directive('appEval', [
  ->
    link: ($scope, $elem, attrs) ->
      $scope.$eval attrs.appEval
])
```

* * *

如果是在使用 `Jade` 的话，那么本着 “控制器里的嵌套 Scope 越少越好” 的原则，推荐使用迭代器来替换 `ngRepeat` ：

```jade
p.input-object.text-right
  each day, index in ['今天', '明天', '后天']
    button.button(
      type="button"
      data-ng-class="{'button-positive': startDayOffset === #{index}}"
      data-ng-click="startDayOffset = #{index}"
    )= day
```

不过即使如此， `appEval` 依旧是有用武之地的。比如说用来初始化某个控制器的变量：

```jade
p.input-object.text-right(data-app-eval="startDayOffset = 0")
  each day, index in ['今天', '明天', '后天']
    button.button(
      type="button"
      data-ng-class="{'button-positive': startDayOffset === #{index}}"
      data-ng-click="startDayOffset = #{index}"
    )= day
```

因为 `offset` 的值是由视图控制的，如果在控制器里设置了某个变量的默认值，那么当视图中的候选值调整后，控制器的代码也需要进行相应的调整。

如果把初始化默认值的代码放在视图中，维护起来会更加的便捷，而且不容易漏改。

