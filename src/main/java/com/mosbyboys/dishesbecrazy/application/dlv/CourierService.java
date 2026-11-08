package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.technical.repository.CourierRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CourierEntity;
import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepo courierRepo;

    public List<CourierData> getOnlineCouriers() {
        return courierRepo.findByStateNot("OFFLINE").stream().map(this::toCourierData).toList();
    }
    
    public List<CourierData> getIdleCouriers() {
        return courierRepo.findByState("IDLE").stream().map(this::toCourierData).toList();
    }

    private CourierData toCourierData(CourierEntity courier) {
        return new CourierData(courier.getId(), courier.getState(),courier.getLatitude(),courier.getLongitude());
    }
    
    //Changes a courier to IDLE when going on duty OR after completing a delivery.
    @Transactional
    public void markIdle(UUID courierId) {

        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException("Courier not found: " + courierId)
            );

        String state = courier.getState();

        if (!"OFFLINE".equalsIgnoreCase(state)
                && !"CARRYING".equalsIgnoreCase(state)) {
            throw new IllegalStateException("Courier must be OFFLINE or CARRYING to become IDLE");
        }

        courier.setState("IDLE");
        courierRepo.save(courier);
    }

    
    //Changes a courier from IDLE to ASSIGNED when accepting an offer.
    @Transactional
    public void markAssigned(UUID courierId) {

        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException("Courier not found: " + courierId));

        if (!"IDLE".equalsIgnoreCase(courier.getState())) {
            throw new IllegalStateException("Courier must be IDLE to accept an offer");
        }

        courier.setState("ASSIGNED");
        courierRepo.save(courier);
    }


    //Changes a courier from ASSIGNED to CARRYING when confirming pickup.
    @Transactional
    public void markCarrying(UUID courierId) {

        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException("Courier not found: " + courierId));

        if (!"ASSIGNED".equalsIgnoreCase(courier.getState())) {
            throw new IllegalStateException("Courier must be ASSIGNED to pick up an order");
        }

        courier.setState("CARRYING");
        courierRepo.save(courier);
    }

    //Changes a courier from IDLE to OFFLINE.
    //Pending offer rejection is handled by DispatchService.
    @Transactional
    public void markOffline(UUID courierId) {

        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Courier not found: " + courierId
            ));

        if (!"IDLE".equalsIgnoreCase(courier.getState())) {
            throw new IllegalStateException(
                "Only IDLE couriers can go OFFLINE"
            );
        }

        courier.setState("OFFLINE");
        courierRepo.save(courier);
    }


}


