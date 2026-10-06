package com.dataflow.backend.controller;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.service.DataServiceClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/data-service")
public class DataServiceAnalysisController {

    private final DataServiceClient dataServiceClient;

    public DataServiceAnalysisController(DataServiceClient dataServiceClient) {
        this.dataServiceClient = dataServiceClient;
    }

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AnalysisResult analyzeFile(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        return dataServiceClient.analyzeFile(
                file.getBytes(),
                file.getOriginalFilename()
        );
    }
}