package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/* Saves and loads menu item rows. A restaurant's menu is every row with its restaurant_id. */
public interface MenuItemRepo extends JpaRepository<MenuItemEntity, UUID> {
    List<MenuItemEntity> findByRestaurantIdOrderByNameAsc(UUID restaurantId);
}
