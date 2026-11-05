package com.mosbyboys.dishesbecrazy.domain.prc;

import java.math.BigDecimal;

public record PricingBreakdown(
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal serviceFee,
    BigDecimal smallOrderSurcharge,
    BigDecimal taxableSubtotal,
    BigDecimal gst,
    BigDecimal qst,
    BigDecimal total
) {}
