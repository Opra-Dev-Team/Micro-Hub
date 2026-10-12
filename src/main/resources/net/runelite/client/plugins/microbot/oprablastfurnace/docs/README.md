# [Opra] Blast Furnace

Smelts bars at the Blast Furnace in Keldagrim. This plugin is standalone. It does not use the Microbot Hub Blastoise Furnace plugin.

Imported from Microbot Hub Blastoise Furnace and renamed so both can be installed at the same time.

## Setup

1. Start at the Blast Furnace with ice gloves or smiths gloves (i) and ores for your chosen bar type. Stamina or energy potions are optional; if they are in the bank they will be used when run energy is low.
2. For non-gold bars, bring a coal bag. For gold bars, use goldsmith gauntlets and bank the coal bag.
3. Install with `installer\OpraMicroHub-Setup.bat`, restart the client, then enable **[Opra] Blast Furnace**.

## Config

The config group is `opra-blast-furnace`.

- **Bars**: steel, gold, mithril, adamantite, runite, or hybrid gold and coal modes.
- **Top Up Amount** (Coffer, default 50,000): coins deposited into the coffer each time it is topped up.
- **Top Up At** (Coffer, default 20,000): the coffer is topped up once it drops below this many coins. It is checked once per bank visit, and coins are taken from the bank.

Below 60 Smithing, the foreman's 2,500 coin fee is paid automatically at the bank before the coffer is touched, and again every 9.5 minutes.

Control flow was rewritten in 1.0.2 so partial bank withdraws no longer skip ahead, SMITHING waits for bars, and dispenser loot confirms success before banking again.

In 1.0.6 the coal bag is tracked as full or not full from its chat messages. The bag is filled from the bank first, and the inventory load (coal, primary ore or gold ore) is withdrawn after. This fixes the loop where the script kept trying to fill a bag that was already full. The script no longer assumes the bag is empty when it starts.

In 1.0.7, coal-only trips (used to stock the furnace with coal when it runs low) go straight back to the bank for the next ore trip instead of waiting at the dispenser. Waiting for bars also stops and returns to the bank if the furnace has no primary ore to smelt.

In 1.0.8, bars are collected in one go: the script waits until the whole batch has smelted, then keeps taking from the dispenser until it and the furnace are empty. The coffer is restocked automatically using the Top Up Amount and Top Up At options.

In 1.0.9 the coffer top up withdraws the full amount in one go and deposits it all. Before, coins in the inventory were counted as 1, so it kept withdrawing small amounts. Top ups under 1,000 coins are skipped, and a warning is logged if Top Up At is not lower than Top Up Amount. The foreman tax also only withdraws coins when the inventory has fewer than 2,500.

In 1.0.10, Top Up Amount means the coins added per top up, not a target total. The foreman fee is paid first at each bank visit when it is due, and the payment is confirmed by the coins leaving your inventory. The coffer deposit step now detects the amount prompt, and both the coffer and foreman steps stop retrying after 3 failures. Every step logs the dialogue text it sees.

In 1.0.11, taking bars from the dispenser needs one click. The script now recognises the "How many bars" menu and presses space, instead of clicking Take again because it missed the menu.

In 1.0.12 the script recovers by itself:
- If the take menu doesn't open, it clicks the dispenser again. After 4 failed tries it resets through the bank.
- Level-up and other "click to continue" popups are cleared automatically.
- A watchdog resets to the bank if the script makes no progress for 45 to 90 seconds. After 3 resets within 5 minutes it logs out and stops for safety.

In 1.0.13 the script pauses while you move the mouse or type in the game window. Microbot blocks script input during that time, so for best results keep the mouse off the game while it runs. Paused time never counts as a failed take. Taking bars now tries space twice, then clicks the bar in the take menu directly.

## Debug (test runs)

1. In plugin config, open **Debug** and enable **Debug logging**.
2. Set **Stall warning seconds** (default 15) if you want warnings when a step stops advancing.
3. Watch the overlay for **State**, **Step**, **Idle**, and a compact snapshot line.
4. In the client log and Microbot.log, look for lines prefixed with `[BF]`. Stall warnings look like `STALL idle=... step=...`.
5. After a stuck run, copy log output from plugin start through the first `STALL` line (or the last repeated **Step**) for review.
