package com.dataflow.backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DataServiceConfig {

    @Bean
    RestClient dataServiceRestClient(
            RestClient.Builder builder,
            @Value("${data-service.base-url}") String baseUrl,
            @Value("${data-service.connect-timeout}") long connectTimeout,
            @Value("${data-service.read-timeout}") long readTimeout) {

        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IllegalArgumentException(
                    "Data service timeouts must be greater than zero");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}