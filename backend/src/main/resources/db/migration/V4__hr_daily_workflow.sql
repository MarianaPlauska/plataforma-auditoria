ALTER TABLE remediation_tasks
    ADD COLUMN category text NOT NULL DEFAULT 'GENERAL'
        CHECK (category IN ('GENERAL', 'PGR_ACTION', 'HR_CHANGE')),
    ADD COLUMN origin_reference text,
    ADD COLUMN source_event text;

ALTER TABLE task_notification_outbox
    ADD COLUMN event_type text NOT NULL DEFAULT 'TASK_CREATED',
    ADD COLUMN reminder_for_date date;

ALTER TABLE hr_people ADD CONSTRAINT hr_people_tenant_id_unique UNIQUE (tenant_id, id);

CREATE UNIQUE INDEX task_notification_reminder_once_idx
    ON task_notification_outbox(tenant_id, task_id, event_type, reminder_for_date)
    WHERE reminder_for_date IS NOT NULL;

CREATE TABLE hr_change_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    person_id uuid NOT NULL,
    external_ref text NOT NULL,
    change_type text NOT NULL CHECK (change_type IN ('NEW_HIRE', 'UNIT_CHANGED', 'STATUS_CHANGED', 'EFFECTIVE_DATE_CHANGED')),
    previous_unit text,
    new_unit text,
    previous_status text,
    new_status text,
    effective_date date,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, person_id) REFERENCES hr_people(tenant_id, id)
);
CREATE INDEX hr_change_events_tenant_created_idx ON hr_change_events(tenant_id, created_at DESC);

ALTER TABLE hr_change_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_change_events FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_change_events_tenant_isolation ON hr_change_events
    USING (tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));
