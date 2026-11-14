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
) {

    public static class Builder {

        private BigDecimal subtotal;
        private BigDecimal deliveryFee;
        private BigDecimal serviceFee;
        private BigDecimal smallOrderSurcharge;
        private BigDecimal taxableSubtotal;
        private BigDecimal gst;
        private BigDecimal qst;
        private BigDecimal total;

        public Builder subtotal(BigDecimal value) {
            this.subtotal = value;
            return this;
        }

        public Builder deliveryFee(BigDecimal value) {
            this.deliveryFee = value;
            return this;
        }

        public Builder serviceFee(BigDecimal value) {
            this.serviceFee = value;
            return this;
        }

        public Builder smallOrderSurcharge(BigDecimal value) {
            this.smallOrderSurcharge = value;
            return this;
        }

        public Builder taxableSubtotal(BigDecimal value) {
            this.taxableSubtotal = value;
            return this;
        }

        public Builder gst(BigDecimal value) {
            this.gst = value;
            return this;
        }

        public Builder qst(BigDecimal value) {
            this.qst = value;
            return this;
        }

        public Builder total(BigDecimal value) {
            this.total = value;
            return this;
        }

        public PricingBreakdown build() {
            return new PricingBreakdown(
                    subtotal,
                    deliveryFee,
                    serviceFee,
                    smallOrderSurcharge,
                    taxableSubtotal,
                    gst,
                    qst,
                    total
            );
        }
    }
}

