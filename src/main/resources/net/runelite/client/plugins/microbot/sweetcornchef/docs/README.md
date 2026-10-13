# [Opra] Sweetcorn Chef

Picks outer sweetcorn at Kastori, cooks a full inventory on the nearby oven, drops cooked and burnt sweetcorn, then repeats.

The corn field cannot be walked into. Only outer plants within pick range (2 tiles) are used. The oven is clicked from outside the hut with the camera turned to it; the script does not walk into the oven building.

## Setup

1. Stand at the Kastori oven path (`1379, 3053, 0`) with an empty inventory.
2. Cooking level 28+ is required for sweetcorn.
3. Install it with `installer\OpraMicroHub-Setup.bat` from the Micro Hub, restart the client, then enable **[Opra] Sweetcorn Chef**.

## Config

- **Pick speed**: Spam pick, Fast click, or Normal click. Spam pick hits the same plant until it is depleted.
- **Seeds**: Drop seeds with each batch, or keep them. Keeping seeds reserves an invent slot, so the pick target becomes 27 raw sweetcorn instead of 28.
- **Stop after**: session cap on completed cook/drop trips. `0` means no limit.
- **Hide overlay**: hide the stats panel.

## Route

| Step | Location |
|------|----------|
| Sweetcorn | Outer plants within 2 tiles of the player (near `1379, 3053, 0`) |
| Oven | Clicked from outside (`1379, 3054, 0`) |

Loop: pick until invent is full of raw sweetcorn (27 if keeping seeds), cook all on the oven, drop cooked and burnt (and seeds if Drop is selected), then pick again.
