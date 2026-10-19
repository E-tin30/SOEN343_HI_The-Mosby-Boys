package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.technical.repository.MenuItemRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.MenuItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/* Menu browsing (UC-ORD-01) now, menu editing (UC-ORD-05) later.
Converts between MenuItemEntity rows and MenuItem domain objects. */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepo menuItemRepo;

    @Transactional(readOnly = true)
    public List<MenuItem> getMenu(UUID restaurantId) {
        return menuItemRepo.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuItem load(UUID menuItemId) {
        return menuItemRepo.findById(menuItemId)
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Menu item not found: " + menuItemId));
    }

    // Writes the item's current stock/status back to its row (e.g. after decreaseStock or markSoldOut)
    @Transactional
    public void save(MenuItem item) {
        MenuItemEntity entity = menuItemRepo.findById(item.getId()).orElseGet(MenuItemEntity::new);
        entity.setId(item.getId());
        entity.setRestaurantId(item.getRestaurantId());
        entity.setName(item.getName());
        entity.setPrice(item.getPrice());
        entity.setStockQuantity(item.getStockQuantity());
        entity.setStatus(item.getStatus());
        entity.setDescription(item.getDescription());
        entity.setImageUrl(item.getImageUrl());
        menuItemRepo.save(entity);
    }

    private MenuItem toDomain(MenuItemEntity e) {
        return new MenuItem(e.getId(), e.getRestaurantId(), e.getName(), e.getPrice(), e.getStockQuantity(),
                e.getStatus(), e.getDescription(), e.getImageUrl(), e.getCreatedAt());
    }
}
