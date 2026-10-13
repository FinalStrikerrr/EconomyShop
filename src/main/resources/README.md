# EconomyShop 1.0.0

EconomyShop is a Java 25 / Maven plugin source project for Paper-compatible servers. It provides a configurable shop, inventory selling, and persistent player buy orders backed by SQLite.

## Requirements
- Java 25 runtime (the generated class files target Java 25)
- Maven 3.9+
- Paper 1.20.1 API baseline; compatibility with each server fork/version must be verified on that exact runtime
- Vault and a compatible economy provider for monetary operations

Java 25 bytecode **will not load on Java 21 or Java 17**. Install and run the server using Java 25. Java source compatibility does not guarantee compatibility with all Minecraft versions.

## Build
```bash
mvn clean verify
mvn clean package
```
Output: `target/EconomyShop-1.0.0.jar`

## Install
1. Build with JDK 25.
2. Copy the jar into `plugins/`.
3. Install Vault and an economy provider such as EssentialsX if you want money features.
4. Start the server once to generate configuration files.
5. Configure `prices.yml` and `categories.yml`, then run `/economyshop reload`.

## Commands
- `/shop` — category shop
- `/sell` — sell menu / eligible inventory sales
- `/sellhand` — sell the held stack
- `/sellall` — sell eligible configured items
- `/orders` — browse active buy orders
- `/order create <MATERIAL> <QUANTITY> <UNIT_PRICE>` — create an order
- `/order manage` — list/cancel your active orders
- `/sellhistory` — recent sales
- `/balance`, `/worth`
- `/economyshop reload`, `/economyshop admin`

## Permissions
See `plugin.yml`. Administrative actions require `economyshop.admin` or the narrower `economyshop.admin.reload`.

## Transaction and compatibility notes
SQLite operations are synchronized but currently run synchronously from command/event paths. This can cause tick stalls under load and must be refactored to async database work with scheduler-safe inventory handoff before high-population production use. Order fulfillment uses conditional database updates to reduce over-fulfillment races. Bukkit inventory and external Vault economy calls cannot participate in the same ACID transaction; therefore no plugin can honestly guarantee perfect atomicity across both systems. This project uses a guarded workflow and conservative validation, but must be tested on a staging server before production use. External economy provider behavior varies.

Folia is **not declared as supported** in metadata because region-aware inventory transaction paths have not been certified here. Do not enable Folia support until audited and tested against the exact Folia build. Spigot/Purpur compatibility likewise requires runtime testing.

## Current scope
This project is a functional initial implementation, not a claim that every item in a large production specification has been validated on every server. MySQL, Auction House adapters, full metadata matching, GUI-based order wizard, PlaceholderAPI expansion, and Folia support are not implemented in this initial source package. Use SQLite and material-based pricing for the implemented core. See `docs/IMPLEMENTATION_STATUS.md`.

## Security
Never give untrusted users write access to plugin configuration. Do not configure command rewards from player-controlled data. Back up `plugins/EconomyShop/economyshop.db` before upgrades.
