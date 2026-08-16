package com.example.tradingbot.adapter.output.binance

import com.example.tradingbot.application.port.output.MarketDataPort
import com.example.tradingbot.application.port.output.ObservabilityPort
import com.example.tradingbot.configuration.TradingProperties
import com.example.tradingbot.domain.exception.InvalidDataException
import com.example.tradingbot.domain.valueobject.Price
import com.example.tradingbot.domain.valueobject.Symbol
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCheckedSupplier
import io.github.resilience4j.core.functions.CheckedSupplier
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.Retry.decorateCheckedSupplier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Duration

@Component
class BinanceMarketDataAdapter(
    private val properties: TradingProperties,
    private val webClient: WebClient,
    private val observability: ObservabilityPort,
    @Qualifier("binanceRetry") private val retry: Retry,
    @Qualifier("binanceCircuitBreaker") private val circuitBreaker: CircuitBreaker,
) : MarketDataPort {

    override fun currentPrice(symbol: Symbol): Price {
        val url = "${properties.binance.restBaseUrl}/api/v3/ticker/price?symbol=${symbol.value}"
        val supplier = CheckedSupplier<BigDecimal> {
            val response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(TickerPriceResponse::class.java)
                .timeout(Duration.ofSeconds(10))
                .block()
                ?: throw InvalidDataException("Binance ticker returned no body for $symbol")
            val price = response.price
                ?: throw InvalidDataException("Binance ticker returned no price for $symbol")
            if (price.signum() < 0) {
                throw InvalidDataException("Binance ticker returned negative price for $symbol")
            }
            price
        }
        val decorated = decorateCheckedSupplier(circuitBreaker, decorateCheckedSupplier(retry, supplier))
        return try {
            Price(decorated.get())
        } catch (e: Exception) {
            observability.adapterError("binance", "ticker")
            throw e
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TickerPriceResponse(
    val symbol: String? = null,
    val price: BigDecimal? = null,
)
