package com.mosbyboys.dishesbecrazy.ui.controller;

import com.mosbyboys.dishesbecrazy.application.prc.PRCFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PRCFacade prcFacade;

    @PostMapping("/breakdown")
    public PricingBreakdown getBreakdown(
            @RequestBody PlaceOrderRequest request) {

        // Convert request items into OrderLine objects
        List<OrderLine> orderLines = request.items().stream().map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price())).toList();

        return prcFacade.getTotal(orderLines);
    }
}