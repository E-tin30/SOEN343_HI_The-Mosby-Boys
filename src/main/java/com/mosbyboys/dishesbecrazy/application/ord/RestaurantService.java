package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.technical.repository.RestaurantRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.RestaurantEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepo restaurantRepo;
    private final UserRepo userRepo;

    @Transactional(readOnly = true)
    public Restaurant load(UUID restaurantId) {
        RestaurantEntity entity = restaurantRepo.findById(restaurantId)
                .orElseThrow(() -> new NoSuchElementException("Restaurant not found: " + restaurantId));
        return toDomain(List.of(entity)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findAll() {
        return toDomain(restaurantRepo.findAll());
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findOpen() {
        return toDomain(restaurantRepo.findByStatus(RestaurantStatus.OPEN));
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getNames(Collection<UUID> restaurantIds) {
        return restaurantRepo.findAllById(restaurantIds).stream()
                .collect(Collectors.toMap(RestaurantEntity::getId, RestaurantEntity::getName));
    }

    @Transactional
    public void save(Restaurant restaurant) {
        RestaurantEntity entity = restaurantRepo.findById(restaurant.getUserId()).orElseGet(RestaurantEntity::new);
        entity.setId(restaurant.getUserId());
        entity.setName(restaurant.getName());
        entity.setStatus(restaurant.getStatus());
        if (restaurant.getLocation() != null) {
            entity.setLatitude(restaurant.getLocation().latitude());
            entity.setLongitude(restaurant.getLocation().longitude());
        }
        restaurantRepo.save(entity);
    }

    // A Restaurant is also a User, so each row is combined with its User row (one query for all of them).
    private List<Restaurant> toDomain(List<RestaurantEntity> entities) {
        Map<UUID, UserProfile> users = userRepo.findUsersByIds(entities.stream().map(RestaurantEntity::getId).toList());
        return entities.stream().map(e -> toDomain(e, users.get(e.getId()))).toList();
    }

    private Restaurant toDomain(RestaurantEntity e, UserProfile user) {
        if (user == null) {
            throw new IllegalStateException("Restaurant has no User row: " + e.getId());
        }
        Location location = e.getLatitude() == null && e.getLongitude() == null
                ? null
                : new Location(e.getLatitude(), e.getLongitude());
        OffsetDateTime createdAt = e.getCreatedAt() == null ? null : e.getCreatedAt().atOffset(ZoneOffset.UTC);
        // Email and password live in Supabase Auth, not public."User".
        return new Restaurant(e.getId(), createdAt, user.firstName(), user.lastName(), null, user.username(), null,
                e.getName(), e.getStatus(), location);
    }
}
