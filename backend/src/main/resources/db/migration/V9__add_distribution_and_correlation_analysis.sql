ALTER TABLE analysis_results
    ADD COLUMN distribution_analysis JSONB NOT NULL
        DEFAULT '{"columns": []}'::jsonb,
    ADD COLUMN correlation_analysis JSONB NOT NULL
        DEFAULT '{
            "columns": [],
            "pearson": [],
            "spearman": [],
            "observation_counts": [],
            "truncated": false
        }'::jsonb;