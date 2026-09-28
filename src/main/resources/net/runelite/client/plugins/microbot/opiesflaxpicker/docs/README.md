# [OPIE] Flax Picker

Picks flax at Nemus Retreat. Pick flax only banks every full inventory. Bow strings and linen yarn spin that inventory at the nearby wheel, then bank.

## Setup

1. Stand at Nemus Retreat, at the bank or the flax field.
2. Install it with `installer\OpiesPluginLibrary-Setup.bat` from the Opie plugin library, restart the client, then enable **[OPIE] Flax Picker**.
3. Choose the mode in the plugin config. Bow strings need Crafting 10. Linen yarn needs Crafting 12.

Anything already in the inventory is banked first so all 28 slots are free.

## Config

- **Mode**: Pick flax only, Bow strings, or Linen yarn.
- **Pick speed**: Spam pick, Fast click, or Normal click. Spam pick hits the same plant until it is depleted.
- **Stop after**: session cap on banked flax, bow strings, or linen yarn, depending on the mode. `0` means no limit.
- **Bank PIN**: optional 4-digit PIN.
- **Hide overlay**: hide the stats panel.

## Route

| Step | Location |
|------|----------|
| Flax | Nemus Retreat field (`1373, 3320, 0`) |
| Spinning wheel | (`1372, 3314, 0`), bow strings or linen yarn |
| Bank | Nemus Retreat (`1386, 3309, 0`) |

One pick can give several flax. The script stays on that plant until it is depleted, then clicks the next closest flax.

Pick flax only uses the collecting antiban setup. Bow strings and linen yarn use the crafting antiban setup.
