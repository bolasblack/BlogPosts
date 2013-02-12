---
layout: post
title: "基本完成了 todoist 的 Cli 客户端"
description: ""
category: 代码
tags: [python, todoist, GTD, cli]
---

这个已经写了很久了，之前也自称写完过，不过当时写完后没怎么用，主要问题是发现想要添加一个新待办事项的时候还需要开一个终端依旧有些蛋疼

于是项目就此搁置

最近换了 Mac ，工作有些忙，暂时没时间折腾 Arch Linux ，所以大部分事情都在 Mac 上搞定，用了 Things 后就开始想着是不是写个 todoist 的快速添加待办事项的小程序出来

天可怜见，差点就去学 Objective-C 了呀！

不过后来发现 Mac 上有个叫做 DTerm 的应用，允许你随时唤出一个执行并显示执行结果的窗口

于是重写 todoistCli 的心思又活络起来了

几天下来，除了基本的逻辑和语言没有改变以外，其他的差不多都重写过了

用起来比之前爽多了

然后也发现对 Python 的恶感竟然又少了不少，话说回来我还真是个没立场的家伙啊

最近一直想着想搞搞 Clojure-py 呢，一个 Python 的 Clojure 实现，目前还是实验室级别的东西，不过相对于运行在 JVM 的 Clojure 来说，我想大概 Clojure-py 更亲切一点吧……

话说回来 todoistCli 托管在我的 Github 账户上，如果感兴趣的话可以试试 [https://github.com/bolasblack/todoistCli](https://github.com/bolasblack/todoistCli)
