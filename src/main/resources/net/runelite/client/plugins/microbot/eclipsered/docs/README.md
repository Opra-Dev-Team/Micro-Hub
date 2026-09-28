# Eclipse Red

Hops safe members worlds collecting the single Eclipse red spawn on the top floor of Guildmaster Apatura's quarters in the Hunter Guild. When the inventory hits the configured threshold, wines are deposited at the Hunter Guild bank.

## Requirements

- Membership
- Hunter Guild access
- Start near the guild. The spawn tile is `1555, 3035, 2`. The bank is `1542, 3041, 0`.

## How it works

1. Banks at the Hunter Guild first and reads coins plus Eclipse red already in the bank. Each wine is 700 gp. The overlay shows total wealth from then on, including wines picked up before the next deposit.
2. Walks to the spawn tile.
3. Picks up Eclipse red (item 29415) if it is on the tile.
4. Hops to a random accessible members world that is not PvP, high-risk, bounty, skill-total, LMS, deadman, arena, tournament, seasonal, beta, or fresh start.
5. Repeats until the wine threshold is reached (default 28).
6. Banks at Hunter Guild. Config chooses resume or stop after depositing. The opening bank always continues into the collect loop.

Worlds recently visited are skipped for the configured cooldown so the spawn has time to exist again.

A hop that only flashes the login screen is left alone. If the client stays on the login screen, or the login text says the hop limit was hit, the script waits (default 15 minutes) and logs back in. It does not stop. Banned accounts, bad credentials, and a non-member on a members world still stop.

## Config

- Wine threshold (inventory size before banking)
- Stop goal: WINES or GP
- Stop after wines (session total, 0 = unlimited)
- Stop after gp (in k): 100 = 100k, 1000 = 1m. Each wine is 700 gp, so 100k needs 143 wines
- After banking: RESUME or STOP
- World cooldown
- Avoid empty / overcrowded worlds
- Max wait for wine before hopping
- Hop limit wait (minutes)
