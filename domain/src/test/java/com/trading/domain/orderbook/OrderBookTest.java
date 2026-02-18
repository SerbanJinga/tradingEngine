package com.trading.domain.orderbook;

import com.trading.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderBookTest {

    private OrderBook orderBook;
    private Symbol symbol;

    @BeforeEach
    void setUp() {
        symbol = Symbol.of("BTC-USD");
        orderBook = new OrderBook(symbol);
    }

    // ============ Basic Operations ============

    @Test
    void shouldStartEmpty() {
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.getBestBidPrice()).isEmpty();
        assertThat(orderBook.getBestAskPrice()).isEmpty();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldAddBuyOrderWithoutMatch() {
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");

        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.trades()).isEmpty();
        assertThat(result.order().isOpen()).isTrue();
        assertThat(result.order().filledQuantity()).isEqualByComparingTo("0");
        assertThat(orderBook.getBids()).hasSize(1);
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.getBestBidPrice()).contains(new BigDecimal("50000"));
        assertThat(orderBook.isEmpty()).isFalse();
    }

    @Test
    void shouldAddSellOrderWithoutMatch() {
        Order sellOrder = createLimitOrder(Side.SELL, "51000", "1.0");

        MatchResult result = orderBook.addOrder(sellOrder);

        assertThat(result.trades()).isEmpty();
        assertThat(result.order().isOpen()).isTrue();
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("51000"));
    }

    @Test
    void shouldRejectOrderWithWrongSymbol() {
        Order wrongSymbolOrder = Order.create(
            OrderId.generate(),
            UserId.of("user-1"),
            Symbol.of("ETH-USD"),  // Wrong symbol
            Side.BUY,
            OrderType.LIMIT,
            new BigDecimal("3000"),
            new BigDecimal("10")
        );

        assertThatThrownBy(() -> orderBook.addOrder(wrongSymbolOrder))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match order book symbol");
    }

    // ============ Matching - Full Fill ============

    @Test
    void shouldMatchFullyWhenPricesCross() {
        // Add SELL order at 50000
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "1.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order at 50000 (should match)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Verify trade
        assertThat(result.trades()).hasSize(1);
        Trade trade = result.trades().get(0);
        assertThat(trade.symbol()).isEqualTo(symbol);
        assertThat(trade.price()).isEqualByComparingTo("50000");
        assertThat(trade.quantity()).isEqualByComparingTo("1.0");
        assertThat(trade.makerOrderId()).isEqualTo(sellOrder.orderId());
        assertThat(trade.takerOrderId()).isEqualTo(buyOrder.orderId());
        assertThat(trade.makerSide()).isEqualTo(Side.SELL);

        // Verify order status
        assertThat(result.order().isFilled()).isTrue();
        assertThat(result.order().filledQuantity()).isEqualByComparingTo("1.0");

        // Both orders should be filled and removed from book
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getAsks()).isEmpty();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldMatchWhenBuyPriceExceedsAskPrice() {
        // Add SELL at 50000
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "1.0"));

        // Add BUY at 51000 (higher than ask, should match at 50000)
        Order buyOrder = createLimitOrder(Side.BUY, "51000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).price()).isEqualByComparingTo("50000");
        assertThat(orderBook.isEmpty()).isTrue();
    }

    // ============ Matching - Partial Fill ============

    @Test
    void shouldMatchPartiallyWhenQuantityInsufficient() {
        // Add SELL order for 1.0
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "1.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order for 2.0 (should partially match)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "2.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Verify trade
        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).quantity()).isEqualByComparingTo("1.0");

        // Verify order status
        assertThat(result.order().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.order().filledQuantity()).isEqualByComparingTo("1.0");
        assertThat(result.order().getRemainingQuantity()).isEqualByComparingTo("1.0");

        // Remaining 1.0 should be in the book
        assertThat(orderBook.getBids()).hasSize(1);
        assertThat(orderBook.getBids().get(0).getRemainingQuantity()).isEqualByComparingTo("1.0");
        assertThat(orderBook.getAsks()).isEmpty();
    }

    @Test
    void shouldPartiallyFillMakerOrder() {
        // Add SELL order for 2.0
        Order sellOrder = createLimitOrder(Side.SELL, "50000", "2.0");
        orderBook.addOrder(sellOrder);

        // Add BUY order for 1.0 (should partially fill sell order)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().isFilled()).isTrue();

        // Sell order should have 1.0 remaining
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getAsks().get(0).getRemainingQuantity()).isEqualByComparingTo("1.0");
    }

    // ============ Matching - Multiple Orders ============

    @Test
    void shouldMatchAgainstMultipleOrdersAtSamePrice() {
        // Add multiple SELL orders at same price
        Order sell1 = createLimitOrder(Side.SELL, "50000", "0.5");
        Order sell2 = createLimitOrder(Side.SELL, "50000", "0.3");
        orderBook.addOrder(sell1);
        orderBook.addOrder(sell2);

        // Add BUY order that matches both
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "0.8");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Should create 2 trades
        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).quantity()).isEqualByComparingTo("0.5");
        assertThat(result.trades().get(1).quantity()).isEqualByComparingTo("0.3");
        assertThat(result.order().isFilled()).isTrue();
        assertThat(orderBook.isEmpty()).isTrue();
    }

    @Test
    void shouldMatchAgainstMultiplePriceLevels() {
        // Add SELL orders at different prices
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "0.5"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50100", "0.5"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50200", "1.0"));

        // Add BUY order at 50100 (should match first two levels)
        Order buyOrder = createLimitOrder(Side.BUY, "50100", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        // Should match 0.5 at 50000, then 0.5 at 50100
        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).price()).isEqualByComparingTo("50000");
        assertThat(result.trades().get(1).price()).isEqualByComparingTo("50100");
        assertThat(result.order().isFilled()).isTrue();

        // Order at 50200 should remain
        assertThat(orderBook.getAsks()).hasSize(1);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("50200"));
    }

    // ============ Price-Time Priority ============

    @Test
    void shouldRespectPriceTimePriority() {
        // Add orders at same price in sequence
        Order order1 = createLimitOrder(Side.SELL, "50000", "1.0");
        Order order2 = createLimitOrder(Side.SELL, "50000", "1.0");
        Order order3 = createLimitOrder(Side.SELL, "50000", "1.0");

        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);

        // Match should execute against order1 first (FIFO)
        Order buyOrder = createLimitOrder(Side.BUY, "50000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).makerOrderId()).isEqualTo(order1.orderId());

        // Order2 and Order3 should remain
        assertThat(orderBook.getAsks()).hasSize(2);
    }

    @Test
    void shouldMatchBestPriceFirst() {
        // Add SELL orders at different prices
        orderBook.addOrder(createLimitOrder(Side.SELL, "50200", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "50100", "1.0"));

        // BUY at high price should match best (lowest) ask first
        Order buyOrder = createLimitOrder(Side.BUY, "51000", "1.0");
        MatchResult result = orderBook.addOrder(buyOrder);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).price()).isEqualByComparingTo("50000");

        // Other orders remain
        assertThat(orderBook.getAsks()).hasSize(2);
        assertThat(orderBook.getBestAskPrice()).contains(new BigDecimal("50100"));
    }

    // ============ Cancel ============

    @Test
    void shouldCancelOrder() {
        Order order = createLimitOrder(Side.BUY, "50000", "1.0");
        orderBook.addOrder(order);

        boolean cancelled = orderBook.cancelOrder(order.orderId());

        assertThat(cancelled).isTrue();
        assertThat(orderBook.getBids()).isEmpty();
        assertThat(orderBook.getOrder(order.orderId())).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenCancellingNonExistentOrder() {
        boolean cancelled = orderBook.cancelOrder(OrderId.generate());

        assertThat(cancelled).isFalse();
    }

    @Test
    void shouldCancelSpecificOrderFromMultiple() {
        Order order1 = createLimitOrder(Side.BUY, "50000", "1.0");
        Order order2 = createLimitOrder(Side.BUY, "50000", "2.0");
        Order order3 = createLimitOrder(Side.BUY, "50000", "3.0");

        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);

        orderBook.cancelOrder(order2.orderId());

        assertThat(orderBook.getBids()).hasSize(2);
        assertThat(orderBook.getOrder(order1.orderId())).isPresent();
        assertThat(orderBook.getOrder(order2.orderId())).isEmpty();
        assertThat(orderBook.getOrder(order3.orderId())).isPresent();
    }

    // ============ Query Methods ============

    @Test
    void shouldReturnBidsInCorrectOrder() {
        orderBook.addOrder(createLimitOrder(Side.BUY, "50000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.BUY, "51000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.BUY, "49000", "1.0"));

        var bids = orderBook.getBids();

        // Should be sorted by price descending (best bid first)
        assertThat(bids).hasSize(3);
        assertThat(bids.get(0).price()).isEqualByComparingTo("51000");
        assertThat(bids.get(1).price()).isEqualByComparingTo("50000");
        assertThat(bids.get(2).price()).isEqualByComparingTo("49000");
    }

    @Test
    void shouldReturnAsksInCorrectOrder() {
        orderBook.addOrder(createLimitOrder(Side.SELL, "52000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "51000", "1.0"));
        orderBook.addOrder(createLimitOrder(Side.SELL, "53000", "1.0"));

        var asks = orderBook.getAsks();

        // Should be sorted by price ascending (best ask first)
        assertThat(asks).hasSize(3);
        assertThat(asks.get(0).price()).isEqualByComparingTo("51000");
        assertThat(asks.get(1).price()).isEqualByComparingTo("52000");
        assertThat(asks.get(2).price()).isEqualByComparingTo("53000");
    }

    // ============ Helper Methods ============

    private Order createLimitOrder(Side side, String price, String quantity) {
        return Order.create(
            OrderId.generate(),
            UserId.of("user-" + System.nanoTime()),
            symbol,
            side,
            OrderType.LIMIT,
            new BigDecimal(price),
            new BigDecimal(quantity)
        );
    }
}
