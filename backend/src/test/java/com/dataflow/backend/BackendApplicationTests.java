package com.dataflow.backend;

import com.dataflow.backend.dto.AnalysisResult;
import tools.jackson.databind.json.JsonMapper;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
class BackendApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateUserWithEncryptedPassword() {

        String email = "bcrypt-test-" + System.currentTimeMillis() + "@dataflow.com";
        String rawPassword = "MySecurePassword123";

        User user = userService.createUser(email, rawPassword);

        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());

        assertEquals(email, user.getEmail());

        // The stored password must not be the original password
        assertNotEquals(rawPassword, user.getPasswordHash());

        // BCrypt must be able to verify the original password
        assertTrue(
                passwordEncoder.matches(
                        rawPassword,
                        user.getPasswordHash()));
    }

    @Test
    void shouldDeserializeAdvancedColumnStatisticsFromSnakeCaseJson()
            throws Exception {

        String json = """
                                                {
                                                    "row_count": 10,
                                                    "column_count": 1,
                                                    "missing_count": 0,
                                                    "duplicate_count": 0,
                                                    "columns": [
                                                        {
                                                            "name": "sales",
                                                            "data_type": "float64",
                                                            "null_count": 0,
                                                            "unique_count": 10,
                                                            "unique_percentage": 100.0,
                                                            "duplicate_count": 0,
                                                            "duplicated_unique_count": 0,
                                                            "missing_percentage": 0.0,
                                                            "is_completely_empty": false,
                                                            "min_value": 1.0,
                                                            "max_value": 100.0,
                                                            "mean_value": 50.0,
                                                            "median_value": 50.0,
                                                            "std_deviation": 30.0,
                                                            "zero_count": 0,
                                                            "negative_count": 0,
                                                            "q1_value": 14.0,
                                                            "q3_value": 22.0,
                                                            "iqr_value": 8.0,
                                                            "p5_value": 10.8,
                                                            "p10_value": 11.6,
                                                            "p90_value": 39.2,
                                                            "p95_value": 69.6,
                                                            "outlier_count": 1
                                                        }
                                                    ],
                                                    "quality_issues": [],
                                                    "duplicate_analysis": {
                    "candidate_keys": [
                        {
                            "columns": ["id"],
                            "key_type": "SINGLE",
                            "evaluated_row_count": 2,
                            "unique_count": 2,
                            "uniqueness_percentage": 100.0
                        }
                    ],
                    "duplicate_groups": [
                        {
                            "columns": ["name", "age"],
                            "group_count": 1,
                            "duplicate_count": 1,
                            "max_frequency": 2,
                            "examples": [
                                {
                                    "values": ["John", 25],
                                    "frequency": 2
                                }
                            ]
                        }
                    ],
                    "evaluated_combinations": 1,
                    "columns_truncated": false,
                    "combinations_truncated": false,
                    "duplicate_groups_truncated": false,
                    "truncated": false
                }
                                                }
                                                """;

        AnalysisResult result = jsonMapper.readValue(
                json,
                AnalysisResult.class);

        var column = result.columns().get(0);

        assertEquals(14.0, column.q1Value());
        assertEquals(22.0, column.q3Value());
        assertEquals(8.0, column.iqrValue());
        assertEquals(10.8, column.p5Value());
        assertEquals(11.6, column.p10Value());
        assertEquals(39.2, column.p90Value());
        assertEquals(69.6, column.p95Value());
        assertEquals(1, column.outlierCount());
        var duplicateAnalysis = result.duplicateAnalysis();

        assertNotNull(duplicateAnalysis);
        assertEquals(1, duplicateAnalysis.evaluatedCombinations());
        assertFalse(duplicateAnalysis.truncated());

        assertEquals(1, duplicateAnalysis.candidateKeys().size());

        var candidateKey = duplicateAnalysis.candidateKeys().get(0);

        assertEquals(List.of("id"), candidateKey.columns());
        assertEquals("SINGLE", candidateKey.keyType());
        assertEquals(2, candidateKey.evaluatedRowCount());
        assertEquals(2, candidateKey.uniqueCount());
        assertEquals(100.0, candidateKey.uniquenessPercentage());

        assertEquals(1, duplicateAnalysis.duplicateGroups().size());

        var duplicateGroup = duplicateAnalysis.duplicateGroups().get(0);

        assertEquals(List.of("name", "age"), duplicateGroup.columns());
        assertEquals(1, duplicateGroup.groupCount());
        assertEquals(1, duplicateGroup.duplicateCount());
        assertEquals(2, duplicateGroup.maxFrequency());

        assertEquals(1, duplicateGroup.examples().size());

        var example = duplicateGroup.examples().get(0);

        assertEquals(2, example.frequency());
        assertEquals("John", example.values().get(0));
        assertEquals(25, example.values().get(1));
    }
}