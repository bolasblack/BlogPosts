---
title: 我写了一个 MCP 转 Skill 的 Skill
tags: [Claude Code, Open Code, Codex, MCP, AI]
---

最近写了个 skill，功能是把 MCP server 转成 Claude Code skill。听起来有点绕，但其实挺实用的。

## 为什么要转？

MCP 本身没什么问题，很好，至少给了一个统一的，可发现的交互协议，而且这个协议的接受度还这么广，太难得了，Restful 虽然很多服务商都支持，但是这个东西的约束太少，太灵活，实话说很多时候“可发现”这个事情很多大厂（尤其是不少中国的大厂）做的简直和屎一样；而 GraphQL, gRPC 之类的，接受度又都很有限，非常碎片化。有 MCP 至少这方面的问题解决很多。

但是它还是有点问题。

### 上下文窗口吃的太严重了

MCP 的工作方式是把所有 tool 的定义一股脑丢进上下文里。如果你接了好几个 MCP server，每个 server 又有一堆 tools，那上下文窗口里光是 tool 定义就占了一大坨，举个例子，我刚看了一下，GitHub mcp 占了我 10k token (5%) 的上下文。

Claude Code skill 有个叫 progressive disclosure（渐进式披露）的机制：AI 先看到一个简短的描述，只有在需要用到这个 skill 的时候才会加载完整的内容。这样上下文窗口就不会被一堆可能根本用不到的 tool 定义给塞满了。

虽然目前这个 skill 没办法把有些需要 OAuth 的 mcp 服务给变成 skill （严格来说，要做还是能做的，就是麻烦了点，我正在考虑后面有空的话就加上……），但是至少能少占一点是一点……

### AI 调用 tool 效率太低了

这是让我最想做这个转换的原因。

传统的方式是让 AI 一个一个地调用 tool：先调用 A，拿到结果，再调用 B，再调用 C……每次调用都要走一遍"AI 决策 → 发起调用 → 拿到结果 → AI 再决策"的循环。

问题是这个过程很不可靠。AI 可能会漏掉某个步骤，可能会在中间突然疯了，而且每次调用的结果都要塞进上下文里，token 消耗蹭蹭往上涨，甚至可能会因为返回的内容过长而失败，然后 AI 还得想办法多次分页请求内容。

更好的方式是让 AI 写一段脚本，一次性完成所有调用（或者对响应结果进行预处理）。代码的控制流比 AI 的"思考链"要可靠得多，而且 Anthropic 官方也在研究这个方向——他们发了一篇叫 [Code execution with MCP](https://www.anthropic.com/engineering/code-execution-with-mcp) 的文章，数据显示 token 消耗从 15 万降到 2 千，减少了 98.7%。

我的这个 skill 生成的结果里就包含了一个 `api.mjs`，AI 可以直接 import 进来写脚本用：

```javascript
import { callTool } from '/<mcp-skill-path>/scripts/api.mjs';

// 并行搜索多个仓库的 issues
const repos = ['facebook/react', 'vuejs/vue', 'sveltejs/svelte'];
const results = await Promise.all(
  repos.map(repo => callTool('search_issues', {
    repo,
    query: 'memory leak'
  }))
);

// 汇总结果，只返回需要的字段
const summary = results.flatMap((r, i) =>
  r.content[0].text.items?.slice(0, 3).map(issue => ({
    repo: repos[i],
    title: issue.title,
    url: issue.html_url
  })) ?? []
);

console.log(JSON.stringify(summary, null, 2));
```

如果用传统的 tool calling 方式，AI 得调用 3 次 `search_issues`，每次调用的完整结果都要塞进上下文，然后 AI 再"思考"一遍怎么汇总。用脚本的话，一次执行完，返回给 AI 的就是处理好的精简结果。

### 版本锁定

MCP server 通常是 `npx @some/mcp-server` 这样运行的，每次都拉最新版。这意味着 server 的维护者随时可以推一个恶意更新，而你完全不知道。

转成 skill 的时候可以锁定版本号，至少能防一手供应链攻击。

## 简单示例

用起来大概是这样：

```
> /mcp-skill-generator 帮我转一下 @anthropic/mcp-server-filesystem
> /mcp-skill-generator 帮我转一下 https://docs.devin.ai/work-with-devin/deepwiki-mcp
```

然后它会问你要存到哪里（项目目录还是个人目录），接着自动提取 schema、生成文件。最后得到的目录结构是：

```
mcp-filesystem/
├── SKILL.md
├── config.toml
├── tools/
│   ├── read_file.md
│   ├── write_file.md
│   └── ...
└── scripts/
    ├── mcp-caller.mjs
    ├── ...
    └── api.mjs          # AI 可以用这个写脚本
```

## 最后

代码在 [我的 claude-skills 仓库](https://github.com/user/claude-skills) 里，有兴趣的可以看看。

说实话这个 skill 本身就是用 Claude Code 写的，写的过程中我深刻体会到了"让 AI 写代码"比"让 AI 调用 tool"靠谱多少——至少代码跑不通的时候能看到报错信息，而不是 AI 默默地走偏了你还不知道。
