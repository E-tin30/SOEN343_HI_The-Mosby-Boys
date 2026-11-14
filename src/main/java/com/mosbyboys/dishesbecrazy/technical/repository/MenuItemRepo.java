package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.MenuItemEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepo extends JpaRepository<MenuItemEntity, UUID> {
    List<MenuItemEntity> findByRestaurantIdOrderByNameAsc(UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItemEntity m where m.id = :id")
    Optional<MenuItemEntity> findByIdForUpdate(@Param("id") UUID id);
}