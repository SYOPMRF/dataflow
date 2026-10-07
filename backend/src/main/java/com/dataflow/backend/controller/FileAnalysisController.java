package com.dataflow.backend.controller;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.service.FileAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
public class FileAnalysisController {

    private final FileAnalysisService fileAnalysisService;

    public FileAnalysisController(FileAnalysisService fileAnalysisService) {
        this.fileAnalysisService = fileAnalysisService;
    }

    @PostMapping("/{id}/process")
    public AnalysisResult processFile(@PathVariable Long id) throws IOException {
        return fileAnalysisService.analyzeFile(id);
    }
}