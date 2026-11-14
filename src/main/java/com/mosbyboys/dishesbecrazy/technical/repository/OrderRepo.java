package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* Saves and loads orders. The state is stored as text and turned back into a state object on load. */
public interface OrderRepo extends JpaRepository<OrderEntity, UUID>  {
    List<OrderEntity> findByRestaurantIdAndStateInOrderByCreatedAtAsc(UUID restaurantId, Collection<String> states);
    List<OrderEntity> findByStateInOrderByCreatedAtAsc(Collection<String> states);
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<OrderEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);
    List<OrderEntity> findByCourierIdAndStateInOrderByCreatedAtDesc(UUID courierId, Collection<String> states);
    List<OrderEntity> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
}
