#!/usr/bin/env bash
set -euo pipefail
# Keep Git Bash on Windows from rewriting Linux container paths.
export MSYS_NO_PATHCONV=1

db_sql() {
    docker compose exec -T db sh -c \
        'exec psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 "$@"' \
        psql "$@"
}

echo 'Checking the backend startup log...'
backend_logs=$(docker compose logs --no-color backend 2>&1) || {
    printf '%s\n' "$backend_logs" >&2
    exit 1
}
if ! grep -F 'PostgreSQL connection verified.' <<< "$backend_logs"; then
    echo 'The backend startup log is missing the PostgreSQL verification message.' >&2
    printf '%s\n' "$backend_logs" >&2
    exit 1
fi
curl --fail --silent --show-error --retry 5 --retry-connrefused \
    http://localhost:9000/admin >/dev/null

echo 'Checking that PostgreSQL rejects an invalid password...'
if output=$(docker compose run --rm --no-deps -e DB_PASSWORD=invalid-for-test \
    --entrypoint java backend -cp /app/out:/app/lib/postgresql.jar database.Database 2>&1); then
    echo 'The backend accepted an invalid database password.' >&2
    exit 1
fi
if ! grep -F 'PostgreSQL startup check failed (SQL state 28P01)' <<< "$output"; then
    echo 'Expected PostgreSQL to reject the password with SQL state 28P01.' >&2
    printf '%s\n' "$output" >&2
    exit 1
fi

echo 'Checking that a row survives database container recreation...'
probe_table="scrum47_probe_$(printf '%s_%s' "$BASHPID" "$RANDOM")"
db_sql -q -c "CREATE TABLE $probe_table (id integer PRIMARY KEY); INSERT INTO $probe_table VALUES (1);"
trap 'db_sql -q -c "DROP TABLE IF EXISTS $probe_table;"' EXIT

docker compose up -d --wait --wait-timeout 120 --force-recreate db
probe_id=$(db_sql -At -c "SELECT id FROM $probe_table;")
if [[ "$probe_id" != 1 ]]; then
    echo 'The probe row did not survive database container recreation.' >&2
    exit 1
fi
echo 'SCRUM-47 connection and persistence checks passed'
