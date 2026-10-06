package com.dataflow.backend.controller;

import com.dataflow.backend.service.DataServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-service")
public class DataServiceController {

    private final DataServiceClient dataServiceClient;

    public DataServiceController(DataServiceClient dataServiceClient) {
        this.dataServiceClient = dataServiceClient;
    }

    @GetMapping("/health")
    public String healthCheck() {
        return dataServiceClient.healthCheck();
    }
}