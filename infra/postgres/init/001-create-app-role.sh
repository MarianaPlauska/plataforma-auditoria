#!/bin/bash
set -e
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_password="$POSTGRES_APP_PASSWORD" \
  --set=app_db="$POSTGRES_DB" <<'SQL'
CREATE ROLE audit_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOBYPASSRLS;
GRANT CONNECT, CREATE ON DATABASE :"app_db" TO audit_app;
GRANT CREATE, USAGE ON SCHEMA public TO audit_app;
ALTER SCHEMA public OWNER TO audit_app;
SQL
