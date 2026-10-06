package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    public AnalysisResult analyzeFile(byte[] fileContent, String filename) {
        ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        return restClient
                .post()
                .uri("/analysis")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AnalysisResult.class);
    }
}