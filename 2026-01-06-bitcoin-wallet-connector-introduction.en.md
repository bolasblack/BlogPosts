---
title: "Bitcoin Wallet Connector: Probably the Best Bitcoin Wallet Adapter Currently"
tags: [Bitcoin, Wallet, Connector, Adapter, React, TypeScript]
---

Clickbait intended 🤪

In the [previous article](./2026-01-03-bitcoin-wallet-ecosystem-is-a-mess.en.md), I ranted about how chaotic the Bitcoin wallet ecosystem is: WBIPs standards that nobody implements, sats-connect compatibility issues, wallets each doing their own thing with APIs...

This article introduces the library I built: [bitcoin-wallet-connector](https://github.com/bolasblack/bitcoin-wallet-connector).

Try the **[Live Demo](https://bitcoin-wallet-connector.netlify.app/iframe.html?id=components-bitcoinconnectionprovider--default&viewMode=story)** first, or check out the **[Storybook](https://bitcoin-wallet-connector.netlify.app/)**.

## What This Library Focuses On

### Smoothing Out the Ridiculous Differences

`bitcoin-wallet-connector` provides a unified API that lets you connect to all supported wallets with the same code:

```typescript
import {
  BitcoinWalletConnector,
  UnisatWalletAdapterFactory,
  XverseWalletAdapterFactory,
  LeatherWalletAdapterFactory,
} from "bitcoin-wallet-connector";

// Register the wallets you want to support
const connector = new BitcoinWalletConnector([
  UnisatWalletAdapterFactory(),
  XverseWalletAdapterFactory(),
  LeatherWalletAdapterFactory(),
]);

// Subscribe to available wallet changes
// Note: The timing of wallet extension API injection is unpredictable
// (some inject at DOMContentLoaded, others after the load event),
// so subscribing is recommended over getting
connector.subscribeAvailableAdapters((availableAdapters) => {
  console.log(
    "Available wallets:",
    availableAdapters.map(([id]) => id)
  );
  // => ['unisat', 'xverse', ...]
});

// Connect wallet - same API for all wallets
const [adapterId, adapter] = availableAdapters[0];
await connector.connect(adapterId, adapter);

// Get addresses, sign, send transactions - unified interface
const addresses = await adapter.getAddresses();
const result = await adapter.signMessage(
  addresses[0].address,
  "Hello Bitcoin!"
);
```

That's it. Write the code once, support all wallets.

Currently supported wallets:

| Wallet                                     | Adapter                         | Extra Dependencies |
| ------------------------------------------ | ------------------------------- | ------------------ |
| [UniSat](https://unisat.io/)               | `UnisatWalletAdapterFactory`    | -                  |
| [Xverse](https://www.xverse.app/)          | `XverseWalletAdapterFactory`    | `sats-connect`     |
| [OKX](https://www.okx.com/web3)            | `OkxWalletAdapterFactory`       | -                  |
| [Leather](https://leather.io/)             | `LeatherWalletAdapterFactory`   | `@leather.io/rpc`  |
| [Bitget](https://web3.bitget.com/)         | `BitgetWalletAdapterFactory`    | -                  |
| [Magic Eden](https://wallet.magiceden.io/) | `MagicEdenWalletAdapterFactory` | `sats-connect`     |

All adapters implement the same interface:

```typescript
interface WalletAdapter {
  // Connect/Disconnect
  connect(): Promise<void>;
  disconnect(): Promise<void>;

  // Get addresses
  getAddresses(): Promise<WalletAdapterAddress[]>;

  // Message signing
  signMessage(address: string, message: string): Promise<SignMessageResult>;

  // Send BTC
  sendBitcoin(
    fromAddress: string,
    receiverAddress: string,
    satoshiAmount: bigint
  ): Promise<{ txid: string }>;

  // PSBT signing
  signAndFinalizePsbt(
    psbtHex: string,
    signIndices: [address: string, signIndex: number][]
  ): Promise<{ signedPsbtHex: string }>;

  // Listen for address changes
  onAddressesChanged(callback): { unsubscribe: () => void };
}
```

No matter which wallet users choose, your business logic stays the same.

#### About sendRunes/sendInscriptions/sendBRC20

Currently, this library only supports `signMessage`, `sendBitcoin`, and `signPsbt`. It doesn't support `sendRunes`, `sendInscriptions`, or `sendBRC20`.

Because these involve more complex dependencies (like needing an Ordinals Indexer, a BRC20 Indexer, etc.). These would make a `Connector` overly complicated.

In my view, this should be the responsibility of a `Transaction Builder`. The `Transaction Builder` handles transaction construction, then passes it to the `Connector` for signing and broadcasting.

### Security Matters

When designing this library, I prioritized **dependency security**. The reason is simple: wallet libraries directly handle user assets, and any security vulnerability could result in real financial losses.

#### Peer Dependencies

I declared important dependencies as peer dependencies rather than bundling them:

```bash
pnpm add bitcoin-wallet-connector @scure/base @scure/btc-signer
```

This means:

- You directly control the versions of these dependencies
- If a dependency has a security vulnerability, you can **upgrade immediately** without waiting for this library to release a new version
- You won't end up with two versions of `@scure/btc-signer` bundled in your final build

#### Optional Dependencies: Install Only What You Need

Wallet SDKs (like `sats-connect`, `@leather.io/rpc`) are **optional** peer dependencies:

```bash
# Only supporting UniSat and OKX? No extra dependencies needed

# Need Xverse support?
pnpm add sats-connect

# Need Leather support?
pnpm add @leather.io/rpc
```

You only install what you need, **reducing your exposure to malicious package scripts**.

#### Dynamic Imports: Lazy Loading

This is another important security design: **Wallet SDKs are lazy-loaded via `dynamic import()`**.

```typescript
// Internal implementation sketch
const availability = createAvailability({
  getPrecondition: () => window.unisat ?? null,
  initializer: async () => {
    // Only when user actually wants to connect this wallet
    // will the corresponding implementation be loaded
    const { UnisatWalletAdapterImpl } = await import(
      "./UnisatWalletAdapter.impl"
    );
    return new UnisatWalletAdapterImpl();
  },
});
```

Suppose `sats-connect` gets compromised in a supply chain attack (not uncommon in the npm ecosystem). If your users only use UniSat wallet, **the malicious code won't be loaded or executed**, because `sats-connect` is only imported when users click "Connect Xverse".

This should **reduce users' exposure to supply chain attacks**.

## Framework Integration

Currently React integration is provided with an out-of-the-box Context Provider:

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
        <p>Connected: {walletSession.adapterId}</p>
        <button onClick={() => disconnect()}>Disconnect</button>
      </div>
    );
  }

  return (
    <div>
      {availableAdapters.map(([adapterId, adapter]) => (
        <button key={adapterId} onClick={() => connect(adapterId, adapter)}>
          Connect {adapterId}
        </button>
      ))}
    </div>
  );
}
```

The core `BitcoinWalletConnector` is framework-agnostic. If you're using Vue, Svelte, Solid, or other frameworks, wrapping it into corresponding hooks/composables should be straightforward. Contributions are welcome!

## Get Started and Contribute

### Quick Start

```bash
pnpm add bitcoin-wallet-connector @scure/base @scure/btc-signer

# Install wallet SDKs as needed
pnpm add sats-connect      # Xverse / Magic Eden
pnpm add @leather.io/rpc   # Leather
```

Or get the demo running in 5 minutes:

1. Clone the repo
2. `pnpm install`
3. `pnpm storybook`
4. Open http://localhost:6006

You can test wallet connections, signing, and other features in Storybook.

### Contributing

This is an open source project, and community contributions are very welcome!

If you want to add support for a new wallet, there's one small preference: **try to avoid depending on the wallet's official SDK**.

Why? Because many wallet APIs are just mounted on the `window` object and can be called directly without introducing an additional SDK. For example, the UniSat, OKX, and Bitget adapters have zero external dependencies.

Introducing an SDK means one more potential supply chain attack vector, one more package users need to install, and potentially unnecessary bundle size. Of course, if a wallet can only be accessed through its SDK (like Xverse's `sats-connect`), that's fine — we can make it an optional peer dependency.

See [CONTRIBUTING.md](https://github.com/bolasblack/bitcoin-wallet-connector/blob/master/CONTRIBUTING.md) for detailed contribution guidelines.

---

The project is fully open source (MIT). Stars and contributions are welcome!

- GitHub: https://github.com/bolasblack/bitcoin-wallet-connector
- Demo: https://bitcoin-wallet-connector.netlify.app/
- npm: https://www.npmjs.com/package/bitcoin-wallet-connector
