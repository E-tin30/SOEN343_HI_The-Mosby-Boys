package com.mosbyboys.dishesbecrazy.domain.ord;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.*;
import lombok.*;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;


@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "\"Restaurant\"")
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) 
    private UUID id;

    @Column(name = "created_at")
    private Instant createdAt; 
    private String name;

    @Enumerated(EnumType.STRING)        
    @Column(nullable = false, length = 20) 
    private RestaurantStatus status; 

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "restaurant_id")   
    private List<MenuItem> menu = new ArrayList<>();

    @Embedded
    private Location location;    

    @Column(name = "user_id")
    private UUID ownerId; 

    public void pause() {
        this.status = RestaurantStatus.PAUSED;
    }

    public void open() { 
        this.status = RestaurantStatus.OPEN;
    }

    public void close() {
        this.status = RestaurantStatus.CLOSED; 
    }

    public boolean isOpen() {
        return this.status == RestaurantStatus.OPEN;
    }
}