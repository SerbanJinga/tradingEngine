package com.trading.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrder() {
        Order order = Order.create(
            OrderId.generate(),
            UserId.of("user-1"),
            Symbol.of("BTC-USD"),
            Side.BUY,
            OrderType.LIMIT,
            new BigDecimal("50000"),
            new BigDecimal("1.5")
        );

        assertThat(order.price()).isEqualByComparingTo("50000");
        assertThat(order.quantity()).isEqualByComparingTo("1.5");
        assertThat(order.filledQuantity()).isEqualByComparingTo("0");
        assertThat(order.getRemainingQuantity()).isEqualByComparingTo("1.5");
        assertThat(order.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.isOpen()).isTrue();
        assertThat(order.isFilled()).isFalse();
    }

    @Test
    void shouldUpdateFilledQuantityToPartial() {
        Order order = createTestOrder("1.0");

        Order updated = order.withFilledQuantity(new BigDecimal("0.5"));

        assertThat(updated.filledQuantity()).isEqualByComparingTo("0.5");
        assertThat(updated.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(updated.getRemainingQuantity()).isEqualByComparingTo("0.5");
        assertThat(updated.isOpen()).isTrue();
    }

    @Test
    void shouldUpdateFilledQuantityToFilled() {
        Order order = createTestOrder("1.0");

        Order updated = order.withFilledQuantity(new BigDecimal("1.0"));

        assertThat(updated.filledQuantity()).isEqualByComparingTo("1.0");
        assertThat(updated.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(updated.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(updated.isFilled()).isTrue();
        assertThat(updated.isOpen()).isFalse();
    }

    @Test
    void shouldCancelOrder() {
        Order order = createTestOrder("1.0");

        Order cancelled = order.withCancelledStatus();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.isOpen()).isFalse();
    }

    @Test
    void shouldRejectNegativePrice() {
        assertThatThrownBy(() ->
            Order.create(
                OrderId.generate(),
                UserId.of("user-1"),
                Symbol.of("BTC-USD"),
                Side.BUY,
                OrderType.LIMIT,
                new BigDecimal("-100"),
                new BigDecimal("1")
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Price must be positive");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() ->
            Order.create(
                OrderId.generate(),
                UserId.of("user-1"),
                Symbol.of("BTC-USD"),
                Side.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000"),
                new BigDecimal("-1")
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Quantity must be positive");
    }

    @Test
    void shouldRejectFilledQuantityExceedingTotal() {
        assertThatThrownBy(() ->
            new Order(
                OrderId.generate(),
                UserId.of("user-1"),
                Symbol.of("BTC-USD"),
                Side.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000"),
                new BigDecimal("1.0"),
                new BigDecimal("1.5"),  // Filled > Total
                OrderStatus.OPEN,
                java.time.Instant.now()
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("cannot exceed");
    }

    // Helper
    private Order createTestOrder(String quantity) {
        return Order.create(
            OrderId.generate(),
            UserId.of("user-1"),
            Symbol.of("BTC-USD"),
            Side.BUY,
            OrderType.LIMIT,
            new BigDecimal("50000"),
            new BigDecimal(quantity)
        );
    }
}
