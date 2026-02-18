package com.trading.domain.model;

/**
 * Order side representing the direction of a trade.
 * <p>
 * BUY orders are placed on the bid side of the order book.
 * SELL orders are placed on the ask side of the order book.
 */
public enum Side {
    /** Order to purchase an asset */
    BUY,

    /** Order to sell an asset */
    SELL
}
