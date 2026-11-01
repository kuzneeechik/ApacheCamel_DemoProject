package com.example.orders;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class WarehouseReservation implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(WarehouseReservation.class);
    public static final String ATTEMPTS = "demo.warehouseAttempts";
    public static final String RESERVATION_ID = "demo.reservationId";
    private final Map<String, Integer> failuresBeforeSuccess;

    public WarehouseReservation(Map<String, Integer> failuresBeforeSuccess) {
        this.failuresBeforeSuccess = Map.copyOf(failuresBeforeSuccess);
        if (this.failuresBeforeSuccess.values().stream().anyMatch(n -> n < 0)) {
            throw new IllegalArgumentException("Failure counts must be non-negative");
        }
    }

    @Override
    public void process(Exchange exchange) {
        Order order = exchange.getMessage().getBody(Order.class);
        Integer previous = exchange.getProperty(ATTEMPTS, Integer.class);
        int attempt = previous == null ? 1 : previous + 1;
        exchange.setProperty(ATTEMPTS, attempt);
        LOG.info("Warehouse reservation: order={}, attempt={}", order.id(), attempt);

        if (attempt <= failuresBeforeSuccess.getOrDefault(order.id(), 0)) {
            throw new IllegalStateException("Warehouse temporarily unavailable for order " + order.id());
        }
        exchange.setProperty(RESERVATION_ID, "RES-" + order.id());
    }
}
