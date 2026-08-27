package net.bsxray.gitsync.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Minimal GitHub REST API client used to upload/download files.
 * Author: bsxray
 */
public final class GitHubApi {
    private static final String API = "https://api.github.com";

    private final String owner;
    private final String repo;
    private final String token;
    private final String branch;
    private final HttpClient client;

    public GitHubApi(String owner, String repo, String token, String branch) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.branch = branch;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Lists all files (blobs) in the whole repository, recursively. */
    public List<TreeEntry> getTree() throws Exception {
        URI uri = URI.create(API + "/repos/" + owner + "/" + repo
                + "/git/trees/" + enc(branch) + "?recursive=1");
        JsonObject obj = getJson(uri);
        JsonArray arr = obj.has("tree") ? obj.getAsJsonArray("tree") : null;
        List<TreeEntry> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "";
            if (!"blob".equals(type)) continue;
            String path = o.has("path") ? o.get("path").getAsString() : "";
            String sha = o.has("sha") ? o.get("sha").getAsString() : "";
            long size = o.has("size") ? o.get("size").getAsLong() : -1;
            out.add(new TreeEntry(path, sha, size));
        }
        return out;
    }

    /** Downloads a file's raw bytes by its blob SHA. Works for files of any size. */
    public byte[] downloadBlob(String sha) throws Exception {
        JsonObject obj = getJson(URI.create(API + "/repos/" + owner + "/" + repo
                + "/git/blobs/" + sha));
        String content = obj.has("content") ? obj.get("content").getAsString() : "";
        // The blob API base64 can contain escaped newlines; strip all whitespace before decoding.
        String cleaned = content.replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * Creates or updates a single file in the repository.
     * Automatically supplies the existing file SHA when present (update),
     * otherwise creates the file.
     */
    public void uploadFile(String path, byte[] bytes, String message) throws Exception {
        String existingSha = null;
        try {
            JsonObject meta = getJson(URI.create(API + "/repos/" + owner + "/" + repo
                    + "/contents/" + encPath(path) + "?ref=" + enc(branch)));
            if (meta.has("sha")) {
                existingSha = meta.get("sha").getAsString();
            }
        } catch (GitHubApiException e) {
            if (e.code != 404) throw e;
            // 404 = file does not exist yet -> will be created.
        }

        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("branch", branch);
        body.addProperty("content", Base64.getEncoder().encodeToString(bytes));
        if (existingSha != null) {
            body.addProperty("sha", existingSha);
        }
        putJson(URI.create(API + "/repos/" + owner + "/" + repo + "/contents/" + encPath(path)), body);
    }

    private JsonObject getJson(URI uri) throws Exception {
        HttpResponse<String> r = send("GET", uri, null);
        if (r.statusCode() >= 300) {
            throw new GitHubApiException(r.statusCode(), r.body());
        }
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private void putJson(URI uri, JsonObject body) throws Exception {
        HttpResponse<String> r = send("PUT", uri, body);
        if (r.statusCode() >= 300) {
            throw new GitHubApiException(r.statusCode(), r.body());
        }
    }

    private HttpResponse<String> send(String method, URI uri, JsonObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(60));
        switch (method) {
            case "GET" -> b.GET();
            case "PUT" -> {
                b.header("Content-Type", "application/json");
                b.PUT(HttpRequest.BodyPublishers.ofString(
                        body == null ? "{}" : body.toString(), StandardCharsets.UTF_8));
            }
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encPath(String path) {
        StringBuilder sb = new StringBuilder();
        String[] segs = path.split("/");
        for (String seg : segs) {
            if (seg.isEmpty()) continue;
            if (sb.length() > 0) sb.append("/");
            sb.append(URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }
}
