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
    data-ng-class="{'button-positive': checkStartDayOffset($index)}"
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

