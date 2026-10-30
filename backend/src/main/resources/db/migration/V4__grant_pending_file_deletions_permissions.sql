DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'dataflow_runtime'
    ) THEN
        EXECUTE '
            GRANT SELECT, INSERT, UPDATE, DELETE
            ON TABLE public.pending_file_deletions
            TO dataflow_runtime
        ';

        EXECUTE '
            GRANT USAGE
            ON SEQUENCE public.pending_file_deletions_id_seq
            TO dataflow_runtime
        ';
    END IF;
END
$$;
