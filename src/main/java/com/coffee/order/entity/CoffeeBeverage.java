package com.coffee.order.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "coffee_beverage")
public class CoffeeBeverage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private double basePrice;

    @Column(length = 200)
    private String description;

    public CoffeeBeverage() {}

    public CoffeeBeverage(String name, String type, double basePrice, String description) {
        this.name = name;
        this.type = type;
        this.basePrice = basePrice;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
