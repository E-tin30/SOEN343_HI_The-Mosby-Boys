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
        //delivery fee
        BigDecimal deliveryFee = pricingConfiguration.getDeliveryFee();

        //service fee rate
        BigDecimal serviceFee = subtotal.multiply(pricingConfiguration.getServiceFeeRate());

        //check and add surcharge
        BigDecimal smallOrderSurcharge;
        if(subtotal.compareTo(pricingConfiguration.getSmallOrderThreshold()) < 0) {
            smallOrderSurcharge = pricingConfiguration.getSmallOrderSurcharge();
        } else {
            smallOrderSurcharge = BigDecimal.ZERO;
        }

        //new subtotal (taxable subtotal)
        BigDecimal taxableSubtotal = subtotal.add(deliveryFee).add(serviceFee).add(smallOrderSurcharge);

        //add tax
        BigDecimal gst = subtotal.multiply(pricingConfiguration.getGst());
        BigDecimal qst  = subtotal.multiply(pricingConfiguration.getQst());

        //final total
        BigDecimal total = taxableSubtotal.add(gst).add(qst);

        return new PricingBreakdown(subtotal, deliveryFee, serviceFee, smallOrderSurcharge, taxableSubtotal, gst, qst, total);
    }
}
