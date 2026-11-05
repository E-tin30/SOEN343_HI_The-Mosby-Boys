export interface PricingBreakdown {
    subtotal: number;
    deliveryFee: number;
    serviceFee: number;
    smallOrderSurcharge: number;
    taxableSubtotal: number;
    gst: number;
    qst: number;
    total: number;
}