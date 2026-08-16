package com.example.tradingbot.adapter.input.rest

import com.example.tradingbot.adapter.input.rest.dto.DtoMappers
import com.example.tradingbot.adapter.input.rest.dto.HealthResponse
import com.example.tradingbot.adapter.input.rest.dto.StatusResponse
import com.example.tradingbot.application.port.input.GetSystemStatusUseCase
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class SystemController(
    private val getSystemStatus: GetSystemStatusUseCase,
    private val healthEndpoint: HealthEndpoint,
) {

    @GetMapping("/health")
    fun health(): HealthResponse {
        val descriptor = healthEndpoint.health()
        val status = descriptor.status.code.uppercase()
        val components = (descriptor as? CompositeHealthDescriptor)?.components
        val checks = (components ?: emptyMap())
            .mapValues { (_, component) -> component.status.code.uppercase() }
        return HealthResponse(status = status, checks = checks)
    }

    @GetMapping("/status")
    fun status(): StatusResponse {
        val s = getSystemStatus.status()
        return StatusResponse(
            mode = s.mode,
            signalsEnabled = s.signalsEnabled,
            emergencyActive = s.emergencyActive,
            openTrade = s.openTrade?.let(DtoMappers::toOpenTrade),
            marketDataHealthy = s.marketDataHealthy,
            lastCandleProcessedAt = s.lastCandleProcessedAt,
            signalsDisabledBy = s.signalsDisabledBy,
            signalsDisabledAt = s.signalsDisabledAt,
        )
    }
}
