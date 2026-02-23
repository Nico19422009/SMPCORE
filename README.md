# SMPCORE
A Minecraft Paper plugin to configure SMP server features.

## Features (v1)
- Sidebar profile/status display
- Multi-currency economy (primary + secondary currencies)
- Player trading request/accept flow and trade payments
- Auction House with listing fee and tax
- Admin config tools (`/smpconfig reload|status|set`)

## Build
Requirements:
- Java 17+
- Gradle 8+

Build JAR:
```bash
gradle clean build
```

Output:
- `build/libs/smpcore-<version>.jar`

## Test on Paper
1. Copy the built JAR into your server `plugins/` folder.
2. Start server and ensure SMPCORE enables cleanly.
3. Run quick checks:
   - `/smpconfig status`
   - `/balance`
   - `/pay <player> <amount>`
   - `/trade <player>` then `/trade accept`
   - `/ah sell <price>`, `/ah browse`, `/ah buy <id>`

## Default config/resources
- `config.yml` — feature toggles, currency setup, economy/auction values
- `messages.yml` — text messages
- `balances.yml` — economy balances
- `auctions.yml` — auction listings
