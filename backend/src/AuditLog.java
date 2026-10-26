import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class AuditLog {
    private static final Path FILE =
            Path.of("data", "admin-audit.tsv");

    public static final Set<String> ACTIONS = Set.of(
            "ROLE_CHANGED",
            "ACCOUNT_SUSPENDED",
            "ACCOUNT_UNSUSPENDED",
            "POST_REMOVED",
            "USER_WARNED",
            "FLAG_DISMISSED",
            "RESOURCE_APPROVED",
            "RESOURCE_REJECTED",
            "QUESTION_APPROVED",
            "QUESTION_REJECTED"
    );

    private static final Set<String> MODERATION_ACTIONS = Set.of(
            "POST_REMOVED",
            "USER_WARNED",
            "FLAG_DISMISSED",
            "RESOURCE_APPROVED",
            "RESOURCE_REJECTED",
            "QUESTION_APPROVED",
            "QUESTION_REJECTED"
    );

    private AuditLog() {
    }

    public record Entry(
            String id,
            Instant timestamp,
            String action,
            String targetType,
            String targetId,
            String targetName,
            String adminId,
            String reason,
            String previousValue,
            String newValue
    ) {
    }

    // Temporary identity for the standalone demo.
    // Replace this with the signed-in admin's ID during integration.
    public static String demoAdminId() {
        String configured =
                System.getenv("DEMO_ADMIN_ID");

        return configured == null || configured.isBlank()
                ? "demo-admin"
                : configured.trim();
    }

    public static synchronized List<Entry> readAll()
            throws IOException {

        List<Entry> entries = new ArrayList<>();

        if (!Files.exists(FILE)) {
            return entries;
        }

        for (String line :
                Files.readAllLines(FILE, StandardCharsets.UTF_8)) {

            if (line.isBlank()) {
                continue;
            }

            try {
                String[] fields = line.split("\t", -1);

                if (fields.length != 10) {
                    throw new IllegalArgumentException(
                            "Invalid audit entry"
                    );
                }

                for (int i = 0; i < fields.length; i++) {
                    fields[i] = decode(fields[i]);
                }

                if (!ACTIONS.contains(fields[2])) {
                    throw new IllegalArgumentException(
                            "Invalid audit action"
                    );
                }

                entries.add(new Entry(
                        fields[0],
                        Instant.parse(fields[1]),
                        fields[2],
                        fields[3],
                        fields[4],
                        fields[5],
                        fields[6],
                        fields[7],
                        fields[8],
                        fields[9]
                ));
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Could not read the audit log",
                        exception
                );
            }
        }

        return entries;
    }

    public static synchronized void recordUserChange(
            User user,
            String value,
            boolean changingStatus
    ) throws IOException {

        if (changingStatus && !User.isValidStatus(value)) {
            throw new IllegalArgumentException("Invalid status");
        }

        if (!changingStatus && !User.isValidRole(value)) {
            throw new IllegalArgumentException("Invalid role");
        }

        String previousValue = changingStatus
                ? user.getStatus()
                : user.getRole();

        if (previousValue.equals(value)) {
            return;
        }

        String action = changingStatus
                ? (value.equals("SUSPENDED")
                    ? "ACCOUNT_SUSPENDED"
                    : "ACCOUNT_UNSUSPENDED")
                : "ROLE_CHANGED";

        append(new Entry(
                UUID.randomUUID().toString(),
                Instant.now(),
                action,
                "USER",
                String.valueOf(user.getId()),
                user.getName(),
                demoAdminId(),
                "",
                previousValue,
                value
        ));
    }

    // Called by the moderation-action feature when it is implemented.
    // This does not create fake moderation entries.
    public static synchronized void recordModerationAction(
            String action,
            String targetType,
            String targetId,
            String targetName,
            String reason
    ) throws IOException {

        if (!MODERATION_ACTIONS.contains(action)) {
            throw new IllegalArgumentException(
                    "Invalid moderation action"
            );
        }

        if (targetType == null || targetType.isBlank()
                || targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException(
                    "An affected item is required"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A moderation reason is required"
            );
        }

        append(new Entry(
                UUID.randomUUID().toString(),
                Instant.now(),
                action,
                targetType,
                targetId,
                targetName == null ? "" : targetName,
                demoAdminId(),
                reason.trim(),
                "",
                ""
        ));
    }

    // Replays account changes from the same file that stores the audit log.
    // Existing role/status properties are loaded first by UserApi.
    public static synchronized void restoreUsers(
            List<User> users
    ) throws IOException {

        Map<String, User> byId = new HashMap<>();

        for (User user : users) {
            byId.put(String.valueOf(user.getId()), user);
        }

        for (Entry entry : readAll()) {
            if (!entry.targetType().equals("USER")) {
                continue;
            }

            User user = byId.get(entry.targetId());

            if (user == null) {
                continue;
            }

            try {
                switch (entry.action()) {
                    case "ROLE_CHANGED" ->
                            user.setRole(entry.newValue());

                    case "ACCOUNT_SUSPENDED",
                         "ACCOUNT_UNSUSPENDED" ->
                            user.setStatus(entry.newValue());

                    default -> {
                        // Other actions do not change account fields.
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Invalid saved account change",
                        exception
                );
            }
        }
    }

    private static void append(Entry entry)
            throws IOException {

        List<Entry> entries = readAll();
        entries.add(entry);

        StringBuilder contents = new StringBuilder();

        for (Entry current : entries) {
            String[] fields = {
                    current.id(),
                    current.timestamp().toString(),
                    current.action(),
                    current.targetType(),
                    current.targetId(),
                    current.targetName(),
                    current.adminId(),
                    current.reason(),
                    current.previousValue(),
                    current.newValue()
            };

            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    contents.append('\t');
                }

                contents.append(encode(fields[i]));
            }

            contents.append('\n');
        }

        Files.createDirectories(FILE.getParent());

        Path temporary = Files.createTempFile(
                FILE.getParent(),
                "admin-audit-",
                ".tmp"
        );

        try {
            byte[] bytes = contents.toString()
                    .getBytes(StandardCharsets.UTF_8);

            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                channel.force(true);
            }

            try {
                Files.move(
                        temporary,
                        FILE,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String decode(String value) {
        return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8
        );
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder json = new StringBuilder("\"");

        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 32) {
                        json.append(String.format(
                                "\\u%04x",
                                (int) character
                        ));
                    } else {
                        json.append(character);
                    }
                }
            }
        }

        return json.append('"').toString();
    }

    public static String toJson(Entry entry) {
        return "{"
                + "\"id\":" + quote(entry.id()) + ","
                + "\"timestamp\":"
                + quote(entry.timestamp().toString()) + ","
                + "\"action\":" + quote(entry.action()) + ","
                + "\"targetType\":" + quote(entry.targetType()) + ","
                + "\"targetId\":" + quote(entry.targetId()) + ","
                + "\"targetName\":" + quote(entry.targetName()) + ","
                + "\"adminId\":" + quote(entry.adminId()) + ","
                + "\"reason\":" + quote(entry.reason()) + ","
                + "\"previousValue\":"
                + quote(entry.previousValue()) + ","
                + "\"newValue\":" + quote(entry.newValue())
                + "}";
    }
}