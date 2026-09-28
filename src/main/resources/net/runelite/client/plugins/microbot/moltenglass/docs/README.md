# [Opra] Molten Glass

Smelts molten glass at the Edgeville furnace: withdraw 14 Buckets of sand and 14 Soda ash from the bank, smelt at the furnace, deposit everything, repeat.

## Setup

1. Stand at or near Edgeville bank (`3096, 3494, 0`).
2. Keep Buckets of sand and Soda ash in the bank (at least 14 of each per trip).
3. Install it with `installer\OpraMicroHub-Setup.bat` from the Micro Hub, restart the client, then enable **[Opra] Molten Glass**.

## Config

- **Stop after molten glass**: session cap on molten glass smelted (counted when deposited). `0` = unlimited.
- **Bank PIN**: optional 4-digit PIN.
- **Hide overlay**: hide the stats panel.

## Route

| Step | Location |
|------|----------|
| Bank | Edgeville (`3096, 3494, 0`) |
| Furnace | Edgeville furnace (`3109, 3499, 0`), clicked from the bank (no web-walk to the furnace tile) |

The script uses the crafting antiban template.
