package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.technical.repository.CourierRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CourierEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepo courierRepo;

    public List<CourierData> getOnlineCouriers() {
        return courierRepo.findByStateNot("OFFLINE").stream().map(this::toCourierData).toList();
    }

    private CourierData toCourierData(CourierEntity courier) {
        return new CourierData(courier.getId(), courier.getState(),courier.getLatitude(),courier.getLongitude());
    }
}


