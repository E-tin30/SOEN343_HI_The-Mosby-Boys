package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

public record DemoFixtures(List<Account> accounts, List<MenuSeed> menuItems, List<OrderSeed> orders) {
    public DemoFixtures {
        accounts = List.copyOf(accounts);
        menuItems = List.copyOf(menuItems);
        orders = List.copyOf(orders);
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

    public record OrderSeed(UUID id, String customerUsername, String restaurantUsername,
                            String courierUsername, String state, Instant placedAt, BigDecimal subtotal,
                            String rejectionReason, List<LineSeed> lines, List<EventSeed> events) {
        public OrderSeed {
            lines = lines == null ? null : List.copyOf(lines);
            events = events == null ? null : List.copyOf(events);
        }
    }
    // A fixed order snapshot includes its lines and history, so their order relationship is unambiguous.

    public record LineSeed(UUID id, UUID menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
    }
    // Stores the purchased name and price independently from later menu changes.

    public record EventSeed(UUID id, String action, String previousState, String newState,
                            Instant createdAt, String performedByUsername) {
    }
    // Usernames resolve to established Auth identities when persistence is implemented later.
}
