package com.example.tradingbot.domain.exception

import com.example.tradingbot.domain.valueobject.Symbol

open class TradingException(message: String) : RuntimeException(message)

class OpenTradeExistsException(val openSymbol: Symbol?) :
    TradingException("A trade is already open${openSymbol?.let { " on $it" } ?: ""}")

class NoOpenTradeException : TradingException("There is no open trade to close")

class SignalsDisabledException : TradingException("Signal processing is disabled")

class EmergencyActiveException : TradingException("Emergency mode is active; new trades are blocked")

class DuplicateCandleException(symbol: String, timeframe: String, openTime: Any) :
    TradingException("Duplicate candle $symbol $timeframe $openTime")

class InvalidDataException(message: String) : TradingException(message)

class NotFoundException(what: String, id: String) : TradingException("$what not found: $id")
