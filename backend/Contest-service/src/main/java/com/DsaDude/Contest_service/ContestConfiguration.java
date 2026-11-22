package com.DsaDude.Contest_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
class ContestConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder contestServiceClient() {
        var transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(2000);
        transport.setReadTimeout(5000);
        return RestClient.builder().requestFactory(transport);
    }

    @Bean
    Clock contestClock() {
        return Clock.systemUTC();
    }
}
