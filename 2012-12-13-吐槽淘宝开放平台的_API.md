---
layout: post
title: "吐槽淘宝开放平台的API"
description: ""
category: 吐槽
tags: [淘宝, 开放平台, api, 吐槽]
---

生平不知道第几次见到如此坑爹的 API ，全部都是国内公司出品。

* [人人 http://wiki.dev.renren.com/wiki/API](http://wiki.dev.renren.com/wiki/API)
* [淘宝 http://open.taobao.com/doc/api_list.htm?id=102](http://open.taobao.com/doc/api_list.htm?id=102)

那种令人作呕的接口调用方式简直就是丧心病狂。

更加令人发指的是，淘宝文档中提供的 [Demo](http://api.taobao.com/apitools/apiTools.htm?catId=38&apiName=taobao.taobaoke.widget.items.convert) 竟然无法使用，不论是 Python 的还是 Java 的。

如果你不允许使用 SDK ，麻烦你说明好吗亲？！！再加上灭绝人性的 sign 的算法，如果最后的 sign 需要将所有的字符转化为大写字母，麻烦你在文档中说明而不是只在[ PHP 代码](http://open.taobao.com/doc/detail.htm?id=988)中写着好吗，亲！！！

更加不用提那个莫名其妙的 `partner_id` 参数，如果不看别人写的文章和 JSSDK 发起的请求，我是绝对不会知道还存在这么个东西。

请问这个是从哪里出现的？为什么前无古人后无来者？

再加上淘宝 JSSDK 的 Demo 页面那种完全无法令人理解的界面和说明，我有理由相信这个一定是临时工干的对不对？

相关资料：

* [淘宝API计算SIGN参数（error code:25 Invalid Signature）](http://my.oschina.net/ryanhoo/blog/86876)
* [Github/laoqiu/sinaapp taobaoapi.py](https://github.com/laoqiu/sinaapp/blob/master/webapp/scripts/taobaoapi.py)
* [我针对 taobao.taobaoke.widget.items.convert 这类 widget 接口，根据 taobaoapi.py 修改而来的文件](https://github.com/bolasblack/PythonLib/blob/master/taobaoapi.py)
