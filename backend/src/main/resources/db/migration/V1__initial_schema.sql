CREATE TABLE tenants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    original_filename text NOT NULL,
    object_key text NOT NULL,
    content_type text,
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    status text NOT NULL CHECK (status IN ('RECEIVED', 'PROCESSING', 'READY', 'FAILED')),
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    UNIQUE (tenant_id, object_key)
);
CREATE INDEX documents_tenant_created_idx ON documents(tenant_id, created_at DESC);

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    action text NOT NULL,
    resource_id uuid,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_tenant_created_idx ON audit_logs(tenant_id, created_at DESC);

CREATE TABLE ingestion_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    document_id uuid NOT NULL REFERENCES documents(id),
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);
CREATE INDEX ingestion_outbox_pending_idx ON ingestion_outbox(created_at) WHERE published_at IS NULL;

CREATE TABLE document_embeddings (
    id uuid PRIMARY KEY,
    content text NOT NULL,
    metadata json NOT NULL DEFAULT '{}'::json,
    embedding vector(768) NOT NULL
);
CREATE INDEX document_embeddings_tenant_idx ON document_embeddings ((metadata->>'tenant_id'));
CREATE INDEX document_embeddings_hnsw_idx ON document_embeddings USING hnsw (embedding vector_cosine_ops);

DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['tenants', 'documents', 'audit_logs', 'ingestion_outbox'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
    END LOOP;
END $$;

CREATE POLICY tenants_tenant_isolation ON tenants
    USING (id::text = current_setting('app.current_tenant', true))
    WITH CHECK (id::text = current_setting('app.current_tenant', true));
CREATE POLICY documents_tenant_isolation ON documents
    USING (tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));
CREATE POLICY audit_logs_tenant_isolation ON audit_logs
    USING (tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));
CREATE POLICY outbox_tenant_isolation ON ingestion_outbox
    USING (current_setting('app.current_tenant', true) = 'system'
           OR tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (current_setting('app.current_tenant', true) = 'system'
                OR tenant_id::text = current_setting('app.current_tenant', true));

ALTER TABLE document_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_embeddings FORCE ROW LEVEL SECURITY;
CREATE POLICY embeddings_tenant_isolation ON document_embeddings
    USING (metadata->>'tenant_id' = current_setting('app.current_tenant', true))
    WITH CHECK (metadata->>'tenant_id' = current_setting('app.current_tenant', true));
