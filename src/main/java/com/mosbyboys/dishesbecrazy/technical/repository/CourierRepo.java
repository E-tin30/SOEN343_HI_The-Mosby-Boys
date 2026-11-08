package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.CourierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierRepo extends JpaRepository<CourierEntity, UUID> {
    
    List<CourierEntity> findByStateNot(String state);
    
    List<CourierEntity> findByState(String state);
}
