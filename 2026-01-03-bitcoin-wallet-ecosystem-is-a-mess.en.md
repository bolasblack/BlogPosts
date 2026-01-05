---
title: "The Bitcoin Wallet Extensions Ecosystem is a Mess: A Developer's Rant"
tags: [Bitcoin, wallet, dApp, Web3]
---

If you're building a Bitcoin dApp, you've probably already discovered that the Bitcoin community's ecosystem is an absolute dumpster fire.

The simplest example is Bitcoin wallets. Everyone seems eager to create standards for others, but nobody seems eager to actually implement them.

## "Standards"? What Standards?

Let me be more specific:

### WBIPs: Even the Standard Makers Don't Follow Their Own Standards

Leather and Xverse created [WBIPs](https://wbips.netlify.app/), supposedly to establish a unified Bitcoin wallet API standard, but:

**The signPsbt finalize parameter**

The [`signPsbt`](https://github.com/wbips/wbips-docs/blob/a7862572785686da8c0097125366086fea3c70d6/pages/request_api/signPsbt.md) standard defines a `finalize` parameter, but neither of the initiators implemented it:

- Leather's [code](https://github.com/leather-io/mono/blob/2570b8e89e1a7d67ff2ec469c42b321f5d956e92/packages/rpc/src/methods/bitcoin/sign-psbt.ts#L25-L35)
- Xverse's [code](https://github.com/secretkeylabs/sats-connect-core/blob/cfbbdfde28a646f1b5114dad39ec76da85c3ffe9/src/request/types/btcMethods.ts#L178-L192)

**The wallet auto-discovery mechanism is completely wrong**

[WBIP004](https://github.com/wbips/wbips-docs/blob/a7862572785686da8c0097125366086fea3c70d6/pages/wbips/WBIP004.md) defines a wallet plugin auto-discovery mechanism, where the first step is to inject the wallet provider into `wbip_providers`.

But in reality, both Xverse and Leather inject into `window.btc_providers`:

- Leather's [code](https://github.com/leather-io/mono/blob/2570b8e89e1a7d67ff2ec469c42b321f5d956e92/packages/provider/src/add-leather-to-providers.ts#L21-L52)
- Xverse's [code](https://github.com/secretkeylabs/xverse-web-extension/blob/bdbd6a008fd3d8f04ea18eca0374fae67d90114c/src/inpage/index.ts#L53)

**Two other major wallets don't care at all**

Not to mention that **UniSat** and **OKX Wallet**, which actually have massive traffic, seem to have zero interest in supporting WBIPs.

### sats-connect: A Standard on Top of Standards

Then, beyond WBIPs, Xverse also created [sats-connect](https://github.com/xverseapp/sats-connect), apparently wanting to extend some of their own APIs beyond WBIPs, implementing a standard on top of a standard.

This isn't bad, especially since it also provides compatibility with the UniSat API. Magic Eden even claims to be "sats-connect compatible."

"This sounds absolutely amazing! sats-connect is the meta! The savior of the world!" Right? That's what you're thinking.

But there are still "two small clouds" hovering over this perfect world:

1. **sats-connect doesn't support OKX Wallet** (even though OKX Wallet's API has now migrated to be UniSat-compatible, sats-connect can't auto-discover it)
2. **Magic Eden's auto-discovery follows a different standard**: [`ExodusMovement/bitcoin-wallet-standard`](https://github.com/ExodusMovement/bitcoin-wallet-standard), not the WBIPs standard. So you can't rely on sats-connect to auto-discover it.

### Wait What, Another Standard?

"Wait, what the hell is this `ExodusMovement/bitcoin-wallet-standard`? I've never heard of it!"

Right, before I tried to integrate Magic Eden, I hadn't heard of it either. Now I've heard of it, and so have you.

### More Fun Facts

Oh, here's a "fun fact": if your users have both Xverse and Magic Eden installed, and you don't handle it properly, when they click "Connect Wallet," they might **randomly receive a confirmation request from one of the two wallets**. Life is full of surprises.

Oh, and another "fun fact": although Magic Eden claims to be "sats-connect compatible," it's actually compatible with **an older version of the API**.

## Show Some Code

You might think, "Fine, I'll just integrate them all myself." So let's look at the code differences for implementing a simple "connect wallet" feature:

```typescript
// UniSat - Direct window object call
const unisatResponse = await window.unisat.requestAccounts()

// Leather - Yet another API
const leatherResponse = await window.LeatherProvider.request("getAddresses")

// Xverse - From official docs
import { request } from "sats-connect"
const xverseResponse = await request("getAddresses", null)

// Magic Eden - From official docs
import { getAddress, AddressPurpose, BitcoinNetworkType } from "sats-connect"
await getAddress({
  getProvider: getBtcProvider,
  payload: {
    purposes: [AddressPurpose.Ordinals, AddressPurpose.Payment],
    message: "Address for receiving Ordinals and payments",
    network: {
      type: BitcoinNetworkType.Mainnet,
    },
  },
  onFinish: response => {
    console.log("onFinish response, ", response.addresses)
    connectionStatus?.setAccounts(response.addresses as unknown as Account[])
  },
  onCancel: () => {
    alert("Request canceled")
  },
})
```

Four wallets, four different implementations.

What if you need to integrate 6 wallets in your project? Congratulations, you'll need to go through all the issues I mentioned above, and enjoy wasting a huge chunk of your life along with me.

## To Sum It Up

"F\*\*k the stupid ecosystem, what a dumpster fire!" — you/I thought.

## So I Built a Library

So I built [bitcoin-wallet-connector](https://github.com/bolasblack/bitcoin-wallet-connector) to help others waste less time on this crap.

In the **next article**, I'll detail the usage and design philosophy of this library. If you can't wait, check out:

- **[Live Demo](https://bitcoin-wallet-connector.netlify.app/iframe.html?id=components-bitcoinconnectionprovider--default&viewMode=story)**
- **[GitHub Repository](https://github.com/bolasblack/bitcoin-wallet-connector)**
