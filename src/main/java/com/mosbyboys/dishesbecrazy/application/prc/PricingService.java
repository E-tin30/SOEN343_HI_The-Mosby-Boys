package com.mosbyboys.dishesbecrazy.application.prc;

import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PricingService {

    private final PricingConfiguration pricingConfiguration;

    //constructor dependency injection
    public PricingService(PricingConfiguration pricingConfiguration) {
        this.pricingConfiguration = pricingConfiguration;
    }

    public PricingBreakdown calculateTotal(BigDecimal subtotal) {
        // Delivery fee
        BigDecimal deliveryFee = pricingConfiguration.getDeliveryFee();

        // Service fee rate
        BigDecimal serviceFee = subtotal.multiply(
                pricingConfiguration.getServiceFeeRate()
        );

        // Check and add surcharge
        BigDecimal smallOrderSurcharge;

        if (subtotal.compareTo(
                pricingConfiguration.getSmallOrderThreshold()) < 0) {
            smallOrderSurcharge = pricingConfiguration.getSmallOrderSurcharge();
        } else {
            smallOrderSurcharge = BigDecimal.ZERO;
        }

        // New subtotal (taxable subtotal)
        BigDecimal taxableSubtotal = subtotal
                .add(deliveryFee)
                .add(serviceFee)
                .add(smallOrderSurcharge);

        // Add tax
        BigDecimal gst = taxableSubtotal.multiply(
                pricingConfiguration.getGst()
        );

        BigDecimal qst = taxableSubtotal.multiply(
                pricingConfiguration.getQst()
        );

        // Final total
        BigDecimal total = taxableSubtotal.add(gst).add(qst);

        // Build pricing breakdown
        return new PricingBreakdown.Builder()
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .serviceFee(serviceFee)
                .smallOrderSurcharge(smallOrderSurcharge)
                .taxableSubtotal(taxableSubtotal)
                .gst(gst)
                .qst(qst)
                .total(total)
                .build();
    }
}
