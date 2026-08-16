package com.example.tradingbot.application.service.strategy

import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.valueobject.Symbol
import java.math.BigDecimal
import java.util.ArrayDeque

class InMemoryCandleHistory(private val maxPerSymbol: Int = 500) : CandleHistory {

    private val candlesBySymbol = mutableMapOf<Symbol, ArrayDeque<MarketCandle>>()

    override fun append(candle: MarketCandle) {
        val queue = candlesBySymbol.getOrPut(candle.symbol) { ArrayDeque() }
        if (queue.peekLast()?.openTime == candle.openTime) {
            return
        }
        queue.addLast(candle)
        while (queue.size > maxPerSymbol) {
            queue.removeFirst()
        }
    }

    override fun closes(symbol: Symbol, window: Int): List<BigDecimal> =
        candlesBySymbol[symbol]?.toList()?.takeLast(window)?.map { it.close.value } ?: emptyList()

    override fun size(symbol: Symbol): Int = candlesBySymbol[symbol]?.size ?: 0
}
