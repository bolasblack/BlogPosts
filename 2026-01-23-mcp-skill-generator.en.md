---
title: I Made a Skill That Converts MCP to Skill
tags: [Claude Code, Open Code, Codex, MCP, AI]
---

I recently made a skill that converts MCP servers into Claude Code skills. Sounds a bit meta, but it's actually quite useful.

## Why Convert?

MCP itself is great. It provides a unified, discoverable interaction protocol, and the adoption rate is impressive. RESTful APIs are supported by many providers, sure, but they're too loosely constrained—honestly, "discoverability" is something many big companies (especially quite a few Chinese tech giants) do terribly. GraphQL, gRPC and the like have limited adoption and are quite fragmented. MCP solves a lot of these problems.

But it still has some issues.

### Context Window Consumption Is Too Heavy

MCP works by dumping all tool definitions into the context at once. If you've connected several MCP servers, each with a bunch of tools, the tool definitions alone take up a huge chunk of your context window. For example, I just checked—the GitHub MCP takes up 10k tokens (5%) of my context.

Claude Code skills have a mechanism called progressive disclosure: the AI first sees a brief description, and only loads the full content when it actually needs to use that skill. This way the context window doesn't get stuffed with tool definitions that might never be used.

Although this skill currently can't convert MCP services that require OAuth (technically it's doable, just a bit more work—I'm considering adding it when I have time...), at least every bit of context saved counts...

### AI Tool Calling Is Too Inefficient

This is the main reason I wanted to build this converter.

The traditional approach has the AI call tools one by one: call A, get the result, call B, call C... Each call goes through an "AI decides → make call → get result → AI decides again" loop.

The problem is this process is unreliable. The AI might skip a step, might suddenly go off the rails mid-way, and each call result has to be stuffed into the context, making token consumption skyrocket. It might even fail because the response is too long, forcing the AI to figure out how to paginate through the content.

A better approach is to have the AI write a script that completes all calls at once (or preprocesses the responses). Code control flow is much more reliable than AI "chain of thought", and Anthropic is officially researching this direction—they published an article called [Code execution with MCP](https://www.anthropic.com/engineering/code-execution-with-mcp), showing token consumption dropping from 150k to 2k, a 98.7% reduction.

The skill I made generates an `api.mjs` that the AI can directly import and use for scripting:

```javascript
import { callTool } from '/<mcp-skill-path>/scripts/api.mjs';

// Search issues across multiple repos in parallel
const repos = ['facebook/react', 'vuejs/vue', 'sveltejs/svelte'];
const results = await Promise.all(
  repos.map(repo => callTool('search_issues', {
    repo,
    query: 'memory leak'
  }))
);

// Summarize results, return only needed fields
const summary = results.flatMap((r, i) =>
  r.content[0].text.items?.slice(0, 3).map(issue => ({
    repo: repos[i],
    title: issue.title,
    url: issue.html_url
  })) ?? []
);

console.log(JSON.stringify(summary, null, 2));
```

With traditional tool calling, the AI would need to call `search_issues` 3 times, with each call's full result stuffed into context, then the AI "thinks" again about how to summarize. With a script, it executes once and returns the processed, concise result to the AI.

### Version Locking

MCP servers are typically run like `npx @some/mcp-server`, pulling the latest version every time. This means the server maintainer could push a malicious update at any moment, and you'd have no idea.

When converting to a skill, you can lock the version number—at least providing some defense against supply chain attacks.

## Quick Example

Usage looks something like this:

```
> /mcp-skill-generator convert @anthropic/mcp-server-filesystem
> /mcp-skill-generator convert https://docs.devin.ai/work-with-devin/deepwiki-mcp
```

It will ask where you want to save it (project directory or personal directory), then automatically extract the schema and generate files. The resulting directory structure is:

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
    └── api.mjs          # AI can use this for scripting
```

## Finally

The code is in [my claude-skills repo](https://github.com/user/claude-skills) if you're interested.

To be honest, this skill itself was written using Claude Code. During the process, I deeply experienced how much more reliable "having AI write code" is compared to "having AI call tools"—at least when code doesn't run, you can see error messages, instead of the AI quietly going off track without you knowing.
