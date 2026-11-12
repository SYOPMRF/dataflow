package com.dataflow.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.dataflow.backend.dto.AnalysisResult;

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

class DataServiceClientTest {

    @Autowired
    private RestClient.Builder builder;

    private MockRestServiceServer mockServer;
    private DataServiceClient dataServiceClient;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        RestClient restClient = builder
                .baseUrl("http://localhost:8000")
                .build();

        dataServiceClient = new DataServiceClient(restClient);
    }

    @Test
    void analyzeFileShouldDeserializeDuplicateAnalysisFromSnakeCaseJson() {

        String response = """
                {
                    "row_count": 2,
                    "column_count": 2,
                    "missing_count": 0,
                    "duplicate_count": 1,
                    "columns": [],
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

        mockServer.expect(
                org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("http://localhost:8000/analysis"))
                .andExpect(
                        org.springframework.test.web.client.match.MockRestRequestMatchers
                                .method(HttpMethod.POST))
                .andExpect(
                        org.springframework.test.web.client.match.MockRestRequestMatchers
                                .content().contentTypeCompatibleWith(
                                        MediaType.MULTIPART_FORM_DATA))
                .andExpect(
                        org.springframework.test.web.client.match.MockRestRequestMatchers
                                .content().string(org.hamcrest.Matchers.containsString("name=\"file\"")))
                .andRespond(
                        org.springframework.test.web.client.response.MockRestResponseCreators
                                .withSuccess(response, MediaType.APPLICATION_JSON));

        AnalysisResult result = dataServiceClient.analyzeFile(
                "id,name\n1,John\n2,John".getBytes(),
                "users.csv");

        assertNotNull(result);
        assertEquals(2, result.rowCount());
        assertEquals(2, result.columnCount());
        assertEquals(1, result.duplicateCount());

        var duplicateAnalysis = result.duplicateAnalysis();

        assertNotNull(duplicateAnalysis);
        assertEquals(1, duplicateAnalysis.evaluatedCombinations());
        assertFalse(duplicateAnalysis.truncated());

        assertFalse(duplicateAnalysis.columnsTruncated());
        assertFalse(duplicateAnalysis.combinationsTruncated());
        assertFalse(duplicateAnalysis.duplicateGroupsTruncated());

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

        mockServer.verify();
    }
}