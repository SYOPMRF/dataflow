package com.dataflow.backend.dto;

import java.util.List;

public record DuplicateAnalysis(

        List<CandidateKey> candidateKeys,

        List<DuplicateGroup> duplicateGroups,

        int evaluatedCombinations,

        boolean columnsTruncated,

        boolean combinationsTruncated,

        boolean duplicateGroupsTruncated,

        boolean truncated) {

    public DuplicateAnalysis {
        candidateKeys = candidateKeys == null ? List.of() : List.copyOf(candidateKeys);
        duplicateGroups = duplicateGroups == null ? List.of() : List.copyOf(duplicateGroups);
    }

    public static DuplicateAnalysis empty() {
        return new DuplicateAnalysis(
                List.of(),
                List.of(),
                0,
                false,
                false,
                false,
                false
        );
    }
}