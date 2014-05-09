其实很简单，因为 Shell 好用~

在 Terminal 打开 Vim ，然后 <kbd>Ctrl-z</kbd> 。

“biu!”，不见了。

再开个 node ，然后 <kbd>Ctrl-z</kbd> 。

“biu!”，又不见了。

然后输入 `jobs` ，回车：

```
[1]  - suspended  vim
[2]  + suspended  node
```

然后输入 `fg %2` ，回车，“biu!”，Vim 又出来了。

退出 Vim ，然后输入 `fg` ，“biu!”，node 回来了。

什么？你说 Terminal 下复制麻烦？

```vimscript
:'<,'>w !pbcopy<CR>
```
