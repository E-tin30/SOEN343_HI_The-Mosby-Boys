package com.mosbyboys.dishesbecrazy.ui.controller;

import com.mosbyboys.dishesbecrazy.application.prc.PRCFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final StripeClient stripeClient;
    private final PRCFacade prcFacade;

    public PaymentController(@Value("${stripe.secret-key}") String secretKey, PRCFacade prcFacade) {
        this.stripeClient = new StripeClient(secretKey);
        this.prcFacade = prcFacade;
    }

    @PostMapping("/create-payment-intent")
    public Map<String, Object> createPaymentIntent(@RequestBody PlaceOrderRequest request) throws Exception {
        //get order line items from the frontend
        List<OrderLine> orderLines = request.items().stream().map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price(), item.menuItemName())).toList();

        //calculate the amount
        PricingBreakdown pricingBreakdown = prcFacade.getTotal(orderLines);
        long total = pricingBreakdown.total().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        if (total < 50) {
            throw new IllegalArgumentException("Payment must be at least $0.50 CAD");
        }

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder().setAmount(total).setCurrency("cad").addPaymentMethodType("card").build();

        PaymentIntent intent = stripeClient.v1().paymentIntents().create(params);

        return Map.of("clientSecret", intent.getClientSecret());
    }

    @PostMapping("/breakdown")
    public PricingBreakdown getBreakdown(
            @RequestBody PlaceOrderRequest request) {

        // Convert request items into OrderLine objects
        List<OrderLine> orderLines = request.items().stream().map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price(), item.menuItemName())).toList();

        return prcFacade.getTotal(orderLines);
    }

}