package com.example.tradingbot.application.port.output

interface MarketDataConnectionPort {
    fun isConnected(): Boolean
}
