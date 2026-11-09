package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.technical.repository.CourierRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.CustomerRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.OrderRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CourierEntity;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CustomerEntity;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEntity;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;


import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final OrderRepo orderRepo;
    private final CustomerRepo customerRepo;
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

    @Transactional
    public void moveToCustomerLocation(UUID courierId, UUID customerId) {

        Location destination = jdbcTemplate.queryForObject(
            "SELECT latitude, longitude FROM \"Customer\" WHERE user_id = ?",
            (rs, rowNum) -> new Location(
                rs.getDouble("latitude"),
                rs.getDouble("longitude")
            ),
            customerId
        );

        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException("Courier not found"));

        courier.setLatitude(destination.latitude());
        courier.setLongitude(destination.longitude());

        courierRepo.save(courier);
    }

    @Transactional
    public void moveToDropoffLocation(UUID courierId, UUID orderId) {
        //Find the order
        OrderEntity order = orderRepo.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        //Find the courier
        CourierEntity courier = courierRepo.findById(courierId)
            .orElseThrow(() -> new IllegalArgumentException("Courier not found"));
        
        //Verify this courier was assigned to the order
        if (!courierId.equals(order.getCourierId())) {
            throw new IllegalStateException("Order does not belong to this courier");
        }

        //Find the customer who placed the order
        CustomerEntity customer = customerRepo.findById(order.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        //Move the courier to the customer's coordinates
        courier.setLatitude(customer.getLatitude());
        courier.setLongitude(customer.getLongitude());

        courierRepo.save(courier);
    }

}


