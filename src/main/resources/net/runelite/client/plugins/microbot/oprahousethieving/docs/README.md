# [Opra] House Thieving

Pickpockets wealthy citizens and thieves houses in Varlamore. This plugin is standalone. It does not use the Microbot Hub House Thieving plugin.

Imported from Microbot Hub House Thieving and renamed so both can be installed at the same time.

## Setup

1. Start in Varlamore with 50 Thieving and Children of the Sun completed.
2. Install it with `installer\OpraMicroHub-Setup.bat`, restart the client, then enable **[Opra] House Thieving**.

Rogue's outfit and a Graceful cape are recommended. The Civitas bank (near the houses) is used for banking.

## Config

The config group is `opra-house-thieving`.

### House thieving

- **Max House Keys** / **Minimum House Keys**: pickpocket until the max, then thieve houses down to the minimum.
- **Auto Gear**: on start, bank at Civitas to equip the best Rogue and Graceful pieces you own, deposit everything except valuables and house keys, then withdraw all valuables and house keys. If you are already in full Rogue plus Graceful cape and have keys, the bank is skipped. Missing pieces are skipped; the run continues with what you have.
- **Bank PIN**: your 4-digit PIN if the account uses one (hidden in config). Leave empty if you have no PIN.

### Pickpocketing

- **Pickpocket Food Amount** / **Food Selection** / **Food Eat Percentage**: food used while pickpocketing.
- **Use Dodgy Necklace** / **Dodgy Necklace Amount**: optional dodgy necklaces from the bank.
- **Only Pickpocket Distracted**: wait for a distraction instead of bringing food.
- **World Hop For Distracted Citizens** / **World Hop Wait Time**: hop if no distraction starts in time.

## Behavior

- Scouting clicks visible locked doors at any distance and exits through the window when the homeowner returns.
- Bonus shine chests are claimed quickly when the chat warns you.
- Closed houses reopen only after the door was seen unlocked (owner home) and locked again with the owner away.
- When every owner is home, the bot waits at the scout tile of the house likeliest to open next instead of looping on one spot.
- When the inventory fills during a run, coin pouches are opened, then everything except house keys and valuables is banked at Civitas, and thieving continues.
