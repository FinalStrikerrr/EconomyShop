package com.avenoxstudio.economyshop.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BuyOrder(long id, UUID owner, String material, long originalQuantity, long remainingQuantity,
                       BigDecimal unitPrice, BigDecimal escrow, long createdAt, long expiresAt, String status) {}
