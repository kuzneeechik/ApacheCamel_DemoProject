package com.example.orders;

public record Order(String id, String type, int quantity) {
    public Order {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Order ID is required");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Order type is required");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }
}
