package com.example.orders;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        WarehouseReservation warehouse = new WarehouseReservation(Map.of(
                "S-102", 2,
                "S-103", 10,
                "P-201", 2,
                "P-202", 10));
        OrderRoutes routes = new OrderRoutes(warehouse);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addRoutes(routes);
            context.start();

            ProducerTemplate producer = context.createProducerTemplate();

            try {
                run(producer, "Standard order - success", "direct:standard", new Order("S-101", "LAPTOP", 1));
                run(producer, "Standard order - recovered", "direct:standard", new Order("S-102", "MOUSE", 2));
                run(producer, "Standard order - review", "direct:standard", new Order("S-103", "MONITOR", 1));
                run(producer, "Priority order - recovered", "direct:priority", new Order("P-201", "SERVER", 1));
                run(producer, "Priority order - failure", "direct:priority", new Order("P-202", "GPU", 3));
                LOG.info("Summary: confirmed={}, manualReview={}",
                    routes.confirmed().size(), routes.manualReview().size());
            } finally {
                producer.stop();
            }
        }
    }

    private static void run(ProducerTemplate producer, String label, String endpoint, Order order) {
        LOG.info("--- {} (order={}) ---", label, order.id());
        try {
            OrderOutcome result = producer.requestBody(endpoint, order, OrderOutcome.class);
            LOG.info("Result: order={}, status={}", result.orderId(), result.status());
        } catch (CamelExecutionException e) {
            LOG.warn("Result: order={}, status=FAILED, reason={}", order.id(),
                e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }
}
