package com.mosbyboys.dishesbecrazy.application.prc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "pricing")
@Getter
public class PricingConfiguration {

    private BigDecimal deliveryFee;
    private BigDecimal serviceFeeRate;
    private BigDecimal smallOrderThreshold;
    private BigDecimal smallOrderSurcharge;
    private BigDecimal gst;
    private BigDecimal qst;

}
