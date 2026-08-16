package com.example.tradingbot.application.service.strategy

import com.example.tradingbot.application.port.output.NotificationMessage
import com.example.tradingbot.application.port.output.NotificationPort
import com.example.tradingbot.domain.model.PaperTrade
import com.example.tradingbot.domain.model.TradingSignal

class NotificationMessageService(private val notificationPort: NotificationPort) {

    fun signalGenerated(signal: TradingSignal) {
        when (signal.side) {
            com.example.tradingbot.domain.model.SignalSide.BUY ->
                notificationPort.send(NotificationMessage.BuySignal(signal))
            com.example.tradingbot.domain.model.SignalSide.SELL ->
                notificationPort.send(NotificationMessage.SellSignal(signal))
            com.example.tradingbot.domain.model.SignalSide.HOLD -> Unit
        }
    }

    fun signalIgnored(signal: TradingSignal, openSymbol: com.example.tradingbot.domain.valueobject.Symbol?) {
        notificationPort.send(NotificationMessage.IgnoredSignal(signal, openSymbol))
    }

    fun tradeOpened(trade: PaperTrade) {
        notificationPort.send(NotificationMessage.TradeOpened(trade))
    }

    fun tradeClosed(trade: PaperTrade) {
        notificationPort.send(NotificationMessage.TradeClosed(trade))
        notificationPort.send(NotificationMessage.GainOrLoss(trade))
    }
}
