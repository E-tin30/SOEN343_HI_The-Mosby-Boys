package com.mosbyboys.dishesbecrazy.ui.controller;

import com.mosbyboys.dishesbecrazy.application.dash.DashboardService;
import com.mosbyboys.dishesbecrazy.application.dash.RestaurantDashboardData;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/restaurants")
    public List<RestaurantDashboardData> getRestaurants() {
        return dashboardService.getRestaurants();
    }
}
