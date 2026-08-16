package com.example.tradingbot.domain.valueobject

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyScale {
    const val SCALE = 8
    val ROUNDING: RoundingMode = RoundingMode.HALF_UP
}

@JvmInline
value class Symbol(val value: String) {
    init {
        require(value.isNotBlank()) { "Symbol must not be blank" }
        require(value.uppercase() == value) { "Symbol must be uppercase (e.g. BTCUSDT)" }
    }
}

@JvmInline
value class Timeframe(val value: String) {
    init {
        require(value.isNotBlank()) { "Timeframe must not be blank" }
    }
}

@JvmInline
value class Price(val value: BigDecimal) {
    init {
        require(value >= BigDecimal.ZERO) { "Price must not be negative" }
    }

    fun scaled(): BigDecimal = value.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
}

@JvmInline
value class Quantity(val value: BigDecimal) {
    init {
        require(value > BigDecimal.ZERO) { "Quantity must be positive" }
    }

    fun scaled(): BigDecimal = value.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
}

@JvmInline
value class Money(val value: BigDecimal) {
    fun scaled(): BigDecimal = value.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
}

@JvmInline
value class Percentage(val value: BigDecimal) {
    init {
        require(value.signum() >= 0) { "Percentage must not be negative" }
    }
}

@JvmInline
value class Commission(val value: BigDecimal) {
    fun scaled(): BigDecimal = value.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
}

@JvmInline
value class Slippage(val value: BigDecimal) {
    fun scaled(): BigDecimal = value.setScale(MoneyScale.SCALE, MoneyScale.ROUNDING)
}

@JvmInline
value class SignalId(val value: String) {
    init {
        require(value.isNotBlank()) { "SignalId must not be blank" }
    }
}

@JvmInline
value class TradeId(val value: String) {
    init {
        require(value.isNotBlank()) { "TradeId must not be blank" }
    }
}
