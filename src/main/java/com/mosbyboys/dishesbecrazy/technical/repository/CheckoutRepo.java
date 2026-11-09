package com.mosbyboys.dishesbecrazy.technical.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.CheckoutEntity;

import jakarta.persistence.LockModeType;

public interface CheckoutRepo extends JpaRepository<CheckoutEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select checkout from CheckoutEntity checkout where checkout.id = :id")
    Optional<CheckoutEntity> findForUpdate(@Param("id") UUID id);
    // Serializes completion/recovery for one checkout across requests and Stripe callbacks.
}
