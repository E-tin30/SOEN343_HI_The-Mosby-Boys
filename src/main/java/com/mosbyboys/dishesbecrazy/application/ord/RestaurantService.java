package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.technical.repository.RestaurantRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.RestaurantEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/* Loads restaurants and converts between RestaurantEntity rows and Restaurant domain objects. */
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepo restaurantRepo;

    @Transactional(readOnly = true)
    public Restaurant load(UUID restaurantId) {
        return restaurantRepo.findById(restaurantId)
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Restaurant not found: " + restaurantId));
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findAll() {
        return restaurantRepo.findAll().stream().map(this::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findOpen() {
        return restaurantRepo.findByStatus(RestaurantStatus.OPEN).stream().map(this::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findByOwner(UUID ownerId) {
        return restaurantRepo.findByOwnerId(ownerId).stream().map(this::toDomain).toList();
    }

    // Writes the restaurant's current state back to its row (e.g. after pause/reopen)
    @Transactional
    public void save(Restaurant restaurant) {
        RestaurantEntity entity = restaurantRepo.findById(restaurant.getId()).orElseGet(RestaurantEntity::new);
        entity.setId(restaurant.getId());
        entity.setOwnerId(restaurant.getOwnerId());
        entity.setName(restaurant.getName());
        entity.setStatus(restaurant.getStatus());
        if (restaurant.getLocation() != null) {
            entity.setLatitude(restaurant.getLocation().latitude());
            entity.setLongitude(restaurant.getLocation().longitude());
        }
        restaurantRepo.save(entity);
    }

    private Restaurant toDomain(RestaurantEntity e) {
        Location location = e.getLatitude() == null && e.getLongitude() == null
                ? null
                : new Location(e.getLatitude(), e.getLongitude());
        return new Restaurant(e.getId(), e.getOwnerId(), e.getName(), e.getStatus(), location, e.getCreatedAt());
    }
}
