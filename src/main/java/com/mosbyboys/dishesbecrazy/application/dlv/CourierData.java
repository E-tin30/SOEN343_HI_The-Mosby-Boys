package com.mosbyboys.dishesbecrazy.application.dlv;

import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

public record CourierData(UUID id, CourierState state, double latitude, double longitude) {
}
