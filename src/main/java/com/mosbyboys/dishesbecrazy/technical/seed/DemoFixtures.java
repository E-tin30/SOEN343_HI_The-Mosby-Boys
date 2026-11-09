package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

public record DemoFixtures(List<Account> accounts, List<MenuSeed> menuItems) {
    public DemoFixtures {
        accounts = List.copyOf(accounts);
        menuItems = List.copyOf(menuItems);
    }
    // Keeps a validated fixture snapshot unchanged while seeders or restoration consume it.

    public record Account(String email, String firstName, String lastName, String username,
                          Role role, String address, String state, String location,
                          String restaurantName, String status, Double latitude, Double longitude) {
        public Account {
            email = email == null ? null : email.toLowerCase(Locale.ROOT);
        }
        // Validation, provisioning and account reuse all receive Supabase's lowercase identity spelling.
    }
    // Preserves the existing profile JSON fields; passwords remain in environment configuration.

    public record MenuSeed(UUID id, String restaurantUsername, String name, BigDecimal price,
                           int stockQuantity, MenuItemStatus status, String description) {
    }
}
