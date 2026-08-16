package com.example.tradingbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TradingBotApplication

fun main(args: Array<String>) {
    runApplication<TradingBotApplication>(*args)
}
