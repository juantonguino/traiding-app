package com.example.tradingbot.domain.service

import com.example.tradingbot.domain.model.TradeResult
import com.example.tradingbot.domain.model.TradeResultComputation
import com.example.tradingbot.domain.valueobject.Money
import com.example.tradingbot.domain.valueobject.MoneyScale
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Quantity
import java.math.BigDecimal
import java.math.RoundingMode

class TradeResultCalculator(
    private val feePercent: BigDecimal,
    private val slippagePercent: BigDecimal,
) {
    init {
        require(feePercent.signum() >= 0) { "feePercent must not be negative" }
        require(slippagePercent.signum() >= 0) { "slippagePercent must not be negative" }
    }

    fun compute(entryPrice: Price, exitPrice: Price, quantity: Quantity): TradeResultComputation {
        val entryNotional = entryPrice.value.multiply(quantity.value)
        val exitNotional = exitPrice.value.multiply(quantity.value)
        val grossPnl = exitPrice.value.subtract(entryPrice.value).multiply(quantity.value)

        val entryNotionalScaled = entryNotional.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
        val exitNotionalScaled = exitNotional.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)

        val entryFees = entryNotionalScaled.multiply(feePercent)
        val exitFees = exitNotionalScaled.multiply(feePercent)
        val fees = entryFees.add(exitFees)

        val slippageCost = entryNotionalScaled.add(exitNotionalScaled).multiply(slippagePercent)

        val netPnl = grossPnl.subtract(fees).subtract(slippageCost)

        val returnPct = if (entryNotionalScaled.signum() == 0) {
            BigDecimal.ZERO
        } else {
            netPnl.divide(entryNotionalScaled, 4, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        }

        val result = when {
            netPnl.signum() > 0 -> TradeResult.WIN
            netPnl.signum() < 0 -> TradeResult.LOSS
            else -> TradeResult.BREAK_EVEN
        }

        return TradeResultComputation(
            entryNotional = Money(entryNotionalScaled),
            exitNotional = Money(exitNotionalScaled),
            grossPnl = Money(grossPnl.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)),
            fees = Money(fees.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)),
            slippageCost = Money(slippageCost.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)),
            netPnl = Money(netPnl.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)),
            returnPct = returnPct,
            result = result,
        )
    }

    fun quantityForNotional(notional: BigDecimal, entryPrice: Price): Quantity {
        require(entryPrice.value.signum() > 0) { "entryPrice must be positive" }
        val raw = notional.divide(entryPrice.value, MoneyScale.SCALE, RoundingMode.DOWN)
        require(raw.signum() > 0) { "Computed quantity must be positive for notional $notional at $entryPrice" }
        return Quantity(raw)
    }
}
