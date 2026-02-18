package com.trading.domain.model;

/**
 * Order types supported by the matching engine.
 * <p>
 * For Iteration 1, only LIMIT orders are fully implemented.
 * Other types will be added in Iteration 10.
 */
public enum OrderType {
    /** Standard limit order with specified price (Iteration 1) */
    LIMIT,

    /** Execute immediately at best available price (TODO: Iteration 10) */
    MARKET,

    /** Trigger order when price reaches stop price (TODO: Iteration 10) */
    STOP_LOSS,

    /** Combination of stop trigger and limit execution (TODO: Iteration 10) */
    STOP_LIMIT,

    /** Fill-or-kill: execute completely or cancel (TODO: Iteration 10) */
    FOK,

    /** Immediate-or-cancel: fill what's possible, cancel rest (TODO: Iteration 10) */
    IOC
}
