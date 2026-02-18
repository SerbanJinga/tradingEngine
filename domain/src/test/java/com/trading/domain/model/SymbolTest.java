package com.trading.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SymbolTest {

    @Test
    void shouldCreateValidSymbol() {
        Symbol symbol = Symbol.of("BTC-USD");

        assertThat(symbol.value()).isEqualTo("BTC-USD");
    }

    @Test
    void shouldExtractBaseAndQuote() {
        Symbol symbol = Symbol.of("BTC-USD");

        assertThat(symbol.getBase()).isEqualTo("BTC");
        assertThat(symbol.getQuote()).isEqualTo("USD");
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> Symbol.of("BTCUSD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be in format BASE-QUOTE");
    }

    @Test
    void shouldRejectLowercaseSymbol() {
        assertThatThrownBy(() -> Symbol.of("btc-usd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be in format BASE-QUOTE");
    }

    @Test
    void shouldRejectEmptySymbol() {
        assertThatThrownBy(() -> Symbol.of(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }

    @Test
    void shouldRejectNullSymbol() {
        assertThatThrownBy(() -> Symbol.of(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or blank");
    }
}
