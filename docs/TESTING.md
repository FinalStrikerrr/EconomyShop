# Validation report

Environment available while assembling this source: OpenJDK 21.0.12; Maven was not installed. The project explicitly targets Java 25, so a valid compile/test run was not possible in that environment.

Run these on a machine with JDK 25 and Maven 3.9+:
```bash
java -version
mvn clean verify
```
Then test on a disposable Paper 1.20.1 server with Vault + an economy provider. Exercise order creation, partial fulfillment, cancellation, restart persistence, inventory full/partial inventory, and forced database/provider failures. Test a current supported Paper release separately. No Folia certification is claimed.

The included JUnit tests cover decimal price arithmetic only; they do not substitute for integration or concurrency tests.

## Added automated repository tests

`src/test/java/com/avenoxstudio/economyshop/storage/OrderRepositoryTest.java` covers partial order fulfillment, escrow reduction, pending delivery acknowledgment, payout validation, and rejecting mismatched escrow at order creation. Run with `mvn -U clean verify` on JDK 25.

## Added manual GUI checks

- Open `/shop`; click every category and verify no filler item triggers a category action.
- Run `/shop search diamond`; verify matching results, prices, empty state, and pagination.
- Right-click an item; change quantity up and down, cancel, then confirm and verify exact charged total.
- Open `/sell`; verify custom-named, enchanted, or damaged items are not shown as eligible material-priced items.
- Open `/orders diamond`; test previous/next page, no-results state, close, and order card click behavior.
- Run `/sell preview`, `/sell hand`, `/sell all`, and `/sell history`.
- Claim an order delivery with a full inventory; it should remain pending and no items should be dropped.
- Force an economy provider failure in staging and confirm the console/audit log reports reconciliation rather than claiming a rollback succeeded.
