package com.example.tradingbot.application.port.output

import com.example.tradingbot.domain.model.CloseReason
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal
import com.example.tradingbot.domain.valueobject.Symbol
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

interface NotificationPort {
    fun send(message: NotificationMessage)
}

sealed class NotificationMessage {
    abstract fun text(): String

    abstract val correlationId: String

    protected val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")

    protected val paperLabel = "\n\n\ud83d\udd12 PAPER TRADING \u2014 simulation only. No real order was executed."

    data class BuySignal(
        val signal: TradingSignal,
        override val correlationId: String = signal.signalId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\ud83d\udd34 BUY SIGNAL\n")
            sb.appendBase(signal)
            return sb.append(paperLabel).toString()
        }
    }

    data class SellSignal(
        val signal: TradingSignal,
        override val correlationId: String = signal.signalId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\ud83d\udd35 SELL SIGNAL\n")
            sb.appendBase(signal)
            return sb.append(paperLabel).toString()
        }
    }

    data class IgnoredSignal(
        val signal: TradingSignal,
        val openSymbol: Symbol?,
        override val correlationId: String = signal.signalId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\u26a0\ufe0f SIGNAL IGNORED\n")
            sb.append("Symbol: ${signal.symbol.value}\n")
            sb.append("Side: ${signal.side}\n")
            sb.append("Reason: ${signal.ignoreReason}\n")
            openSymbol?.let { sb.append("Open trade on: $it\n") }
            sb.append("Price: ${signal.price.value.toPlainString()}\n")
            return sb.append(paperLabel).toString()
        }
    }

    data class TradeOpened(
        val trade: PaperTrade,
        override val correlationId: String = trade.tradeId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\ud83d\udfe9 TRADE OPENED (SIMULATED)\n")
            sb.append("Symbol: ${trade.symbol.value}\n")
            sb.append("Entry: ${trade.entryPrice.value.toPlainString()}\n")
            sb.append("Quantity: ${trade.quantity.value.toPlainString()}\n")
            sb.append("Strategy: ${trade.strategy}\n")
            trade.stopLoss?.let { sb.append("Stop-loss: ${it.value.toPlainString()}\n") }
            trade.takeProfit?.let { sb.append("Take-profit: ${it.value.toPlainString()}\n") }
            sb.append("Open time: ${formatTime(trade.openTime)}\n")
            return sb.append(paperLabel).toString()
        }
    }

    data class TradeClosed(
        val trade: PaperTrade,
        override val correlationId: String = trade.tradeId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\ud83d\udd2e TRADE CLOSED (SIMULATED)\n")
            sb.append("Symbol: ${trade.symbol.value}\n")
            sb.append("Entry: ${trade.entryPrice.value.toPlainString()}  Exit: ${trade.exitPrice?.value?.toPlainString()}\n")
            sb.append("Gross PnL: ${trade.grossPnl?.value?.toPlainString()}\n")
            sb.append("Fees: ${trade.fees?.value?.toPlainString()}  Slippage: ${trade.slippageCost?.value?.toPlainString()}\n")
            sb.append("Net PnL: ${trade.netPnl?.value?.toPlainString()}\n")
            sb.append("Return: ${trade.returnPct}%\n")
            sb.append("Close reason: ${trade.closeReason}\n")
            trade.durationSeconds?.let { sb.append("Duration: ${it}s\n") }
            return sb.append(paperLabel).toString()
        }
    }

    data class GainOrLoss(
        val trade: PaperTrade,
        override val correlationId: String = trade.tradeId.value,
    ) : NotificationMessage() {
        override fun text(): String {
            val sign = if ((trade.netPnl?.value ?: BigDecimal.ZERO).signum() >= 0) "\ud83d\udfe2" else "\ud83d\udd34"
            val sb = StringBuilder()
            sb.append("$sign RESULT: ${trade.result}\n")
            sb.append("Symbol: ${trade.symbol.value}\n")
            sb.append("Net PnL: ${trade.netPnl?.value?.toPlainString()} (${trade.returnPct}%)\n")
            return sb.append(paperLabel).toString()
        }
    }

    data class CriticalError(
        val message: String,
        override val correlationId: String = "system",
    ) : NotificationMessage() {
        override fun text(): String = "\ud83d\udea8 CRITICAL ERROR\n$message".plus(paperLabel)
    }

    data class MarketDataLoss(
        val symbol: Symbol,
        override val correlationId: String = "system",
    ) : NotificationMessage() {
        override fun text(): String = "\ud83d\udea8 MARKET DATA LOSS\nNo closed candles received for ${symbol.value} within the configured window.\nNew entries are blocked until data recovers.".plus(paperLabel)
    }

    data class Emergency(
        val closedTradeId: String?,
        override val correlationId: String = "system",
    ) : NotificationMessage() {
        override fun text(): String {
            val sb = StringBuilder()
            sb.append("\ud83d\udea8 EMERGENCY STOP ACTIVATED\n")
            sb.append("Open trade closed: ${closedTradeId ?: "none"}\n")
            sb.append("New entries are blocked.")
            return sb.append(paperLabel).toString()
        }
    }

    data class Summary(
        val text: String,
        override val correlationId: String = "summary",
    ) : NotificationMessage() {
        override fun text(): String = "\ud83d\udcca PERIODIC RESULT SUMMARY\n$text".plus(paperLabel)
    }

    protected fun StringBuilder.appendBase(s: TradingSignal) {
        append("Symbol: ${s.symbol.value}\n")
        append("Price: ${s.price.value.toPlainString()}\n")
        append("Interval: ${s.timeframe.value}\n")
        append("Strategy: ${s.strategy}\n")
        s.stopLoss?.let { append("Stop-loss: ${it.value.toPlainString()}\n") }
        s.takeProfit?.let { append("Take-profit: ${it.value.toPlainString()}\n") }
        append("Confidence: ${s.confidence}%\n")
        append("Reason: ${s.reason}\n")
    }

    protected fun formatTime(t: java.time.Instant): String =
        java.time.ZonedDateTime.ofInstant(t, java.time.ZoneOffset.UTC).format(timeFormatter)
}

fun CloseReason.label(): String = name.replace('_', ' ').lowercase()
