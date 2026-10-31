package com.satmis.common;

import com.jayway.jsonpath.JsonPath;
import com.satmis.TestJwtSecret;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc skips Tomcat's query-string decoding, so these run against the real embedded server
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class MalformedRequestIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", TestJwtSecret::random);
    }

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void search_queryNotValidUtf8_returns400() throws Exception {
        HttpResponse<String> response = get("/api/listings?q=%DD");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.message")).isEqualTo("İstek parametreleri okunamadı");
        assertThat(JsonPath.<String>read(response.body(), "$.path")).isEqualTo("/api/listings");
    }

    @Test
    void search_validUtf8TurkishQuery_returns200() throws Exception {
        assertThat(get("/api/listings?q=%C4%B1%C5%9F%C4%B1k").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
