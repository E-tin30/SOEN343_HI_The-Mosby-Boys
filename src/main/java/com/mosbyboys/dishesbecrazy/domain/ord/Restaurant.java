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
    @GeneratedValue(strategy = GenerationType.UUID) // not certain this will mesh well yet
    private UUID id;

    @Column(name = "created_at")
    private Instant createdAt; 
    private String name;

    // should this be derived and manual? e.g. if all menu items are sold out, then the restaurant is paused
    @Enumerated(EnumType.STRING)          // stores "OPEN" / "PAUSED" as text
    @Column(nullable = false, length = 20) // do i not need to include the name of the collumn if same as attrib name?
    private RestaurantStatus status; 

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "restaurant_id")   // the FK column in "MenuItems" that points back here
    private List<MenuItem> menu = new ArrayList<>();

    @Embedded
    private Location location;    

    @Column(name = "user_id")
    private UUID ownerId; // foreign key to the user that owns this restaurant

    public void pause() {
        this.status = RestaurantStatus.PAUSED;
    }

    public void open() { 
        this.status = RestaurantStatus.OPEN;
    }

    public void close() {
        this.status = RestaurantStatus.CLOSED; // diff between closed and paused?
    }

    public boolean isOpen() {
        return this.status == RestaurantStatus.OPEN;
    }
}