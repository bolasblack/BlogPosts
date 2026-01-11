---
title: "bitcoin-wallet-connector: 可能是目前最好用的比特币钱包适配器"
tags: [Bitcoin, Wallet, Connector, Adapter, React, TypeScript]
---

标题党一下 🤪

[上一篇文章](./2026-01-03-bitcoin-wallet-ecosystem-is-a-mess.md)我吐槽了比特币钱包生态有多混乱：WBIPs 标准没人实现、sats-connect 的兼容性、钱包各有各的 API...

这篇文章介绍下我写的库：[bitcoin-wallet-connector](https://github.com/bolasblack/bitcoin-wallet-connector)。

先来试试 **[在线 Demo](https://bitcoin-wallet-connector.netlify.app/iframe.html?id=components-bitcoinconnectionprovider--default&viewMode=story)** ，或者 **[Storybook](https://bitcoin-wallet-connector.netlify.app/)**

## 我来讲讲这个库关注什么

### 把那些莫名其妙的差异抹平

`bitcoin-wallet-connector` 提供了一套统一的 API，让你用同样的代码接入所有支持的钱包：

```typescript
import {
  BitcoinWalletConnector,
  UnisatWalletAdapterFactory,
  XverseWalletAdapterFactory,
  LeatherWalletAdapterFactory,
} from "bitcoin-wallet-connector";

// 注册你想支持的钱包
const connector = new BitcoinWalletConnector([
  UnisatWalletAdapterFactory(),
  XverseWalletAdapterFactory(),
  LeatherWalletAdapterFactory(),
]);

// 订阅可用钱包变化
// 注意：钱包扩展注入 API 的时机是不确定的（有些在 DOMContentLoaded，
// 有些在 load 事件后），所以推荐使用 subscribe 而不是 get
connector.subscribeAvailableAdapters((availableAdapters) => {
  console.log(
    "可用钱包:",
    availableAdapters.map(([id]) => id)
  );
  // => ['unisat', 'xverse', ...]
});

// 连接钱包 - 所有钱包用同一套 API
const [adapterId, adapter] = availableAdapters[0];
await connector.connect(adapterId, adapter);

// 获取地址、签名、发送交易 - 统一接口
const addresses = await adapter.getAddresses();
const result = await adapter.signMessage(
  addresses[0].address,
  "Hello Bitcoin!"
);
```

就这样，你只需要写一遍代码，就能支持所有钱包。

目前支持的钱包：

| 钱包                                       | Adapter                         | 额外依赖          |
| ------------------------------------------ | ------------------------------- | ----------------- |
| [UniSat](https://unisat.io/)               | `UnisatWalletAdapterFactory`    | -                 |
| [Xverse](https://www.xverse.app/)          | `XverseWalletAdapterFactory`    | `sats-connect`    |
| [OKX](https://www.okx.com/web3)            | `OkxWalletAdapterFactory`       | -                 |
| [Leather](https://leather.io/)             | `LeatherWalletAdapterFactory`   | `@leather.io/rpc` |
| [Bitget](https://web3.bitget.com/)         | `BitgetWalletAdapterFactory`    | -                 |
| [Magic Eden](https://wallet.magiceden.io/) | `MagicEdenWalletAdapterFactory` | `sats-connect`    |

所有 adapter 都实现了相同的接口：

```typescript
interface WalletAdapter {
  // 连接/断开
  connect(): Promise<void>;
  disconnect(): Promise<void>;

  // 获取地址
  getAddresses(): Promise<WalletAdapterAddress[]>;

  // 消息签名
  signMessage(address: string, message: string): Promise<SignMessageResult>;

  // 发送 BTC
  sendBitcoin(
    fromAddress: string,
    receiverAddress: string,
    satoshiAmount: bigint
  ): Promise<{ txid: string }>;

  // PSBT 签名
  signAndFinalizePsbt(
    psbtHex: string,
    signIndices: [address: string, signIndex: number][]
  ): Promise<{ signedPsbtHex: string }>;

  // 监听地址变化
  onAddressesChanged(callback): { unsubscribe: () => void };
}
```

无论用户选择哪个钱包，你的业务代码都是一样的。

#### 关于 sendRunes/sendInscriptions/sendBRC20

目前这个库只支持 `signMessage`, `sendBitcoin`, 和 `signPsbt`，暂时不支持 `sendRunes`, `sendInscriptions`, `sendBRC20`。

因为这些都涉及到更复杂的依赖（比如需要一个 Ordinals Indexer, 还需要一个 BRC20 Indexer 等等）。这些会让一个 `Connector` 变的过于复杂。

在我看来，这应该是一个 `Transaction Builder` 的职责，由 `Transaction Builder` 负责构建交易，然后将交易交给 `Connector` 来签名和发送。

### 安全性很重要

在设计这个库的时候，我把**依赖安全**放在了很高的优先级。原因很简单：钱包库直接接触用户的资产，任何安全漏洞都可能造成真金白银的损失。

#### Peer Dependencies

我把一些重要的依赖声明为 peer dependencies，而不是打包进库里：

```bash
pnpm add bitcoin-wallet-connector @scure/base @scure/btc-signer
```

这意味着：

- 你可以直接控制这些依赖的版本
- 如果某个依赖爆出安全漏洞，你可以**立即升级**，不用等这个库发新版本
- 也不会出现最终的 bundle 里被打包了两个版本的 `@scure/btc-signer` 的尴尬场景

#### 可选依赖：按需安装

钱包 SDK（如 `sats-connect`、`@leather.io/rpc`）是**可选**的 peer dependencies：

```bash
# 只支持 UniSat 和 OKX？不需要安装任何额外依赖

# 需要支持 Xverse？
pnpm add sats-connect

# 需要支持 Leather？
pnpm add @leather.io/rpc
```

你只安装你需要的，减少你被恶意包脚本攻击的风险。

#### 动态导入：延迟加载

这是另外一个重要的安全设计：**钱包 SDK 通过 `dynamic import()` 延迟加载**。

```typescript
// 内部实现示意
const availability = createAvailability({
  getPrecondition: () => window.unisat ?? null,
  initializer: async () => {
    // 只有用户真正要连接这个钱包时，才会加载对应的实现
    const { UnisatWalletAdapterImpl } = await import(
      "./UnisatWalletAdapter.impl"
    );
    return new UnisatWalletAdapterImpl();
  },
});
```

假设 `sats-connect` 这个包被供应链攻击了（这在 npm 生态并不罕见）。如果你的用户只使用 UniSat 钱包，**恶意代码不会被加载和执行**，因为 `sats-connect` 只有在用户点击"连接 Xverse"时才会被 import。

这一条应该能降低用户被供应链攻击的风险。

## 框架集成

目前提供了 React 集成，开箱即用的 Context Provider：

```tsx
import {
  BitcoinConnectionProvider,
  useBitcoinConnectionContext,
} from "bitcoin-wallet-connector/react";
import {
  UnisatWalletAdapterFactory,
  XverseWalletAdapterFactory,
} from "bitcoin-wallet-connector/adapters";

const adapterFactories = [
  UnisatWalletAdapterFactory(),
  XverseWalletAdapterFactory(),
];

function App() {
  return (
    <BitcoinConnectionProvider
      adapterFactories={adapterFactories}
      onWalletConnected={(session) => console.log("Connected:", session)}
      onWalletDisconnected={() => console.log("Disconnected")}
    >
      <WalletUI />
    </BitcoinConnectionProvider>
  );
}

function WalletUI() {
  const { walletSession, availableAdapters, connect, disconnect } =
    useBitcoinConnectionContext();

  if (walletSession) {
    return (
      <div>
        <p>已连接: {walletSession.adapterId}</p>
        <button onClick={() => disconnect()}>断开连接</button>
      </div>
    );
  }

  return (
    <div>
      {availableAdapters.map(([adapterId, adapter]) => (
        <button key={adapterId} onClick={() => connect(adapterId, adapter)}>
          连接 {adapterId}
        </button>
      ))}
    </div>
  );
}
```

核心的 `BitcoinWalletConnector` 是框架无关的，如果你在用 Vue、Svelte、Solid 或其他框架，封装成对应的 hooks/composables 应该不难，当然也欢迎贡献！

## 欢迎使用和贡献

### 快速开始

```bash
pnpm add bitcoin-wallet-connector @scure/base @scure/btc-signer

# 按需安装钱包 SDK
pnpm add sats-connect      # Xverse / Magic Eden
pnpm add @leather.io/rpc   # Leather
```

或者 5 分钟跑通 Demo：

1. Clone 仓库
2. `pnpm install`
3. `pnpm storybook`
4. 打开 http://localhost:6006

你可以在 Storybook 里测试各种钱包的连接、签名等功能。

### 贡献

这是一个开源项目，非常欢迎社区贡献！

如果你想添加新钱包的支持，有一个小小的期望：**尽量不要依赖钱包官方的 SDK**。

为什么？因为很多钱包的 API 其实就是挂在 `window` 对象上的，直接调用就行，没必要引入一个额外的 SDK。比如 UniSat、OKX、Bitget 的 adapter 都是零外部依赖的。

引入 SDK 意味着多一个潜在的供应链攻击入口、用户需要多安装一个包、可能引入不必要的 bundle size。当然，如果某个钱包确实只能通过 SDK 接入（比如 Xverse 的 `sats-connect`），那也没问题，我们可以把它作为可选的 peer dependency。

详细的贡献指南请看 [CONTRIBUTING.md](https://github.com/bolasblack/bitcoin-wallet-connector/blob/master/CONTRIBUTING.md)。

---

项目完全开源（MIT），欢迎 Star 和贡献！

- GitHub: https://github.com/bolasblack/bitcoin-wallet-connector
- Demo: https://bitcoin-wallet-connector.netlify.app/
- npm: https://www.npmjs.com/package/bitcoin-wallet-connector
