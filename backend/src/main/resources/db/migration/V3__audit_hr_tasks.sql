ALTER TABLE documents ADD CONSTRAINT documents_tenant_id_unique UNIQUE (tenant_id, id);

CREATE TABLE control_catalog (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    control_code text NOT NULL,
    version text NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    source_url text NOT NULL,
    effective_from date,
    review_status text NOT NULL DEFAULT 'DRAFT' CHECK (review_status IN ('DRAFT', 'REVIEWED', 'RETIRED')),
    reviewed_by text,
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, control_code, version),
    UNIQUE (tenant_id, id)
);

CREATE TABLE audit_findings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    control_id uuid NOT NULL,
    document_id uuid,
    title text NOT NULL,
    evidence_excerpt text NOT NULL,
    state text NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'IN_REVIEW', 'ACTION_REQUIRED', 'CONFIRMED', 'DISMISSED', 'CLOSED')),
    confidence numeric(4, 3) CHECK (confidence BETWEEN 0 AND 1),
    review_note text,
    reviewed_by text,
    reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, control_id) REFERENCES control_catalog(tenant_id, id),
    FOREIGN KEY (tenant_id, document_id) REFERENCES documents(tenant_id, id),
    UNIQUE (tenant_id, id)
);
CREATE INDEX audit_findings_tenant_created_idx ON audit_findings(tenant_id, created_at DESC);

CREATE TABLE remediation_tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    finding_id uuid,
    title text NOT NULL,
    assignee_email text,
    due_date date,
    state text NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    external_system text,
    external_id text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, finding_id) REFERENCES audit_findings(tenant_id, id),
    UNIQUE (tenant_id, id)
);
CREATE INDEX remediation_tasks_tenant_due_idx ON remediation_tasks(tenant_id, due_date, state);

CREATE TABLE task_notification_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    task_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    FOREIGN KEY (tenant_id, task_id) REFERENCES remediation_tasks(tenant_id, id)
);
CREATE INDEX task_notification_pending_idx ON task_notification_outbox(next_attempt_at) WHERE sent_at IS NULL;

CREATE TABLE hr_imports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    source_name text NOT NULL,
    imported_rows integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by text,
    UNIQUE (tenant_id, id)
);

CREATE TABLE hr_people (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    external_ref text NOT NULL,
    unit_code text NOT NULL,
    employment_status text NOT NULL,
    effective_date date,
    import_id uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, import_id) REFERENCES hr_imports(tenant_id, id),
    UNIQUE (tenant_id, external_ref)
);
CREATE INDEX hr_people_tenant_unit_idx ON hr_people(tenant_id, unit_code, employment_status);

DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['control_catalog', 'audit_findings', 'remediation_tasks', 'task_notification_outbox', 'hr_imports', 'hr_people'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id::text = current_setting(''app.current_tenant'', true)) WITH CHECK (tenant_id::text = current_setting(''app.current_tenant'', true))', table_name || '_tenant_isolation', table_name);
    END LOOP;
END $$;

DROP POLICY remediation_tasks_tenant_isolation ON remediation_tasks;
CREATE POLICY remediation_tasks_tenant_isolation ON remediation_tasks
    USING (current_setting('app.current_tenant', true) = 'system'
           OR tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (current_setting('app.current_tenant', true) = 'system'
                OR tenant_id::text = current_setting('app.current_tenant', true));

DROP POLICY task_notification_outbox_tenant_isolation ON task_notification_outbox;
CREATE POLICY task_notification_outbox_tenant_isolation ON task_notification_outbox
    USING (current_setting('app.current_tenant', true) = 'system'
           OR tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (current_setting('app.current_tenant', true) = 'system'
                OR tenant_id::text = current_setting('app.current_tenant', true));

SELECT set_config('app.current_tenant', '00000000-0000-0000-0000-000000000001', true);

INSERT INTO control_catalog(tenant_id, control_code, version, title, description, source_url, effective_from)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'GRO-RISK-INVENTORY', 'rascunho-2026-05', 'Inventário de riscos ocupacionais', 'Rascunho de controle para registrar perigos, avaliação de riscos, população e revisão. Validar com profissional de SST antes de usar em auditoria.', 'https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-paritaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1', '2026-05-26'),
    ('00000000-0000-0000-0000-000000000001', 'GRO-ACTION-PLAN', 'rascunho-2026-05', 'Plano de ação e medidas preventivas', 'Rascunho de controle para acompanhar medidas, responsáveis, prazos e comprovação de execução. Validar com profissional de SST antes de usar em auditoria.', 'https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-paritaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1', '2026-05-26'),
    ('00000000-0000-0000-0000-000000000001', 'GRO-PSYCHOSOCIAL', 'rascunho-2026-05', 'Fatores de risco psicossociais relacionados ao trabalho', 'Rascunho de controle para avaliação no nível das condições de trabalho e das medidas preventivas, sem classificação individual de saúde. Validar com profissional de SST antes de usar em auditoria.', 'https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-paritaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1', '2026-05-26')
ON CONFLICT (tenant_id, control_code, version) DO NOTHING;
