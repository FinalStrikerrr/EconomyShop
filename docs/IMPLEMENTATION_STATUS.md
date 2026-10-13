# Implementation status

Implemented in this source package:
- Java 25 Maven project and plugin metadata.
- Configurable material-based buy/sell prices and category GUI.
- Vault provider detection and safe disabling of money operations when absent.
- SQLite persistence for orders, order fulfillment and sell history (current calls are synchronous and can stall the main thread).
- Player order creation/cancellation with escrow accounting.
- Partial order fulfillment with conditional updates to avoid over-fulfillment.
- Selling routes to eligible orders by highest unit price when better than the configured base sell price; remaining units fall back to base sell price.
- Inventory GUI click/drag protections for plugin-owned menus.
- Command permission checks and basic help/reload operations.

Not implemented in this initial package:
- Async database worker/scheduler handoff; current database calls can stall the main thread and need refactoring before high-population production use.\n- MySQL/MariaDB backend, PlaceholderAPI expansion, Auction House adapters.
- A complete visual create-order wizard and all of the advanced admin GUI controls.
- Full item metadata matching (prices match by material, with conservative rejection of container items).
- Folia certification and tested version-specific artifacts.
- Runtime integration tests against real Paper/Folia/Spigot/Purpur servers.

The environment used to assemble this archive had Java 21 and no Maven installed, so `mvn clean verify` could not be run here. Do not treat this archive as a verified production release until it is built with JDK 25 and tested on a staging server.


## Enhancement pass (2026-10-13)

- Added `/shop search <query>` with matching configured material results, paging, and buy/sell price lore.
- Added right-click quantity selector with bounded decrement/increment controls and a final purchase confirmation.
- Cached YAML files in memory and explicitly refreshes the cache on `/economyshop reload`, avoiding repeated disk reads on every GUI click.
- Missing or malformed configured prices now fail closed instead of silently becoming zero. Price values are rounded to configured currency precision before calculations.
- Selling and order fulfillment now conservatively exclude item stacks with metadata, reducing accidental sale of renamed, enchanted, damaged, or otherwise customized items under material-only pricing.
- Order delivery claims now check inventory capacity, read pending rows without consuming them, and acknowledge rows only after insertion; this avoids the prior immediate-loss case where rows were marked claimed before delivery. A process crash between insertion and acknowledgement can still require reconciliation; Bukkit inventory and SQLite cannot be made one atomic transaction.
- Added persistent audit entries for several failed economy compensations, so staff have evidence to reconcile provider/database inconsistencies.

Still not complete: Auction House, MySQL/MariaDB, PlaceholderAPI expansion, async database execution, full metadata-aware item matching, automated integration tests, and verified Folia support. The source archive remains a development build and must be tested with JDK 25 and a staging server before live deployment.
