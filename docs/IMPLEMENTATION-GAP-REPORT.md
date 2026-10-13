# Implementation Gap Report

Status assessed on 2026-10-13 from the source currently in this archive.

## Current source implements

- Java 25 Maven project metadata and plugin entry point.
- Configurable material buy/sell prices and a basic category GUI.
- Vault provider discovery and a safe warning when no provider is available.
- SQLite-backed buy orders, basic order escrow fields, partial order fulfillment records, deliveries, sales history, and audit table.
- Basic `/shop`, `/sell`, `/sellhand`, `/sellall`, `/orders`, `/order`, `/balance`, `/worth`, and `/economyshop` handling.
- Basic custom inventory holders and cancellation of click/drag interactions in plugin menus.
- ASM 9.9 dependencies under Maven Shade to address the Java 25 class-file parsing failure seen in the prior build log.

## Required before calling this a complete or production-ready release

- Auction House is not implemented: no listing lifecycle, item-payload escrow, browser, search/sort/filter, purchase confirmation, return inventory, seller payouts, or `/ah` command.
- Orders and Auction House do not share the requested routing engine because the Auction House does not yet exist.
- Order creation, refunds, economy-provider operations, and inventory removal are not one atomic transaction. Vault providers generally cannot participate in the same SQLite transaction; durable intents, idempotency, and reconciliation must be designed and tested before production use.
- Database calls are synchronous in the current implementation and can stall the main server thread.
- SQLite `claimDeliveries` currently claims rows before Bukkit inventory delivery is proven complete. This needs a claim/acknowledgment protocol and durable pending state to avoid loss on interruption.
- The item model currently matches mainly by Material; metadata-sensitive items, containers, custom items, and exact serialized payloads need explicit rules.
- No MySQL/MariaDB backend, schema migrations, PlaceholderAPI expansion, comprehensive tests, or verified Folia scheduler adapter exists yet.
- GUI layout is a consistent fallback, not a verified exact DonutSMP slot-for-slot replica.
- Build and runtime compatibility are not verified in this environment. The environment has Java 21 and no Maven, so Java 25 `mvn clean verify` cannot be honestly reported as passing here.

## Release rule

Do not deploy this archive as a live economy marketplace until the transaction/escrow gaps are addressed and tested on a staging server with a backup. The current project is an early development foundation, not a completed implementation of the supplied master prompt.

## Enhancement pass added 2026-10-13

- Main shop, item category, search, quantity selector, sell, and order GUIs were improved with consistent filler, price lore, pagination, empty states, and explicit close/back/confirm actions.
- Added `/shop search <query>`, `/sell hand`, `/sell all`, `/sell preview`, `/sell history`, and material-filtered `/orders <material>` / `/order <material>` flows.
- Added order result pagination and order-repository tests.
- Config files are cached rather than loaded from disk on every click, and `/economyshop reload` refreshes the cache.
- Price parsing now fails closed for missing, malformed, negative, or zero-after-rounding prices. Prices are normalized to the configured 0–2 decimal precision before use.
- Material-only sell/order paths reject item stacks with metadata rather than risk paying base-material prices for custom-named, enchanted, or damaged items.
- Delivery claims no longer mark rows claimed before inventory insertion. They preflight inventory space and acknowledge deliveries after insertion. A hard process crash between inventory insertion and DB acknowledgement remains a cross-system recovery case.
- Added audit logging for selected failed economy compensations and `/economyshop diagnostics` for recent audit entries.

## Remaining major blockers

- **Auction House is still not implemented.** `auction.yml` remains a configuration template, not a working `/ah` system.
- Orders and auctions do not share the requested cross-market routing engine because there is no Auction House service yet.
- Database calls remain synchronous; this is unsuitable for high-load production use.
- Vault transactions and SQLite updates are not atomically committed together. Audit entries help staff detect some failures but do not replace a durable idempotent transaction/reconciliation engine.
- MySQL/MariaDB, PlaceholderAPI, metadata-aware exact item matching, verified Folia scheduler support, and live runtime testing are still missing.
- No successful Java 25 Maven build was possible in the current environment (JDK 21 only; Maven unavailable). Do not label this archive a production-ready release.
