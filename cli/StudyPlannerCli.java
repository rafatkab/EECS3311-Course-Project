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
            System.out.println("Set STUDY_USERNAME (default student), STUDY_PASSWORD, and optionally BACKEND_URL.");
            System.out.println("If STUDY_PASSWORD is unset, an interactive console securely prompts for it.");
            return;
        }
        if (!(args[0].equals("list") && args.length == 1) && !(args[0].equals("create") && args.length == 5)) {
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
        HttpRequest.Builder request = request(base + "/api/courses", authorization);
        if (args[0].equals("create")) {
            var tokenResponse = client.send(request(base + "/api/csrf", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(tokenResponse);
            String token = tokenResponse.headers().firstValue("X-CSRF-TOKEN").orElseThrow();
            String body = "{\"name\":" + json(args[1]) + ",\"description\":" + json(args[2])
                    + ",\"startDate\":" + json(args[3]) + ",\"endDate\":" + json(args[4]) + "}";
            request.header("X-CSRF-TOKEN", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        check(response);
        System.out.println(response.body());
    }

    private static HttpRequest.Builder request(String url, String authorization) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Authorization", authorization).header("Accept", "application/json");
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
