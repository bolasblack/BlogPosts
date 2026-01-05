---
title: I Moved My Config Sharing Repo to LLM-oriented Mode
tags: [LLM, tooling, ESLint, npm]
---

## What is "LLM-oriented Config Sharing"?

Simply put, config sharing projects provide [llms.txt](https://llmstxt.org/), and consumers use it to let LLMs download/update project config files to keep them up to date—instead of distributing configs through npm registry.

## So Why Not Use npm Anymore?

Traditional ways of sharing tool configurations—whether through `extends` or presets—all face a fundamental contradiction: **the trade-off between convenience and flexibility**.

### The Pros of Inheritance Mode

When you use something like `extends: "@company/eslint-config"`, the benefits are obvious:

- **Easy upgrades**: Just `npm update`, and all projects using this config get the updates
- **Consistency guaranteed**: All projects in the team use the same rules
- **Centralized maintenance**: Improvements and bug fixes only need to happen in one place

### But Reality is Harsh

Problems arise when you need to do anything "non-standard":

1. **Custom requirements**: Your project has special needs and requires overriding certain rules
2. **Dependency version conflicts**: You need to upgrade ESLint, but the upstream config package hasn't caught up yet (e.g., ESLint moved to flat config mode, and you need the latest version, but I'm lazy and haven't updated yet 🤪)
3. **Rule disagreements**: Upstream updated a rule, but it doesn't fit your project (the problem is you might not know at first, and only realize "damn, I got burned" when things break)

At this point, you have two choices:

**Option A: Patch on top of inheritance**

```javascript
// eslint.config.js
export default [
  ...baseConfig,
  {
    rules: {
      // Override one rule
      "some-rule": "off",
      // Override another
      "another-rule": ["error", { different: "options" }],
    },
  },
  // Still need to handle plugin version conflicts...
];
```

As patches pile up, your "inheritance" becomes layer upon layer of overrides, ending up harder to maintain than writing from scratch.

ESLint is actually not too bad. Tools like Prettier and lint-staged that don't have extends/merge mechanisms are even more troublesome. I previously wrote a super complicated lint-staged config (see [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.config.js) and [`lint-staged.helpers.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.helpers.js)). It looks decent (well, at least I think so), but it's actually super hard to use and makes a very simple thing extremely complex.

**Option B: Eject**

Many scaffold tools provide an `eject` command that copies all config files into your project, giving you full control.

But after ejecting:

- You lose the ability to auto-upgrade; you have to update yourself and can't benefit from upstream's tested plugin compatibility
- When upstream has important updates, you need to manually diff and merge

**This is the dilemma**: In inheritance mode you're constrained by upstream; after ejecting you bear the maintenance cost.

## LLM is a Game Changer

Now we have LLMs. Times have changed.

### The New Workflow

1. **Get latest configs**: LLM fetches the latest recommended configs directly from llms.md/llms.txt
2. **Smart merging**: LLM understands your project context and merges new configs into existing ones
3. **Preserve customizations**: Your custom modifications are recognized and preserved
4. **Update on demand**: When you need to upgrade, let LLM handle the merge conflicts

### Why is This Better Than Traditional Approaches?

**All configs live in your codebase**

No hidden config files deep in `node_modules`, no confusion about "where did this rule come from". Every line of config is clearly visible in your project.

**Upgrades become controllable**

Upgrading is no longer an "all or nothing" decision. LLM can help you:

- See what changed upstream
- Understand the impact of each change
- Selectively apply updates
- Automatically handle merge conflicts

**Customization is a first-class citizen**

Your customizations are no longer "patches"—they're part of the config. LLM understands and respects these customizations when updating.

**Dependency version freedom**

You can upgrade any dependency anytime without waiting for upstream config packages to update. Run into issues? LLM can help adjust the config (and configs are easier for them to read now too).

**Upstream config files become simpler**

Let's look at my lint-staged config files again.

Previous version:

- [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.config.js)
- Helper functions I had to write so downstream could reuse and customize the lint-staged config: [`lint-staged.helpers.js`](https://github.com/bolasblack/js-metarepo/blob/0270b291651bcbc75a3ecd81d391f4ad836a814e/packages/toolconfs/lint-staged.helpers.js)

Current version:

- [`lint-staged.config.js`](https://github.com/bolasblack/js-metarepo/blob/25d4495adcba67bda9daf65b04a204acc01cb795/packages/toolconfs/lint-staged.config.js)
  Actually, this is so simple now, let me just inline it:
  ```javascript
  module.exports = {
    "*.{ts,tsx,js,jsx}": ["prettier --write", "eslint"],
    "*.{css,scss,sass,less,md,mdx}": ["prettier --write"],
  };
  ```
- `lint-staged.helpers.js` no longer exists—because it's no longer needed

## You've Said a Lot, But Doesn't This Still Look Like a Step Backward?

Yes and no.

- **Yes**: Config files do exist independently in each project, just like the old days
- **No**: Because the maintenance cost has fundamentally changed:
  - **Before**: Manually read CHANGELOG or diff against upstream config -> Figure out what changed -> Manually modify -> Test and verify
  - **Now**: Tell LLM "upgrade to the latest recommended config" -> Review LLM's changes -> Done

The cognitive burden of maintenance shifts from "I need to understand all these configs" to "I need to review LLM's suggestions". This is what's different from the early days.

## Summary (AI version, couldn't write this myself)

Traditional config sharing forces a choice between "one-size-fits-all convenience" and "full autonomy with complexity". The LLM-oriented approach lets us have both:

- Maintain full ownership and transparency of configs
- While enjoying intelligent assistance for upgrades and maintenance

This isn't a step backward in technology—it's redefining best practices with new tools.
