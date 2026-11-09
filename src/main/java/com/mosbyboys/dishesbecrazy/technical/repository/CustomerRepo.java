package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepo extends JpaRepository<CustomerEntity, UUID> {
}
