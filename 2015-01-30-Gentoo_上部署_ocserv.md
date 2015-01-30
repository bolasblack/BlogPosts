---
title: "Gentoo 上部署 ocserv"
category: ["科学上网"]
tags: ["gentoo", "ocserv", "iOS"]
---

终于忍受不了 iOS 上翻墙的蛋疼劲了，VPN 一锁屏就断。和废掉完全没区别。所以，查查资料，我们来装 ocserv 吧。

## 安装 GnuTLS

ocserv 最让人头疼的事情在于他依赖 GnuTLS 3.x ，Gentoo 的源里目前它还是 2.x 的状态，所以没办法，只能自己编译。

```bash
cd ~
wget ftp://ftp.gnutls.org/gcrypt/gnutls/v3.3/gnutls-3.3.12.tar.xz
tar xvf gnutls-3.3.12.tar.xz
cd gnutls-3.3.12
./configure --enable-shared
make -j2 && sudo make install
```

编译完成后使用 `sudo gnutls-cli -v` 看看打印出来的版本号：

```bash
gnutls-cli 3.3.12
Copyright (C) 2000-2015 Free Software Foundation, and others, all rights reserved.
This is free software. It is licensed for use, modification and
redistribution under the terms of the GNU General Public License,
version 3 or later <http://gnu.org/licenses/gpl.html>
```

如果提示：

    gnutls-cli: error while loading shared libraries: libgnutls-dane.so.0: cannot open shared object file: No such file or directory

类似的错误，就给 `/root/.bashrc` 加一行：

    export LD_LIBRARY_PATH=/lib:/usr/lib:/usr/local/lib

至此，GnuTLS 就装的差不多了。

## 安装 ocserv

这个教程一抓一大吧，而且问题似乎都不大。

```bash
cd ~
wget ftp://ftp.infradead.org/pub/ocserv/ocserv-0.9.0.1.tar.xz
tar xvf ocserv-0.9.0.1.tar.xz
cd ocserv-0.9.0
PKG_CONFIG_PATH=~/gnutls-3.3.12/lib/ ./configure
make -j2 && sudo make install
```

这样子就编译安装完成了。接下来配置 ocserv 。

## 配置 ocserv

先建证书：

```bash
sudo mkdir -p /etc/ocserv/certificates
cd /etc/ocserv/certificates
```

创建 `server.tmpl` 模板，这里的 `cn` 必须对应最终提供服务的 hostname 或 IP ：

```
cn = "Your hostname or IP"
organization = "Your fancy name"
expiration_days = 3650
signing_key
encryption_key
tls_www_server
```

然后来建证书：

```bash
sudo certtool --generate-privkey --outfile server-key.pem
sudo certtool --generate-certificate --load-privkey server-key.pem --load-ca-certificate ca-cert.pem --load-ca-privkey ca-key.pem --template server.tmpl --outfile server-cert.pem
```

然后来改配置：

```bash
sudo cp ~/ocserv-0.9.0/doc/sample.config /etc/ocserv/ocserv.conf
sudo vim /etc/ocserv/ocserv.conf
```

文件内容：

```conf
# 登陆方式，目前先用密码登录
auth = "plain[/etc/ocserv/ocpasswd]"
# 允许同时连接的客户端数量
max-clients = 4
# 限制同一客户端的并行登陆数量
max-same-clients = 2
# 服务监听的IP（服务器 IP ，可不设置）
listen-host = 1.2.3.4
# 服务监听的 TCP/UDP 端口，这个自己看着办，客户端以 IP:PORT 的格式来连接
# 如果改了的话两个端口最好不同，我在使用时发现如果端口相同的话，会导致请求被阻塞的情况
tcp-port = 9000
udp-port = 9001
# 自动优化 VPN 的网络性能
try-mtu-discovery = true
# 服务器证书与密钥
server-cert = /etc/ocserv/certificates/server-cert.pem
server-key =  /etc/ocserv/certificates/server-key.pem
# 客户端连上 VPN 后使用的 DNS
dns = 8.8.8.8
# 注释掉所有的 route ，让服务器成为 gateway
#route = 192.168.1.0/255.255.255.0
# 启用 cisco 客户端兼容性支持
cisco-client-compat = true
```

最后生成帐号密码文件：

    sudo ocpasswd -c /etc/ocserv/ocpasswd username

## 其他配置

以 [Linode 的配置](https://www.linode.com/docs/security/securing-your-server#creating-a-firewall) 为例，新建或修改 `/etc/iptables.firewall.rules` 文件：

```iptables
# 如果是新建文件才需要这行
*filter

# 这里的端口填 ocserv 配置里的 tcp-port 和 udp-port
-A INPUT -p tcp -m state --state NEW --dport 9000 -j ACCEPT
-A INPUT -p udp -m state --state NEW --dport 9001 -j ACCEPT

# 注释这行，允许转发
# -A FORWARD -j DROP

# 如果是新建文件才需要这行
COMMIT



#启用NAT
*nat
-A POSTROUTING -j MASQUERADE
COMMIT
```

完成之后导入新配置并检查配置正确：

```bash
sudo iptables-restore < /etc/iptables.firewall.rules
sudo iptables -L
sudo iptables -t nat -L
```

接着打开 IPv4 的流量转发：

    sudo vim /etc/sysctl.conf

启用此项：

    net.ipv4.ip_forward=1

刷新配置：

    sudo sysctl -p /etc/sysctl.conf

## 测试一下

    sudo ocserv -f -d 1

如果运行成功，就下载一个 AnyConnect 客户端来测试一下。

如果证书是自己签发的，那么 iOS 客户端在连接前先到 `Settings` 标签页关闭 `Block Untrusted Servers` 。

## 余话

关于开机自动启动 ocserv ，开机自动载入 iptables 配置，客户端证书自动连接，这些东西我就不在这里写了，可以参考下面的参考文章。

最后，祝 GFW 早日被终结。

参考文章：

* [折腾笔记：架设OpenConnect Server给iPhone提供更顺畅的网络生活](http://bitinn.net/11084/)
* [Gentoo编译安装Ocserv上Cisco AnyConnect VPN](http://blog.ihipop.info/2014/07/4782.html)
* [HOW TO INSTALL GNUTLS 3.1.23 FROM SOURCE IN UBUNTU 14.04](http://www.bauer-power.net/2014/06/how-to-install-gnutls-3123-from-source.html)
