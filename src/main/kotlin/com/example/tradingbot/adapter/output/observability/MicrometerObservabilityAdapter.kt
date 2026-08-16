package com.example.tradingbot.adapter.output.observability

import com.example.tradingbot.application.port.output.ObservabilityPort
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MicrometerObservabilityAdapter(private val registry: MeterRegistry) : ObservabilityPort {

    private val candlesProcessed: Counter = registry.counter("candles.processed")
    private val signalsGenerated: Counter = registry.counter("signals.generated")
    private val tradesOpened: Counter = registry.counter("trades.opened")
    private val tradesClosed: Counter = registry.counter("trades.closed")
    private val netPnl: Counter = registry.counter("trades.net_pnl")

    override fun candleProcessed() {
        candlesProcessed.increment()
    }

    override fun signalGenerated() {
        signalsGenerated.increment()
    }

    override fun signalIgnored(reason: String) {
        registry.counter("signals.ignored", "reason", reason).increment()
    }

    override fun tradeOpened() {
        tradesOpened.increment()
    }

    override fun tradeClosed() {
        tradesClosed.increment()
    }

    override fun recordNetPnl(netPnl: BigDecimal) {
        this.netPnl.increment(netPnl.toDouble())
    }

    override fun adapterError(adapter: String, operation: String) {
        registry.counter("errors", "adapter", adapter, "operation", operation).increment()
    }

    override fun marketDataHealthy(healthy: Boolean) {
        registry.counter("market_data.healthy", "status", if (healthy) "UP" else "DOWN").increment()
    }
}
