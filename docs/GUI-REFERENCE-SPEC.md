# EconomyShop GUI Reference Specification

Research date: 2026-10-13. This is a source-grounded reference, not a claim of access to DonutSMP's private implementation.

## Verified public behavior

### Shop and sell

- The Modrinth page for `DonutShop` describes itself as a replica of DonutSMP `/shop` and `/sell`, and exposes gallery entries named shop GUI, sell GUI, and sell history GUI. Its public page states a 1.21.x requirement and Vault/economy dependencies. This establishes the broad interaction family, but does not establish exact slot coordinates from text alone.
- The public `Shop DonutSMP` page describes a categories GUI, per-shop menus, and a quantity selector. It is buy-only, so it is useful as a shop navigation reference, not as proof of sell behavior.
- A separate `DonutShop` project documents categories such as End, Nether, Gear, and Food; configurable titles, item prices, quantity selection, and sound feedback. Treat these as that plugin's documented behavior, not necessarily canonical DonutSMP layout.

### Orders

The DonutSMP Wiki's Orders page (last updated 2026-09-07) describes player-created buy requests, `/orders`, `/order <search>`, buyer-defined price and quantity, and the June 9, 2026 integration between Orders, `/sell`, and the Auction House. The page notes that an order's actual availability depends on matching rules, capacity, and funds.

### Auction House

The public Donut Auction Modrinth page describes a 54-slot browser with pagination, sorting, category filters, refresh, search, and personal listings. It documents active/sold/expired listing management, SQLite by default with optional MySQL, Vault economy integration, and asynchronous database operations. Its changelog includes a previous-page button at slot 46 and a sell-held-item button at slot 45 in a particular version; those slot values are version-specific and should not be assumed canonical across all versions.

## Layout rules for EconomyShop

The available public text supports the feature-level specification above, but it does not independently verify every requested slot, border, title, or click behavior across the supplied references. Therefore this project must not label any unverified coordinates as an exact DonutSMP replica.

Fallback layout to implement consistently:

- Shop category screen: 27 slots, category icons in a compact central grid, balance and close controls in the bottom row.
- Category/item screen: 54 slots, 45 content slots, previous/back/next controls on the bottom row.
- Sell screen: 54 slots, inventory-derived eligible items, explicit sell-all and close controls.
- Auction browser: 54 slots, 45 listing slots, distinct search/sort/filter/refresh/personal-listing controls, and explicit pagination.
- Orders browser: 54 slots, order cards with item, owner, remaining quantity, unit price, and a clear fulfill action.
- Confirmation screens: separate confirm and cancel buttons; prices and quantities are calculated server-side and revalidated at commit time.

These are EconomyShop fallback layouts, not verified copies of private or undocumented DonutSMP coordinates.

## Public references

- https://modrinth.com/plugin/donut-shop
- https://modrinth.com/plugin/shop-donutsmp
- https://donutsmp.wiki/orders
- https://modrinth.com/plugin/donut-auction
- https://modrinth.com/plugin/auctionhousepro/

## Implemented development-build refinements

- Main shop is a 54-slot category browser with a 45-slot content area, category pagination, search entry point, balance, and close control.
- Category menus use 45 content slots and a consistent bottom navigation row.
- Search results use the same item lore and price formatting as category menus, with previous/back/next controls and an empty state.
- Right-clicking a shop item opens a quantity selector with decrement/increment controls, a computed total, confirm, and cancel.
- Sell and order menus include explicit close controls; order results have pagination and an empty state.
- The inventory holder is now bound to its actual inventory instead of returning `null`.

These are the project's own fallback layouts. Exact DonutSMP slot parity remains unverified.
