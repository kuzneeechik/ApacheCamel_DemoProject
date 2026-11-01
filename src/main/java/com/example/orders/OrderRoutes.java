package com.example.orders;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class OrderRoutes extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(OrderRoutes.class);
    private final WarehouseReservation warehouse;
    private final List<OrderOutcome> manualReview = new ArrayList<>();
    private final List<OrderOutcome> confirmed = new ArrayList<>();

    public OrderRoutes(WarehouseReservation warehouse) {
        this.warehouse = warehouse;
    }

    public List<OrderOutcome> manualReview() { return List.copyOf(manualReview); }
    public List<OrderOutcome> confirmed() { return List.copyOf(confirmed); }

    @Override
    public void configure() {
        from("direct:priority")
            .routeId("priority-orders")
            .errorHandler(defaultErrorHandler()
                .maximumRedeliveries(2)
                .redeliveryDelay(0)
                .onRedelivery(this::logRetry))
            .process(this::validate)
            .process(warehouse)
            .process(this::confirm);

        from("direct:standard")
            .routeId("standard-orders")
            .errorHandler(deadLetterChannel("direct:manual-review")
                .maximumRedeliveries(2)
                .redeliveryDelay(0)
                .onRedelivery(this::logRetry)
                .onPrepareFailure(e -> LOG.info("Sending order={} to manual review",
                    e.getMessage().getBody(Order.class).id())))
            .process(this::validate)
            .process(warehouse)
            .process(this::confirm);

        from("direct:manual-review")
            .routeId("manual-review")
            .process(e -> {
                Order order = e.getMessage().getBody(Order.class);
                Exception cause = e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                OrderOutcome outcome = new OrderOutcome(order.id(), "MANUAL_REVIEW",
                    cause == null ? "Unknown failure" : cause.getMessage());
                manualReview.add(outcome);
                e.getMessage().setBody(outcome);
                LOG.info("Order={} queued for manual review", order.id());
            });
    }

    private void logRetry(Exchange e) {
        LOG.info("Retry: order={}, #{}", e.getMessage().getBody(Order.class).id(),
            e.getMessage().getHeader(Exchange.REDELIVERY_COUNTER));
    }

    private void validate(Exchange exchange) {
        Order order = exchange.getMessage().getBody(Order.class);
        if (order == null) throw new IllegalArgumentException("Missing order");
        LOG.debug("Validated order={}, type={}, quantity={}", order.id(), order.type(), order.quantity());
    }

    private void confirm(Exchange exchange) {
        Order order = exchange.getMessage().getBody(Order.class);
        OrderOutcome outcome = new OrderOutcome(order.id(), "CONFIRMED",
            "Reserved stock: " + exchange.getProperty(WarehouseReservation.RESERVATION_ID));
        confirmed.add(outcome);
        exchange.getMessage().setBody(outcome);
        LOG.info("Order={} confirmed", order.id());
    }
}
