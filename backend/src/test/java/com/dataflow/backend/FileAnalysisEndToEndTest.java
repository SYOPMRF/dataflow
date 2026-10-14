package com.dataflow.backend;

import com.dataflow.backend.entity.DataQualityIssueEntity;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import com.dataflow.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/dataflow_test",
        "spring.datasource.username=${DATAFLOW_TEST_DB_USERNAME:poysmon}",
        "spring.datasource.password=${DATAFLOW_TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "file.storage.location=target/e2e-test-uploads",
        "data-service.base-url=http://127.0.0.1:8000",
        "jwt.secret=DataFlowTestSecretKeyMustBeAtLeast32BytesLong!",
        "jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "dataflow.e2e", matches = "true")
class FileAnalysisEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private FileColumnRepository fileColumnRepository;

    @Autowired
    private DataQualityIssueRepository dataQualityIssueRepository;

    @Test
    void uploadedCsvIsAnalyzedByRealDataServiceAndPersisted() throws Exception {
        // Arrange: create a dedicated test owner and a real JWT.
        User owner = new User();
        owner.setEmail("e2e-" + UUID.randomUUID() + "@dataflow.test");
        owner.setPasswordHash("not-used-for-authentication");
        owner = userRepository.saveAndFlush(owner);

        String token = jwtService.generateToken(owner.getEmail());

        byte[] csvBytes = (
                "name,age\n" +
                "Ana,25\n" +
                "Luis,-3\n" +
                "Marta,30\n"
        ).getBytes(StandardCharsets.UTF_8);

        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "negative_values.csv",
                "text/csv",
                csvBytes
        );

        // Act 1: upload the CSV through the real Spring HTTP endpoint.
        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(csv)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.original_name")
                        .value("negative_values.csv"))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        Number fileIdValue = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");
        long fileId = fileIdValue.longValue();
        assertFalse(fileId <= 0, "Upload must return a persisted file ID");

        // Act 2: process the stored file through Spring -> real FastAPI.
        mockMvc.perform(
                        post("/api/files/{id}/process", fileId)
                                .header("Authorization", "Bearer " + token)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row_count").value(3))
                .andExpect(jsonPath("$.column_count").value(2))
                .andExpect(jsonPath("$.columns[1].name").value("age"))
                .andExpect(jsonPath("$.columns[1].negative_count").value(1))
                .andExpect(jsonPath("$.quality_issues").isArray())
                .andExpect(jsonPath("$.quality_issues", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.quality_issues[0].column_name").value("age"))
                .andExpect(jsonPath("$.quality_issues[0].issue_type")
                        .value("NEGATIVE_VALUES"))
                .andExpect(jsonPath("$.quality_issues[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$.quality_issues[0].message")
                        .value("Column 'age' contains 1 negative values."));

        // Act 3: retrieve the persisted result through the HTTP endpoint.
        mockMvc.perform(
                        get("/api/files/{id}/analysis", fileId)
                                .header("Authorization", "Bearer " + token)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row_count").value(3))
                .andExpect(jsonPath("$.columns[1].negative_count").value(1))
                .andExpect(jsonPath("$.quality_issues", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.quality_issues[0].issue_type")
                        .value("NEGATIVE_VALUES"));

        // Assert: confirm the result and issue were actually persisted in PostgreSQL.
        File persistedFile = fileRepository.findById(fileId).orElseThrow();
        assertEquals(FileStatus.COMPLETED, persistedFile.getStatus());

        var persistedAnalysis = analysisResultRepository.findByFileId(fileId)
                .orElseThrow();
        assertEquals(3, persistedAnalysis.getRowCount());
        assertEquals(2, persistedAnalysis.getColumnCount());

        FileColumn ageColumn = fileColumnRepository.findAllByFileId(fileId)
                .stream()
                .filter(column -> column.getName().equals("age"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, ageColumn.getNegativeCount());

        var persistedIssues = dataQualityIssueRepository.findAllByFileId(fileId);
        assertEquals(1, persistedIssues.size());

        DataQualityIssueEntity issue = persistedIssues.get(0);
        assertEquals("age", issue.getColumnName());
        assertEquals("NEGATIVE_VALUES", issue.getIssueType());
        assertEquals("MEDIUM", issue.getSeverity());
        assertEquals(
                "Column 'age' contains 1 negative values.",
                issue.getMessage()
        );
        assertNotNull(persistedFile.getProcessedAt());
    }
}
