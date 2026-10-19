package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/* Saves and loads menu items. Used to look up current prices when an order is placed. */
public interface MenuItemRepo extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByStatus(MenuItemStatus status);
}
