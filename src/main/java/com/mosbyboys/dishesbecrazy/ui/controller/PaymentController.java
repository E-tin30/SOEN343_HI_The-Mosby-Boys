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
        List<OrderLine> orderLines = request.items().stream().map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price())).toList();

        //calculate the amount
        PricingBreakdown pricingBreakdown = prcFacade.getTotal(orderLines);
        long total = pricingBreakdown.total().setScale(2, RoundingMode.HALF_UP).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder().setAmount(total).setCurrency("cad").build();

        PaymentIntent intent = stripeClient.v1().paymentIntents().create(params);

        return Map.of("clientSecret", intent.getClientSecret());
    }

}