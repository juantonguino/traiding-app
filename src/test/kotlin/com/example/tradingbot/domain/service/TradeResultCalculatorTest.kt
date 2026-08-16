package com.example.tradingbot.domain.service

import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.support.price
import com.example.tradingbot.support.qty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TradeResultCalculatorTest {

    private val calculator = TradeResultCalculator(
        feePercent = BigDecimal("0.001"),
        slippagePercent = BigDecimal("0.001"),
    )

    @Test
    fun `winning trade computes positive net pnl with fees and slippage`() {
        val computation = calculator.compute(
            entryPrice = price("60000.0"),
            exitPrice = price("66000.0"),
            quantity = qty("0.00166667"),
        )

        assertThat(computation.result).isEqualTo(TradeResult.WIN)
        assertThat(computation.grossPnl.value).isEqualByComparingTo("10.00002000")
        assertThat(computation.fees.value).isEqualByComparingTo("0.21000042")
        assertThat(computation.slippageCost.value).isEqualByComparingTo("0.21000042")
        assertThat(computation.netPnl.value).isPositive()
        assertThat(computation.returnPct).isGreaterThan(BigDecimal.ZERO)
    }

    @Test
    fun `losing trade computes negative net pnl`() {
        val computation = calculator.compute(
            entryPrice = price("60000.0"),
            exitPrice = price("50000.0"),
            quantity = qty("0.00166667"),
        )

        assertThat(computation.result).isEqualTo(TradeResult.LOSS)
        assertThat(computation.grossPnl.value).isNegative()
        assertThat(computation.netPnl.value).isNegative()
        assertThat(computation.returnPct).isNegative()
    }

    @Test
    fun `flat trade is break even once costs are deducted`() {
        val computation = calculator.compute(
            entryPrice = price("60000.0"),
            exitPrice = price("60000.0"),
            quantity = qty("0.00166667"),
        )

        assertThat(computation.result).isEqualTo(TradeResult.LOSS)
        assertThat(computation.netPnl.value).isNegative()
    }

    @Test
    fun `quantity for notional divides by entry price`() {
        val quantity = calculator.quantityForNotional(BigDecimal("100.0"), price("50000.0"))
        assertThat(quantity.value).isEqualByComparingTo("0.00200000")
    }

    @Test
    fun `rejects negative fee and slippage percentages`() {
        assertThat(
            runCatching { TradeResultCalculator(BigDecimal("-0.01"), BigDecimal("0.001")) }.isFailure,
        ).isTrue()
    }
}
