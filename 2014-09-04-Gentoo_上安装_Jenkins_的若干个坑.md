---
title: "Gentoo 上安装 Jenkins 的若干个坑"
category: ["gentoo"]
---

嗯，在 Gentoo 上装了一下 Jenkins ，记录一下遇到的坑吧。

## Overlay

~~Gentoo 官方的 portage 和 gentoo-zh 里都是没有 Jenkins 的，所以得自己去找一下 overlay 。我在 [http://gpo.zugaina.org/](http://gpo.zugaina.org/) 这个网站找到了相应的[文件](http://gpo.zugaina.org/) ，然后由于下文中会提到的一些事情，所以对文件做了一些改动，push 到了自己的仓库 [bolasblack/overlay](https://github.com/bolasblack/overlay) 。版本是 1.577 ，官方最新是 1.578 ，不过看了一下 changelog 似乎没啥太大的变化，就懒得写了。~~

Jenkins 现在已经进入到了官方的 portage 里了，可以使用 `emerge -av jenkins-bin` 来安装。

## 部署上的一些零碎

Gentoo 的 [Init Scripts](http://www.gentoo.org/doc/en/handbook/handbook-x86.xml?part=2&chap=4) 配置文件一般都在 `/etc/conf.d/servicename` 里面， Jenkins 也不例外，改监听的端口什么的都去那边改。

虽然过了一年半了， Nginx 的配置文件还是可以参考 Jenkins 的 Wiki 的：[https://wiki.jenkins-ci.org/display/JENKINS/Running+Hudson+behind+Nginx](https://wiki.jenkins-ci.org/display/JENKINS/Running+Hudson+behind+Nginx)。

这里有一份简化了感觉好像还能用的配置文件，就是不知道什么时候会不会遇到坑 [https://gist.github.com/bolasblack/578d9f086a93ab69bf3b](https://gist.github.com/bolasblack/578d9f086a93ab69bf3b#file-jenkins-conf-L5) 。

## No valid crumb was included in the request

如果在点击某些选项后页面上什么都没出来，JS 控制台提示某个 POST 请求被响应 403 ，Status Text 是标题的话，那就是你在全局安全设置里开了“防止跨站点请求伪造”，然后被 Nginx/Apache 坑了。

Apache 我只知道要禁用 `ignore_invalid_headers` 。Nginx 的话要在 `server` 块里添加一行 `ignore_invalid_headers off;` 来防止 Nginx 过滤掉 Jenkins 为了防止 CSRF 发出的 `.crumb` 或者 `Crumb` 请求头。

示例代码：[https://gist.github.com/bolasblack/578d9f086a93ab69bf3b#file-jenkins-conf-L5](https://gist.github.com/bolasblack/578d9f086a93ab69bf3b#file-jenkins-conf-L5)

参考链接：

* http://www.myexception.cn/ruby-rails/1617396.html
* https://issues.jenkins-ci.org/browse/JENKINS-12875

## Git commit 乱码

具体的处理方法是在系统变量 `JAVA_TOOL_OPTIONS` 里设置默认编码，我们需要的就是 `-Dfile.encoding=UTF-8` 。

参考链接：

* http://bbbush.livejournal.com/392149.html
* http://www.tuicool.com/articles/f6J3I3

## SSH Username with private key

这个 key Jenkins 是不会自动创建的，需要你手动执行 `sudo -u jenkins ssh-keygen -t rsa` 来新建。

由于官方的 conf.d 里把 `jenkins` 的家目录默认设置在了 `/var/lib/jenkins` ，所以找 ssh 的 pub key 的时候也要去那个目录下找。

## It appears that your reverse proxy set up is broken

我也不知道怎么解决，反正用了我自己那个 nginx 配置文件以后就解决了

可能有帮助的链接（反正我没得到帮助）：

* http://www.phase2technology.com/blog/running-jenkins-behind-nginx/

## 设置“启用安全”后所有页面都需要 Basic HTTP Authorization

尝试一下在 `/var/lib/jenkins/home/config.xml` 文件保持 `<useSecurity>` 的情况下删除 `<authorizationStrategy>` 和 `<securityRealm>` ，然后重新激活“启用安全”。

参考链接：

* http://stackoverflow.com/questions/29530558/how-to-disable-basic-http-auth-of-jenkins

## 如何设置 Jenkins 监听的地址

修改 `/etc/conf.d/jenkins` 文件的 `JENKINS_ARGS="--httpListenAddress=127.0.0.1"` 来把监听地址设置为 `127.0.0.1` 。

也可以通过命令 `java -jar  /opt/jenkins/jenkins.war --help` 来获取其他配置参数。
