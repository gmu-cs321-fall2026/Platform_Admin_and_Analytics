public class UserTest {

    public static void main(String[] args) {

        testUserCreation();
        testUserGetters();
        testUserStatusChanges();
        testInvalidStatuses();

        System.out.println("All User tests passed!");
    }

    // Test that a User object is created correctly
    private static void testUserCreation() {

        User user = new User(
                1,
                "John Doe",
                "john@gmu.edu",
                "STUDENT",
                "ACTIVE"
        );

        assert user.getId() == 1;
        assert user.getName().equals("John Doe");
        assert user.getEmail().equals("john@gmu.edu");
        assert user.getRole().equals("STUDENT");
        assert user.getStatus().equals("ACTIVE");

        System.out.println("testUserCreation passed");
    }

    // Test that User getters return the correct values
    private static void testUserGetters() {

        User user = new User(
                2,
                "Jane Smith",
                "jane@gmu.edu",
                "ADMIN",
                "ACTIVE"
        );

        if (user.getId() != 2) {
            throw new AssertionError("Incorrect user ID");
        }

        if (!user.getName().equals("Jane Smith")) {
            throw new AssertionError("Incorrect user name");
        }

        if (!user.getEmail().equals("jane@gmu.edu")) {
            throw new AssertionError("Incorrect user email");
        }

        if (!user.getRole().equals("ADMIN")) {
            throw new AssertionError("Incorrect user role");
        }

        if (!user.getStatus().equals("ACTIVE")) {
            throw new AssertionError("Incorrect user status");
        }

        System.out.println("testUserGetters passed");
    }
    private static void testUserStatusChanges() {
        User user = new User(1, "Test User", "test@example.com", "STUDENT", "ACTIVE");
        user.setStatus("SUSPENDED");
        if (!user.getStatus().equals("SUSPENDED")) throw new AssertionError("Suspend failed");
        user.setStatus("ACTIVE");
        if (!user.getStatus().equals("ACTIVE")) throw new AssertionError("Unsuspend failed");
    }

    private static void testInvalidStatuses() {
        User user = new User(1, "Test User", "test@example.com", "STUDENT", "ACTIVE");
        for (String invalid : new String[] {null, "", "DELETED", "active", "ADMIN"}) {
            if (User.isValidStatus(invalid)) throw new AssertionError("Invalid status accepted");
            try {
                user.setStatus(invalid);
                throw new AssertionError("Invalid status assigned");
            } catch (IllegalArgumentException expected) {
                if (!user.getStatus().equals("ACTIVE")) throw new AssertionError("Invalid update changed status");
            }
        }
        try {
            new User(2, "Test", "test@example.com", "STAFF", "PENDING");
            throw new AssertionError("Invalid initial status accepted");
        } catch (IllegalArgumentException expected) { }
    }
}