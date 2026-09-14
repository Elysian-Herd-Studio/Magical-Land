package top.csituka.magicaland.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import top.csituka.magicaland.client.config.Config;
import top.csituka.magicaland.client.config.ModelConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MglSkinClient {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger("magicaland/mglskin");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final int MAX_RESPONSE = 2_000_000;

    public record RemoteSkin(long id, String name, String username, String data, boolean isPublic) {}

    public record SkinPage(List<RemoteSkin> items, int total, int page, int limit) {
        public int pages() { return Math.max(1, (int) Math.ceil((double) total / limit)); }
    }

    private MglSkinClient() {}

    public static boolean isLoggedIn() {
        String token = Config.getInstance().mglSkinToken;
        return token != null && !token.isBlank();
    }

    public static String username() { return Config.getInstance().mglSkinUsername; }

    public static void logout() {
        Config config = Config.getInstance();
        config.mglSkinToken = "";
        config.mglSkinUsername = "";
        Config.save();
    }

    public static ModelConfig parseModel(RemoteSkin skin) {
        if (skin == null || skin.data() == null || skin.data().length() > 1_000_000) return null;
        try {
            ModelConfig model = GSON.fromJson(skin.data(), ModelConfig.class);
            if (model == null) return null;
            model.name = skin.name();
            return ModelConfig.sanitize(model);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static void fetchSkins(Consumer<List<RemoteSkin>> success, Consumer<String> failure) {
        request("GET", "/api/skins", null, response -> {
            try {
                JsonObject root = JsonParser.parseString(response).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("items");
                List<RemoteSkin> result = new ArrayList<>();
                if (items != null) for (JsonElement element : items) {
                    JsonObject item = element.getAsJsonObject();
                    result.add(readSkin(item));
                }
                onGameThread(() -> success.accept(result));
            } catch (RuntimeException error) {
                onGameThread(() -> failure.accept(message("invalid_response")));
            }
        }, failure);
    }

    public static void fetchCloudSkins(int page, Consumer<SkinPage> success, Consumer<String> failure) {
        if (!isLoggedIn()) { failure.accept(message("login_required")); return; }
        request("GET", "/api/account/skins?page=" + Math.max(1, page), null, response -> {
            try {
                JsonObject root = JsonParser.parseString(response).getAsJsonObject();
                List<RemoteSkin> items = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray("items")) items.add(readSkin(element.getAsJsonObject()));
                SkinPage result = new SkinPage(List.copyOf(items), root.get("total").getAsInt(),
                        root.get("page").getAsInt(), root.get("limit").getAsInt());
                if (result.total() < 0 || result.page() < 1 || result.limit() < 1)
                    throw new IllegalArgumentException("Invalid page");
                onGameThread(() -> success.accept(result));
            } catch (RuntimeException error) {
                onGameThread(() -> failure.accept(message("invalid_response")));
            }
        }, failure);
    }

    public static void setPublic(long id, boolean isPublic, Consumer<Boolean> success, Consumer<String> failure) {
        if (!isLoggedIn()) { failure.accept(message("login_required")); return; }
        JsonObject body = new JsonObject();
        body.addProperty("isPublic", isPublic);
        request("PATCH", "/api/account/skins/" + id, GSON.toJson(body), response -> {
            try {
                boolean updated = readVisibility(JsonParser.parseString(response).getAsJsonObject());
                onGameThread(() -> success.accept(updated));
            } catch (RuntimeException error) {
                onGameThread(() -> failure.accept(message("invalid_response")));
            }
        }, failure);
    }

    private static RemoteSkin readSkin(JsonObject item) {
        return new RemoteSkin(item.get("id").getAsLong(), item.get("name").getAsString(),
                item.has("username") ? item.get("username").getAsString() : "",
                item.get("data").getAsString(), readVisibility(item));
    }

    private static boolean readVisibility(JsonObject item) {
        JsonElement value = item.get("isPublic");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Missing preset visibility");
        return value.getAsBoolean();
    }

    public static void upload(ModelConfig model, Consumer<String> success, Consumer<String> failure) {
        if (!isLoggedIn()) { failure.accept(message("login_required")); return; }
        JsonObject body = new JsonObject();
        body.addProperty("name", model.name);
        body.addProperty("data", GSON.toJson(model));
        request("POST", "/api/skins", GSON.toJson(body), response -> {
            try {
                String name = JsonParser.parseString(response).getAsJsonObject().get("name").getAsString();
                onGameThread(() -> success.accept(name));
            } catch (RuntimeException error) {
                onGameThread(() -> failure.accept(message("invalid_response")));
            }
        }, failure);
    }

    public static void beginLogin(Consumer<String> success, Consumer<String> failure) {
        String state = UUID.randomUUID().toString();
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/callback", exchange -> handleCallback(exchange, server, state, success, failure));
            server.setExecutor(null);
            server.start();
            String callback = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
            String url = baseUrl() + "/minecraft?callback=" + encode(callback) + "&state=" + encode(state);
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception error) {
            failure.accept(message("login_open_error"));
        }
    }

    private static void handleCallback(HttpExchange exchange, HttpServer server, String expectedState,
            Consumer<String> success, Consumer<String> failure) {
        try {
            String query = exchange.getRequestURI().getRawQuery();
            String code = queryValue(query, "code");
            String state = queryValue(query, "state");
            if (code == null || !expectedState.equals(state)) {
                String message = message("login_callback_invalid");
                onGameThread(() -> failure.accept(message));
                sendCallbackResponse(exchange, 400, message);
            } else {
                sendCallbackResponse(exchange, 200, message("login_return"));
                exchangeToken(code, success, failure);
            }
        } catch (IOException error) {
            onGameThread(() -> failure.accept(message("login_callback_error")));
        } finally {
            exchange.close();
            server.stop(0);
        }
    }

    private static void sendCallbackResponse(HttpExchange exchange, int status, String message) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        byte[] bytes = ("<html><meta charset='utf-8'><body>" + message + "</body></html>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void exchangeToken(String code, Consumer<String> success, Consumer<String> failure) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        request("POST", "/api/auth/minecraft/token", GSON.toJson(body), response -> {
            try {
                JsonObject root = JsonParser.parseString(response).getAsJsonObject();
                String token = root.get("token").getAsString();
                String username = root.getAsJsonObject("user").get("username").getAsString();
                onGameThread(() -> {
                    Config config = Config.getInstance();
                    config.mglSkinToken = token;
                    config.mglSkinUsername = username;
                    Config.save();
                    success.accept(username);
                });
            } catch (RuntimeException error) {
                onGameThread(() -> failure.accept(message("login_token_invalid")));
            }
        }, failure);
    }

    private static void request(String method, String path, String body, Consumer<String> success,
            Consumer<String> failure) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Magical-Land/" + modVersion());
            String token = Config.getInstance().mglSkinToken;
            if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json");
            HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenAccept(response -> {
                        if (response.body().length() > MAX_RESPONSE || response.statusCode() / 100 != 2) {
                            onGameThread(() -> failure.accept(response.statusCode() == 401
                                    ? message("login_expired") : message("request_failed", response.statusCode())));
                        } else success.accept(response.body());
            }).exceptionally(error -> {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                LOGGER.warn("MGL Skin request failed: {}", cause.toString());
                onGameThread(() -> failure.accept(message("connection_failed")));
                return null;
            });
        } catch (RuntimeException error) {
            onGameThread(() -> failure.accept(message("invalid_url")));
        }
    }

    private static void onGameThread(Runnable action) { MinecraftClient.getInstance().execute(action); }

    private static String message(String key, Object... args) {
        return Text.translatable("text.magicaland.mglskin." + key, args).getString();
    }

    private static String modVersion() {
        return "0.3.5";
    }

    private static String baseUrl() {
        String configured = Config.getInstance().mglSkinUrl;
        String value = configured == null || configured.isBlank()
                ? Config.DEFAULT_MGL_SKIN_URL : configured.trim();
        if (value.startsWith("http://localhost")) value = "http://127.0.0.1" + value.substring("http://localhost".length());
        if (value.startsWith("https://localhost")) value = "https://127.0.0.1" + value.substring("https://localhost".length());
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static String queryValue(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key))
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
