package com.example.tradingbot.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableScheduling
class WebConfig {

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}
