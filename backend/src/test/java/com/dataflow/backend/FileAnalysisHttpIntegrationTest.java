package com.dataflow.backend;

import com.dataflow.backend.entity.AnalysisResultEntity;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import com.dataflow.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/dataflow_test",
        "spring.datasource.username=poysmon",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "file.storage.location=target/test-uploads",
        "jwt.secret=DataFlowTestSecretKeyMustBeAtLeast32BytesLong!",
        "jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
class FileAnalysisHttpIntegrationTest {

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

    private User owner;
    private User otherUser;
    private File file;

    @BeforeEach
    void setUp() {
        owner = createUser("owner");
        otherUser = createUser("other");

        file = new File();
        file.setUser(owner);
        file.setOriginalName("http-test.csv");
        file.setStoredName(UUID.randomUUID() + "-http-test.csv");
        file.setFileType("text/csv");
        file.setFileSize(100L);
        file.setStatus(FileStatus.COMPLETED);
        file = fileRepository.saveAndFlush(file);

        AnalysisResultEntity analysis = new AnalysisResultEntity();
        analysis.setFile(file);
        analysis.setRowCount(4);
        analysis.setColumnCount(1);
        analysis.setMissingCount(0);
        analysis.setDuplicateCount(1);
        analysisResultRepository.saveAndFlush(analysis);

        FileColumn column = new FileColumn();
        column.setFile(file);
        column.setName("age");
        column.setDataType("integer");
        column.setNullCount(0);
        column.setUniqueCount(3);
        column.setUniquePercentage(75.0);
        column.setDuplicateCount(1);
        column.setDuplicatedUniqueCount(1);
        column.setMissingPercentage(0.0);
        column.setCompletelyEmpty(false);
        column.setMinValue("18");
        column.setMaxValue("65");
        column.setMeanValue(35.5);
        column.setMedianValue(35.5);
        column.setStdDeviation(10.0);
        column.setZeroCount(0);
        column.setNegativeCount(0);
        fileColumnRepository.saveAndFlush(column);
    }

    @Test
    void ownerCanRetrievePersistedAnalysis() throws Exception {
        String token = jwtService.generateToken(owner.getEmail());

        mockMvc.perform(get("/api/files/{id}/analysis", file.getId())
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row_count").value(4))
                .andExpect(jsonPath("$.column_count").value(1))
                .andExpect(jsonPath("$.missing_count").value(0))
                .andExpect(jsonPath("$.duplicate_count").value(1))
                .andExpect(jsonPath("$.columns", hasSize(1)))
                .andExpect(jsonPath("$.columns[0].name").value("age"))
                .andExpect(jsonPath("$.columns[0].data_type").value("integer"))
                .andExpect(jsonPath("$.quality_issues", hasSize(0)));
    }

    @Test
    void anotherUserCannotRetrieveTheFileAnalysis() throws Exception {
        String token = jwtService.generateToken(otherUser.getEmail());

        mockMvc.perform(get("/api/files/{id}/analysis", file.getId())
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File not found"));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/files/{id}/analysis", file.getId())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private User createUser(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@dataflow.test");
        user.setPasswordHash("not-used-for-authentication");
        return userRepository.saveAndFlush(user);
    }

}
