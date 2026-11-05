package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepo extends JpaRepository<MenuItemEntity, UUID> {
    List<MenuItemEntity> findByRestaurantIdOrderByNameAsc(UUID restaurantId);
}
