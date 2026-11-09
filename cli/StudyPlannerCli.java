import java.io.Console;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Java 17 source-file CLI: uses the same authenticated REST API as the GUI. */
public class StudyPlannerCli {
    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception failure) {
            System.err.println("Request failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) {
            System.out.println("Usage: java cli/StudyPlannerCli.java list");
            System.out.println("       java cli/StudyPlannerCli.java create \"NAME\" \"DESCRIPTION\" YYYY-MM-DD YYYY-MM-DD");
            System.out.println("       java cli/StudyPlannerCli.java materials COURSE_ID");
            System.out.println("       java cli/StudyPlannerCli.java upload COURSE_ID FILE [FILE ...] (up to 10)");
            System.out.println("       java cli/StudyPlannerCli.java content COURSE_ID MATERIAL_ID");
            System.out.println("       java cli/StudyPlannerCli.java summarize COURSE_ID MATERIAL_ID");
            System.out.println("       java cli/StudyPlannerCli.java summary COURSE_ID MATERIAL_ID (saved result)");
            System.out.println("       java cli/StudyPlannerCli.java regenerate-summary COURSE_ID MATERIAL_ID");
            System.out.println("       java cli/StudyPlannerCli.java extract-concepts COURSE_ID MATERIAL_ID");
            System.out.println("       java cli/StudyPlannerCli.java concepts COURSE_ID MATERIAL_ID (saved result)");
            System.out.println("       java cli/StudyPlannerCli.java regenerate-concepts COURSE_ID MATERIAL_ID");
            System.out.println("Set STUDY_USERNAME (default student), STUDY_PASSWORD, and optionally BACKEND_URL.");
            System.out.println("       java cli/StudyPlannerCli.java profile");
            System.out.println("       java cli/StudyPlannerCli.java flashcards COURSE_ID REQUEST_JSON_FILE");
            System.out.println("       java cli/StudyPlannerCli.java decks COURSE_ID [BEFORE_SEQUENCE]");
            System.out.println("       java cli/StudyPlannerCli.java deck COURSE_ID DECK_ID");
            System.out.println("       java cli/StudyPlannerCli.java save-profile PROFILE_JSON_FILE");
            System.out.println("       java cli/StudyPlannerCli.java plan COURSE_ID");
            System.out.println("       java cli/StudyPlannerCli.java generate-plan COURSE_ID REQUEST_JSON_FILE");
            System.out.println("       java cli/StudyPlannerCli.java events COURSE_ID FROM_INSTANT TO_INSTANT");
            System.out.println("       java cli/StudyPlannerCli.java add-event COURSE_ID EVENT_JSON_FILE");
            System.out.println("       java cli/StudyPlannerCli.java remove-event COURSE_ID EVENT_ID");
            System.out.println("       java cli/StudyPlannerCli.java ask COURSE_ID \"QUESTION\"");
            System.out.println("       java cli/StudyPlannerCli.java history COURSE_ID [BEFORE_SEQUENCE]");
            System.out.println("If STUDY_PASSWORD is unset, an interactive console securely prompts for it.");
            return;
        }
        boolean valid = switch (args[0]) {
            case "flashcards", "deck" -> args.length == 3;
            case "decks" -> args.length == 2 || args.length == 3;
            case "list" -> args.length == 1;
            case "profile" -> args.length == 1;
            case "save-profile", "plan" -> args.length == 2;
            case "generate-plan", "add-event", "remove-event" -> args.length == 3;
            case "events" -> args.length == 4;
            case "create" -> args.length == 5;
            case "materials" -> args.length == 2;
            case "ask" -> args.length == 3;
            case "history" -> args.length == 2 || args.length == 3;
            case "upload" -> args.length >= 3 && args.length <= 12;
            case "content" -> args.length == 3;
            case "summarize", "summary", "regenerate-summary", "extract-concepts", "concepts", "regenerate-concepts" -> args.length == 3;
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid arguments. Run with help for usage.");
        }
        String username = System.getenv().getOrDefault("STUDY_USERNAME", "student");
        String password = System.getenv("STUDY_PASSWORD");
        if (password == null || password.isBlank()) {
            Console console = System.console();
            if (console == null) throw new IllegalArgumentException("Set STUDY_PASSWORD or use an interactive console.");
            char[] entered = console.readPassword("Password for %s: ", username);
            if (entered == null) throw new IllegalArgumentException("Password entry cancelled.");
            password = new String(entered);
            java.util.Arrays.fill(entered, '\0');
        }
        String base = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8081").replaceAll("/+$", "");
        String authorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
        if (java.util.Set.of("flashcards", "decks", "deck").contains(args[0])) {
            String url = base + "/api/courses/" + UUID.fromString(args[1]) + "/flashcards";
            if (args[0].equals("deck")) url += "/" + UUID.fromString(args[2]);
            if (args[0].equals("decks") && args.length == 3) url += "?before=" + Long.parseLong(args[2]);
            var flashcards = request(url, authorization);
            if (args[0].equals("flashcards")) {
                byte[] bytes;
                try (var stream = Files.newInputStream(Path.of(args[2]))) { bytes = stream.readNBytes(65537); }
                if (bytes.length > 65536) throw new IllegalArgumentException("Flashcard request file must be 64 KiB or smaller.");
                var token = client.send(request(base + "/api/csrf", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
                check(token);
                flashcards.timeout(Duration.ofSeconds(200)).header("X-CSRF-TOKEN", token.headers().firstValue("X-CSRF-TOKEN").orElseThrow())
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            }
            var result = client.send(flashcards.build(), HttpResponse.BodyHandlers.ofString()); check(result); System.out.println(result.body()); return;
        }
        if (java.util.Set.of("profile", "save-profile", "plan", "generate-plan", "events", "add-event", "remove-event").contains(args[0])) {
            planning(client, base, authorization, args); return;
        }
        HttpRequest.Builder request = request(base + "/api/courses", authorization);
        if (args[0].equals("ask") || args[0].equals("history")) {
            String url = base + "/api/courses/" + UUID.fromString(args[1]) + "/chat";
            if (args[0].equals("history") && args.length == 3) url += "?before=" + Long.parseLong(args[2]);
            request = request(url, authorization);
        }
        if (java.util.Set.of("materials", "upload", "content", "summarize", "summary", "regenerate-summary", "extract-concepts", "concepts", "regenerate-concepts").contains(args[0])) {
            String courseId = UUID.fromString(args[1]).toString();
            String url = base + "/api/courses/" + courseId + "/materials";
            if (args[0].equals("content")) url += "/" + UUID.fromString(args[2]) + "/content";
            if (java.util.Set.of("summarize", "summary", "regenerate-summary").contains(args[0])) {
                url += "/" + UUID.fromString(args[2]) + "/summary";
                if (args[0].equals("regenerate-summary")) url += "?regenerate=true";
            }
            if (java.util.Set.of("extract-concepts", "concepts", "regenerate-concepts").contains(args[0])) {
                url += "/" + UUID.fromString(args[2]) + "/concepts";
                if (args[0].equals("regenerate-concepts")) url += "?regenerate=true";
            }
            request = request(url, authorization);
            if (args[0].equals("content")) request.setHeader("Accept", "text/plain, application/json");
        }
        if (java.util.Set.of("create", "upload", "summarize", "regenerate-summary", "extract-concepts", "regenerate-concepts", "ask").contains(args[0])) {
            var tokenResponse = client.send(request(base + "/api/csrf", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(tokenResponse);
            String token = tokenResponse.headers().firstValue("X-CSRF-TOKEN").orElseThrow();
            if (args[0].equals("ask")) {
                request.timeout(Duration.ofSeconds(250)).header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"question\":" + json(args[2]) + "}"));
            } else if (args[0].equals("create")) {
                String body = "{\"name\":" + json(args[1]) + ",\"description\":" + json(args[2])
                    + ",\"startDate\":" + json(args[3]) + ",\"endDate\":" + json(args[4]) + "}";
                request.header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
            } else if (java.util.Set.of("summarize", "regenerate-summary", "extract-concepts", "regenerate-concepts").contains(args[0])) {
                request.timeout(Duration.ofSeconds(200)).header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.noBody());
            } else {
                String boundary = "StudyPlanner" + UUID.randomUUID().toString().replace("-", "");
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                for (int index = 2; index < args.length; index++) {
                    Path path = Path.of(args[index]);
                    String name = path.getFileName().toString().replace('"', '_').replace('\r', '_').replace('\n', '_');
                    byte[] bytes;
                    try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(10 * 1024 * 1024 + 1); }
                    if (bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("Each file must be 10 MiB or smaller.");
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + name
                            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    body.write(bytes);
                    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
                body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                request.timeout(Duration.ofSeconds(60)).header("X-CSRF-TOKEN", token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            }
        }
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        check(response);
        System.out.println(response.body());
    }

    private static HttpRequest.Builder request(String url, String authorization) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Authorization", authorization).header("Accept", "application/json");
    }
    private static void planning(HttpClient client, String base, String authorization, String[] args) throws Exception {
        String command = args[0], method = "GET", body = null;
        String url;
        if (command.equals("profile") || command.equals("save-profile")) url = base + "/api/study-profile";
        else {
            url = base + "/api/courses/" + UUID.fromString(args[1]);
            url += command.equals("plan") ? "/planning" : command.equals("generate-plan") ? "/study-plan" : "/calendar-events";
            if (command.equals("remove-event")) url += "/" + UUID.fromString(args[2]);
            if (command.equals("events")) url += "?from=" + java.net.URLEncoder.encode(java.time.Instant.parse(args[2]).toString(), StandardCharsets.UTF_8)
                    + "&to=" + java.net.URLEncoder.encode(java.time.Instant.parse(args[3]).toString(), StandardCharsets.UTF_8);
        }
        if (java.util.Set.of("save-profile", "generate-plan", "add-event").contains(command)) {
            Path path = Path.of(args[command.equals("save-profile") ? 1 : 2]);
            if (Files.size(path) > 65536) throw new IllegalArgumentException("Planning JSON files must be 64 KiB or smaller.");
            body = Files.readString(path, StandardCharsets.UTF_8); method = command.equals("save-profile") ? "PUT" : "POST";
        } else if (command.equals("remove-event")) method = "DELETE";
        var builder = request(url, authorization).timeout(Duration.ofSeconds(250));
        if (!method.equals("GET")) {
            var token = client.send(request(base + "/api/csrf", authorization).GET().build(), HttpResponse.BodyHandlers.ofString()); check(token);
            builder.header("X-CSRF-TOKEN", token.headers().firstValue("X-CSRF-TOKEN").orElseThrow()).header("Content-Type", "application/json");
        }
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        check(response); System.out.println(response.body());
    }
    private static void check(HttpResponse<String> response) {
        if (response.statusCode() == 401) throw new IllegalArgumentException("Authentication failed. Check your username/password.");
        if (response.statusCode() >= 400) throw new IllegalArgumentException("HTTP " + response.statusCode() + ": " + response.body());
    }
    private static String json(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> { if (c < 32) escaped.append(String.format("\\u%04x", (int)c)); else escaped.append(c); }
            }
        }
        return escaped.append('"').toString();
    }
}
