import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Inclusive calendar dates interpreted consistently with the audit log. */
record StatisticsDateRange(LocalDate startDate, LocalDate endDate) {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    StatisticsDateRange {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        if (endDate != null && endDate.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("End date is out of range.");
        }
    }

    static StatisticsDateRange parse(String query) {
        Map<String, String> fields = new HashMap<>();
        if (query != null && !query.isBlank()) {
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Invalid date filter.");
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (!(key.equals("startDate") || key.equals("endDate"))
                        || fields.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Invalid date filter.");
                }
            }
        }
        return new StatisticsDateRange(date(fields.get("startDate")), date(fields.get("endDate")));
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Invalid date.");
        try {
            return LocalDate.parse(value);
        } catch (java.time.DateTimeException error) {
            throw new IllegalArgumentException("Invalid date.", error);
        }
    }

    boolean hasBounds() { return startDate != null || endDate != null; }
    OffsetDateTime startInclusive() { return startDate.atStartOfDay(ZONE).toOffsetDateTime(); }
    OffsetDateTime endExclusive() { return endDate.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime(); }
}
