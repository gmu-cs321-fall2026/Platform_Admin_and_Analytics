import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Abstraction over the source of canonical account registration timestamps. */
public interface RegistrationRepository {
    List<WeeklyRegistration> findWeeklyRegistrations(int weeks) throws SQLException;

    record WeeklyRegistration(LocalDate weekStart, long registrationCount) { }
}
