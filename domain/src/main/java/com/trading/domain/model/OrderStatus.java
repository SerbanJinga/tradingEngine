package com.trading.domain.model;

/**
 * Lifecycle states of an order.
 * <p>
 * Orders transition through these states:
 * OPEN → PARTIALLY_FILLED → FILLED (normal flow)
 * OPEN → CANCELLED (user cancellation)
 * PARTIALLY_FILLED → CANCELLED (cancellation after partial fill)
 */
public enum OrderStatus {
    /** Order is active in the book, not yet matched */
    OPEN,

    /** Order has been partially matched, with remaining quantity in book */
    PARTIALLY_FILLED,

    /** Order has been completely matched and removed from book */
    FILLED,

    /** Order has been cancelled by user and removed from book */
    CANCELLED
}
