package com.avenoxstudio.economyshop.storage;

import com.avenoxstudio.economyshop.model.BuyOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderRepositoryTest {
    @TempDir Path tempDir;

    @Test void partialFulfillmentCreatesDurableDeliveryAndReducesEscrow() throws Exception {
        UUID owner=UUID.randomUUID(), seller=UUID.randomUUID();
        try(OrderRepository repository=new OrderRepository(tempDir.resolve("orders.db").toFile())) {
            long id=repository.create(owner,"DIAMOND",10,new BigDecimal("2.00"),new BigDecimal("20.00"),System.currentTimeMillis()+60_000);
            assertTrue(repository.fulfill(id,seller,3,new BigDecimal("6.00")));
            BuyOrder order=repository.get(id).orElseThrow();
            assertEquals(7,order.remainingQuantity());
            assertEquals(new BigDecimal("14.00"),order.escrow());
            List<OrderRepository.Delivery> pending=repository.pendingDeliveries(owner);
            assertEquals(1,pending.size());
            assertEquals(3,pending.get(0).quantity());
            repository.acknowledgeDeliveries(owner,List.of(pending.get(0).id()));
            assertTrue(repository.pendingDeliveries(owner).isEmpty());
        }
    }

    @Test void rejectsPayoutThatDoesNotMatchUnitPrice() throws Exception {
        UUID owner=UUID.randomUUID(), seller=UUID.randomUUID();
        try(OrderRepository repository=new OrderRepository(tempDir.resolve("orders.db").toFile())) {
            long id=repository.create(owner,"DIAMOND",10,new BigDecimal("2.00"),new BigDecimal("20.00"),System.currentTimeMillis()+60_000);
            assertFalse(repository.fulfill(id,seller,3,new BigDecimal("5.00")));
            assertEquals(10,repository.get(id).orElseThrow().remainingQuantity());
            assertTrue(repository.pendingDeliveries(owner).isEmpty());
        }
    }

    @Test void rejectsEscrowMismatchAtCreation() throws Exception {
        try(OrderRepository repository=new OrderRepository(tempDir.resolve("orders.db").toFile())) {
            assertThrows(IllegalArgumentException.class,()->repository.create(UUID.randomUUID(),"DIAMOND",10,new BigDecimal("2.00"),new BigDecimal("19.00"),System.currentTimeMillis()+60_000));
        }
    }
}
