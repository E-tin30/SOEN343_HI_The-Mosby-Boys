package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/* Saves and loads orders. The state is stored as text and turned back into a state object on load. */
public interface OrderRepo extends JpaRepository<OrderEntity, UUID>  {
    List<OrderEntity> findByRestaurantIdAndStateInOrderByCreatedAtAsc(UUID restaurantId, Collection<String> states);
}
