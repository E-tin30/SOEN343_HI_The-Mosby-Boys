package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.RestaurantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/* Saves and loads restaurant rows. */
public interface RestaurantRepo extends JpaRepository<RestaurantEntity, UUID> {
    List<RestaurantEntity> findByOwnerId(UUID ownerId);
    List<RestaurantEntity> findByStatus(RestaurantStatus status);
}
