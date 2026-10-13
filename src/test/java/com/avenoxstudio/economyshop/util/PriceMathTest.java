package com.avenoxstudio.economyshop.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PriceMathTest {
    @Test void bulkTotalUsesDecimalArithmetic() {
        assertEquals(new BigDecimal("960.00"), PriceMath.total(new BigDecimal("15.00"),64,2));
    }
    @Test void roundsAtConfiguredScale() {
        assertEquals(new BigDecimal("1.01"), PriceMath.total(new BigDecimal("0.335"),3,2));
    }
    @Test void escrowLimitsAffordableQuantity() {
        assertEquals(3,PriceMath.affordableQuantity(new BigDecimal("10.00"),new BigDecimal("3.00")));
    }
    @Test void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,()->PriceMath.total(BigDecimal.ONE,-1,2));
    }
}