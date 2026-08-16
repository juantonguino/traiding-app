package com.example.tradingbot.adapter.input.binance

import com.example.tradingbot.adapter.input.binance.dto.BinanceKlineEvent
import com.example.tradingbot.domain.exception.InvalidDataException
import com.example.tradingbot.domain.model.MarketCandle
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.example.tradingbot.domain.valueobject.Timeframe
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

@Component
class BinanceKlineMapper {

    fun toMarketCandle(event: BinanceKlineEvent): MarketCandle {
        val k = event.k ?: throw InvalidDataException("Binance kline event has no 'k' payload")
        val symbolValue = (event.symbol ?: k.symbol)?.uppercase() ?: throw InvalidDataException("Binance kline has no symbol")
        val openTimeMillis = k.openTime ?: throw InvalidDataException("Binance kline has no open time (t)")
        val closeTimeMillis = k.closeTime ?: throw InvalidDataException("Binance kline has no close time (T)")
        val timeframeValue = k.interval ?: throw InvalidDataException("Binance kline has no interval (i)")

        val open = parsePrice(k.openPrice, "open")
        val close = parsePrice(k.closePrice, "close")
        val high = parsePrice(k.highPrice, "high")
        val low = parsePrice(k.lowPrice, "low")
        val volume = k.baseVolume?.let { BigDecimal(it) } ?: throw InvalidDataException("Binance kline has no volume (v)")
        val closed = k.isClosed ?: false

        return MarketCandle(
            symbol = Symbol(symbolValue),
            timeframe = Timeframe(timeframeValue),
            openTime = Instant.ofEpochMilli(openTimeMillis),
            closeTime = Instant.ofEpochMilli(closeTimeMillis),
            open = open,
            close = close,
            high = high,
            low = low,
            volume = volume,
            isClosed = closed,
        )
    }

    private fun parsePrice(raw: String?, field: String): Price {
        val v = raw?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: throw InvalidDataException("Binance kline has no valid $field price")
        if (v.signum() < 0) throw InvalidDataException("Binance kline $field price must not be negative")
        return Price(v)
    }
}
