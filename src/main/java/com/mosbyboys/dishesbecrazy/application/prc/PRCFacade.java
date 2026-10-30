package com.mosbyboys.dishesbecrazy.application.prc;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PRCFacade {

    private final PricingService pricingService;

    public PricingBreakdown getTotal(List<OrderLine> lineItems) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLine item : lineItems) {
            subtotal = subtotal.add(item.getLineTotal());
        }

        return pricingService.calculateTotal(subtotal);
    }
}
