package io.github.kwd421.lumitoolbridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class HttpJsonClient {
    public record Response(int statusCode, String body) {}

    private final HttpClient client;
    private final Duration timeout;

    public HttpJsonClient(Duration connectTimeout, Duration timeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.timeout = timeout;
    }

    public Response post(String url, Object payload, String authorization) throws Exception {
        String body = Json.write(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "little-lumi-agent-bridge/" + Config.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null && !authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }
        HttpResponse<String> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    public static String join(String base, String path) {
        String normalizedBase = base;
        while (normalizedBase.endsWith("/")) normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    public static Map<String, Object> parseObject(Response response) {
        return Json.object(Json.parse(response.body()));
    }
}
