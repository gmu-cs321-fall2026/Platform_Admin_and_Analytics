 
public class User {

    private int id;
    private String name;
    private String email;
    private String role;
    private String status;

    public User(
            int id,
            String name,
            String email,
            String role,
            String status) {

        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        setStatus(status);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public static boolean isValidRole(String role) {
        return "STUDENT".equals(role) || "STAFF".equals(role) || "ADMIN".equals(role);
    }

    public void setRole(String role) {
        if (!isValidRole(role)) throw new IllegalArgumentException("Invalid role");
        this.role = role;
    }

    public static boolean isValidStatus(String status) {
        return "ACTIVE".equals(status) || "SUSPENDED".equals(status);
    }

    public void setStatus(String status) {
        if (!isValidStatus(status)) throw new IllegalArgumentException("Invalid status");
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}