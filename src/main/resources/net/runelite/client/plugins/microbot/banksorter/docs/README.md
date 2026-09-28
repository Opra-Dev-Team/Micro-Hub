# [Opra] Bank Sorter

Organizes an ironman-style 8-tab bank, then sorts each category as a contiguous block. Empty placeholder slots are moved with the real item, so a later deposit fills the hole in the right tab.

## Tabs

- **1 Supplies:** teleports, potions by dose, cooked food, and drinks when they have their own block
- **2 Combat:** weapons, armour, ammo, sets
- **3 Gathering Materials:** raw fish/meat, ores, logs, uncut gems, bones, hides
- **4 Production Tools:** skilling tools, moulds, bars, planks, leather, glass, textiles
- **5 Runes / Magic:** runes, pouches, essence, non-teleport tablets
- **6 Farming / Herblore:** herbs, seeds, saplings, secondaries, farming supplies
- **7 Clues / Uniques:** clues when that placement is selected, non-combat uniques, holiday rares
- **8 Misc / Junk:** quest items and uncategorized leftovers
- **Main:** coins, platinum tokens, bonds, and clues when that placement is selected

## Settings

The Layout section is in the plugin config.

- **Layout:** Iron 8-tab is the default. Tight iron groups each tab by family (weapon, patch, fish, nails, and so on) and pulls leftovers such as tomato, unfinished potions, dyes, marks of grace, and sawmill coupons into a real tab.
- **Clues:** Main tab or Clues tab. Scrolls, scroll boxes, caskets, key halves, and clue hunter gear stay together. The default is the main tab, so boxes and caskets that used to sit on tab 7 move once.
- **Teleport jewellery:** Supplies or Combat. Supplies also picks up passage necklaces, explorer's rings, and lyres. Chronicle stays on Supplies.
- **Drinks:** With food, or their own block on Supplies after the food. Ale and tea join that choice. Wine of zamorak stays with herblore.

Changing a setting moves items on the next Organize.

## Install

Bank Sorter ships with the rest of the Micro Hub. Use `installer\OpraMicroHub-Setup.bat` at the root of that repo. Close the client first. The setup window copies the checked jars into the client plugin folder. Restart the client, enable **[Opra] Bank Sorter**, and disable any other bank sorter so you do not get two buttons.

## How to use

1. Enable **[Opra] Bank Sorter** and disable Hub **Bank Tab Sorter** so you do not get two buttons.
2. Open the bank. Placeholders and Insert mode are turned on automatically. Placeholder slots are organized, not skipped.
3. Click **Organize** for a full run, or **Sort Tab** to reorder only the open tab.
4. Click **Stop** if you need to cancel between moves.

RuneLite cannot rename bank tabs. First live run on a large bank takes minutes because every move is a real drag.

## Debug dumps

After Organize, files are written to:

- `debug-runs/<timestamp>/` in the bank sorter repo
- `.runelite/bank-sorter/<timestamp>/`

Each run includes `layout.txt`, `layout.json`, `mismatches.txt`, `run.log`, and `tab-N-*.png`. A second Organize should log `IDEMPOTENT: 0 misplaced` if tabs are already correct.
