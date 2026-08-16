package com.example.tradingbot.adapter.input.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader("X-Request-Id") ?: UUID.randomUUID().toString()
        request.setAttribute("requestId", requestId)
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-Id", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
        }
    }
}
