package com.example.tradingbot.configuration

import com.example.tradingbot.application.port.output.ClockPort
import com.example.tradingbot.application.port.output.TransactionPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SystemClockPort : ClockPort {
    override fun now(): Instant = Instant.now()
}

@Component
class SpringTransactionPort : TransactionPort {

    @Transactional
    override fun <T> executeInTransaction(block: () -> T): T = block()
}
