-- Local-only tenant for issuing dev JWTs. Replace with a controlled tenant-provisioning process.
SELECT set_config('app.current_tenant', '00000000-0000-0000-0000-000000000001', true);
INSERT INTO tenants(id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Local Demo Tenant')
ON CONFLICT (id) DO NOTHING;
