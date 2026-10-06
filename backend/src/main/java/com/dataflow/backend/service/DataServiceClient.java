package com.dataflow.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DataServiceClient {

    private final RestClient restClient;

    public DataServiceClient(RestClient dataServiceRestClient) {
        this.restClient = dataServiceRestClient;
    }

    public String healthCheck() {
        return restClient
                .get()
                .uri("/health")
                .retrieve()
                .body(String.class);
    }
}