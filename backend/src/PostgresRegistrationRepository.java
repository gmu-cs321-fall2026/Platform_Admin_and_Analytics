import database.Database;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL adapter for the platform_account_registrations integration contract. */
public final class PostgresRegistrationRepository implements RegistrationRepository {
    private static final String WEEKLY_COUNTS_SQL = """
            WITH week_starts AS (
                SELECT generate_series(
                    date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                        - (? - 1) * INTERVAL '1 week',
                    date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                    INTERVAL '1 week'
                )::date AS week_start
            ), registration_counts AS (
                SELECT date_trunc('week', registered_at AT TIME ZONE 'UTC')::date AS week_start,
                       COUNT(*) AS registration_count
                FROM platform_account_registrations
                WHERE registered_at >= (
                    date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                    - (? - 1) * INTERVAL '1 week'
                ) AT TIME ZONE 'UTC'
                  AND registered_at < (
                    date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                    + INTERVAL '1 week'
                ) AT TIME ZONE 'UTC'
                GROUP BY 1
            )
            SELECT week_starts.week_start, COALESCE(registration_counts.registration_count, 0)
            FROM week_starts
            LEFT JOIN registration_counts USING (week_start)
            ORDER BY week_starts.week_start
            """;

    @Override
    public List<WeeklyRegistration> findWeeklyRegistrations(int weeks) throws SQLException {
        List<WeeklyRegistration> result = new ArrayList<>();
        try (var connection = Database.open();
             var statement = connection.prepareStatement(WEEKLY_COUNTS_SQL)) {
            statement.setInt(1, weeks);
            statement.setInt(2, weeks);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    LocalDate weekStart = rows.getDate(1).toLocalDate();
                    result.add(new WeeklyRegistration(weekStart, rows.getLong(2)));
                }
            }
        }
        return result;
    }
}
