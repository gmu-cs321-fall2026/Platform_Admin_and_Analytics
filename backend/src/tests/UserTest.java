public class UserTest {

    public static void main(String[] args) {

        testUserCreation();
        testUserGetters();

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
}