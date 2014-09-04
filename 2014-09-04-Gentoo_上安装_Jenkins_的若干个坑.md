---
title: "Gentoo 上安装 Jenkins 的若干个坑"
category: ["gentoo"]
---

嗯，在 Gentoo 上装了一下 Jenkins ，记录一下遇到的坑吧。

## Overlay

Gentoo 官方的 portage 和 gentoo-zh 里都是没有 Jenkins 的，所以得自己去找一下 overlay 。我在 [http://gpo.zugaina.org/](http://gpo.zugaina.org/) 这个网站找到了相应的[文件](http://gpo.zugaina.org/) ，然后由于下文中会提到的一些事情，所以对文件做了一些改动，push 到了自己的仓库 [bolasblack/overlay](https://github.com/bolasblack/overlay) 。版本是 1.577 ，官方最新是 1.578 ，不过看了一下 changelog 似乎没啥太大的变化，就懒得写了。

## 部署上的一些零碎

Gentoo 的 [Init Scripts](http://www.gentoo.org/doc/en/handbook/handbook-x86.xml?part=2&chap=4) 配置文件一般都在 `/etc/conf.d/servicename` 里面， Jenkins 也不例外，改监听的端口什么的都去那边改。

虽然过了一年半了， Nginx 的配置文件还是可以参考 Jenkins 的 Wiki 的：[https://wiki.jenkins-ci.org/display/JENKINS/Running+Hudson+behind+Nginx](https://wiki.jenkins-ci.org/display/JENKINS/Running+Hudson+behind+Nginx)。

## No valid crumb was included in the request

如果在点击某些选项后页面上什么都没出来，JS 控制台提示某个 POST 请求被响应 403 ，Status Text 是标题的话，那就是你在全局安全设置里开了“防止跨站点请求伪造”，然后被 Nginx/Apache 坑了。

Apache 我只知道要禁用 `ignore_invalid_headers` 。Nginx 的话要在 `server` 块里添加一行 `ignore_invalid_headers off;` 来防止 Nginx 过滤掉 Jenkins 为了防止 CSRF 发出的 `.crumb` 或者 `Crumb` 请求头。

示例代码：[https://gist.github.com/bolasblack/163dde77075d002a00d2#file-jenkins-conf-L9](https://gist.github.com/bolasblack/163dde77075d002a00d2#file-jenkins-conf-L9)

参考链接：

* http://www.myexception.cn/ruby-rails/1617396.html
* https://issues.jenkins-ci.org/browse/JENKINS-12875

## Git commit 乱码

这个问题其实是被 Java 的默认运行编码坑的，我已经在 overlay 的 `files/jenkins-bin.confd` 里设置好了。

具体的处理方法是在系统变量 `JAVA_TOOL_OPTIONS` 里设置默认编码，我们需要的就是 `-Dfile.encoding=UTF-8` 。

参考链接：

* http://bbbush.livejournal.com/392149.html
* http://www.tuicool.com/articles/f6J3I3

## SSH Username with private key

这个 key Jenkins 是不会自动创建的，需要你手动执行 `sudo -u jenkins ssh-keygen -t rsa` 来新建。

由于我的那个 overlay 里把 `jenkins` 的家目录默认设置在了 `/var/lib/jenkins` ，所以找 ssh 的 pub key 的时候也要去那个目录下找。
