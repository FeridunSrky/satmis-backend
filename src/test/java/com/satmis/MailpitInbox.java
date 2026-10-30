package com.satmis;

import com.jayway.jsonpath.JsonPath;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads mails the app sent over SMTP back through Mailpit's HTTP API, like a user opening their inbox.
public final class MailpitInbox {

    public static final int SMTP_PORT = 1025;
    private static final int API_PORT = 8025;
    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private MailpitInbox() {
    }

    public static GenericContainer<?> newContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
                .withExposedPorts(SMTP_PORT, API_PORT);
    }

    /**
     * Waits until `expectedMails` mails with a `linkBaseUrl?token=` link reached `email` and returns their tokens,
     * newest first. Mails with other links (a user gets both verification and reset mails) are skipped.
     */
    public static List<String> tokensMailedTo(GenericContainer<?> mailpit, String email, String linkBaseUrl,
                                              int expectedMails) throws Exception {
        Pattern tokenInLink = Pattern.compile(Pattern.quote(linkBaseUrl + "?token=") + "([A-Za-z0-9_-]+)");
        Instant deadline = Instant.now().plus(MAIL_WAIT);
        while (true) {
            List<String> tokens = new ArrayList<>();
            for (String id : messageIds(mailpit, email)) {
                String text = JsonPath.read(get(mailpit, "/api/v1/message/" + id), "$.Text");
                Matcher matcher = tokenInLink.matcher(text);
                if (matcher.find()) {
                    tokens.add(matcher.group(1));
                }
            }
            if (tokens.size() >= expectedMails) {
                return tokens;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Expected " + expectedMails + " mails with " + linkBaseUrl + " links to "
                        + email + ", found " + tokens.size());
            }
            Thread.sleep(POLL_INTERVAL);
        }
    }

    public static List<String> messageIds(GenericContainer<?> mailpit, String email) throws Exception {
        String query = URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8);
        return JsonPath.read(get(mailpit, "/api/v1/search?query=" + query), "$.messages[*].ID");
    }

    private static String get(GenericContainer<?> mailpit, String path) throws Exception {
        URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(API_PORT) + path);
        return HTTP.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }
}
