package com.avenoxstudio.economyshop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceMath {
    private PriceMath() {}
    public static BigDecimal total(BigDecimal unitPrice, long quantity, int scale) {
        if (unitPrice == null || unitPrice.signum() < 0 || quantity < 0 || scale < 0 || scale > 8)
            throw new IllegalArgumentException("Invalid price calculation input");
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(scale, RoundingMode.HALF_UP);
    }
    public static long affordableQuantity(BigDecimal escrow, BigDecimal unitPrice) {
        if (escrow == null || unitPrice == null || escrow.signum() < 0 || unitPrice.signum() <= 0)
            return 0;
        return escrow.divide(unitPrice, 0, RoundingMode.DOWN).longValueExact();
    }
}