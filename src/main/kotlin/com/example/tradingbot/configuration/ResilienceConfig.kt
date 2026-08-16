package com.example.tradingbot.configuration

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.util.concurrent.TimeoutException

@Configuration
class ResilienceConfig {

    @Bean
    fun retryRegistry(properties: TradingProperties): RetryRegistry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts((properties.telegram.maxRetries + 1).coerceAtLeast(1))
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                WebClientRequestException::class.java,
                WebClientResponseException::class.java,
                TimeoutException::class.java,
            )
            .build()
        return RetryRegistry.of(config)
    }

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .build()
        return CircuitBreakerRegistry.of(config)
    }

    @Bean
    fun telegramRetry(registry: RetryRegistry): Retry = registry.retry("telegram")

    @Bean
    fun telegramCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker =
        registry.circuitBreaker("telegram")

    @Bean
    fun binanceRetry(registry: RetryRegistry): Retry = registry.retry("binance-rest")

    @Bean
    fun binanceCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker =
        registry.circuitBreaker("binance-rest")
}
