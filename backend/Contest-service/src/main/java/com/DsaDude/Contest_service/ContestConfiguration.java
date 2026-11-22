package com.DsaDude.Contest_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ContestConfiguration {
    @Bean
    Clock contestClock() {
        return Clock.systemUTC();
    }
}
