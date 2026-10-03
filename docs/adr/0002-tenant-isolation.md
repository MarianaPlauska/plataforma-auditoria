# ADR 0002: Tenant isolation

## Status

Accepted for the local scaffold.

## Decision

Derive tenant identity from a validated JWT `tenant_id` UUID claim. Apply tenant predicates in application queries and PostgreSQL RLS policies. Set `app.current_tenant` transaction-locally before tenant-scoped SQL runs. Vector search must also filter by tenant metadata.

## Consequences

Database policies provide a second boundary beyond service-layer checks. Tests must verify isolation through the production database role; table owners and roles with `BYPASSRLS` are not suitable for application traffic.
