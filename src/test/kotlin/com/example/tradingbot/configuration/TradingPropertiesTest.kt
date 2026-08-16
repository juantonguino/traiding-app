package com.example.tradingbot.configuration

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import java.math.BigDecimal

class TradingPropertiesTest {

    private fun validate(properties: TradingProperties) {
        TradingPropertiesValidator(properties).run(mockk<ApplicationArguments>(relaxed = true))
    }

    @Test
    fun `accepts the safe paper default configuration`() {
        assertThatCode { validate(TradingProperties()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `rejects non paper trading mode`() {
        val properties = TradingProperties().apply { tradingMode = "LIVE" }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("TRADING_MODE must be PAPER")
    }

    @Test
    fun `rejects max open trades other than one`() {
        val properties = TradingProperties().apply { maxOpenTrades = 2 }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("MAX_OPEN_TRADES must be 1")
    }

    @Test
    fun `rejects real order execution`() {
        val properties = TradingProperties().apply { allowRealOrders = true }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ALLOW_REAL_ORDERS must be false")
    }

    @Test
    fun `rejects empty symbol list`() {
        val properties = TradingProperties().apply { symbols = emptyList() }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("at least one TRADING_SYMBOLS")
    }

    @Test
    fun `rejects blank timeframe`() {
        val properties = TradingProperties().apply { timeframe = "  " }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("TRADING_TIMEFRAME must not be blank")
    }

    @Test
    fun `rejects negative fee percentage`() {
        val properties = TradingProperties().apply { feePercent = BigDecimal("-0.001") }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("commission and slippage percentages must not be negative")
    }

    @Test
    fun `rejects negative slippage percentage`() {
        val properties = TradingProperties().apply { slippagePercent = BigDecimal("-0.001") }

        assertThatThrownBy { validate(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("commission and slippage percentages must not be negative")
    }
}
