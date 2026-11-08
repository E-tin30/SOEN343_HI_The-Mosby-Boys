
package com.mosbyboys.dishesbecrazy.application.events;

import java.util.UUID;

public record OrderOfferedEvent(UUID orderId, UUID courierId) {
}
