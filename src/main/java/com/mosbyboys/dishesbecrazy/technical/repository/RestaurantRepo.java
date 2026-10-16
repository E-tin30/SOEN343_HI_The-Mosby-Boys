package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/* Saves and loads restaurants. A restaurant's menu items are loaded through Restaurant.getMenu(). */
public interface RestaurantRepo extends JpaRepository<Restaurant, UUID> {
    List<Restaurant> findByOwnerId(UUID ownerId); // todo: this can save us from needing owner.restoID
    List<Restaurant> findByStatus(RestaurantStatus status);
}
