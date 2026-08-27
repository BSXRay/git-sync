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
 * Uploads use the Git Data API (blobs -> tree -> commit -> ref), which
 * supports files up to 100 MB each (unlike the 1 MB Contents API limit).
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

    // ---------------------------------------------------------------
    // Download (git trees + git blobs)
    // ---------------------------------------------------------------

    /** Lists all files (blobs) in the whole repository, recursively. */
    public List<TreeEntry> getTree() throws Exception {
        JsonObject obj = getJson(URI.create(API + "/repos/" + owner + "/" + repo
                + "/git/trees/" + enc(branch) + "?recursive=1"));
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

    // ---------------------------------------------------------------
    // Upload (Git Data API - supports large files)
    // ---------------------------------------------------------------

    /**
     * Pushes a whole set of files as a single atomic commit to the configured branch.
     * Creates or updates the branch automatically.
     *
     * @param files      list of (path, bytes) to write; each path is repository-relative
     * @param message    commit message
     * @return number of files committed together
     */
    public int pushAll(List<FileData> files, String message) throws Exception {
        if (files.isEmpty()) return 0;

        // Current head commit + its tree (may be null if branch does not exist yet).
        String headCommit = null;
        String baseTree = null;
        try {
            JsonObject branchObj = getJson(URI.create(API + "/repos/" + owner + "/" + repo
                    + "/branches/" + enc(branch)));
            headCommit = branchObj.getAsJsonObject("commit").get("sha").getAsString();
            JsonObject commit = getJson(URI.create(API + "/repos/" + owner + "/" + repo
                    + "/git/commits/" + enc(headCommit)));
            baseTree = commit.getAsJsonObject("tree").get("sha").getAsString();
        } catch (GitHubApiException e) {
            if (e.code != 404) throw e;
            // Branch does not exist yet -> will be created on push.
        }

        // Create one blob per file.
        JsonArray treeItems = new JsonArray();
        for (FileData f : files) {
            String blobSha = createBlob(f.bytes());
            JsonObject item = new JsonObject();
            item.addProperty("path", f.path());
            item.addProperty("mode", "100644");
            item.addProperty("type", "blob");
            item.addProperty("sha", blobSha);
            treeItems.add(item);
        }

        // Build the new tree on top of the current one (so unrelated files stay intact).
        JsonObject treeBody = new JsonObject();
        treeBody.add("tree", treeItems);
        if (baseTree != null) {
            treeBody.addProperty("base_tree", baseTree);
        }
        JsonObject treeRes = postJson(URI.create(API + "/repos/" + owner + "/" + repo + "/git/trees"), treeBody);
        String newTree = treeRes.get("sha").getAsString();

        // Create the commit.
        JsonObject commitBody = new JsonObject();
        commitBody.addProperty("message", message);
        commitBody.addProperty("tree", newTree);
        if (headCommit != null) {
            JsonArray parents = new JsonArray();
            parents.add(headCommit);
            commitBody.add("parents", parents);
        }
        JsonObject commitRes = postJson(URI.create(API + "/repos/" + owner + "/" + repo + "/git/commits"), commitBody);
        String commitSha = commitRes.get("sha").getAsString();

        // Update (or create) the branch reference to the new commit.
        String ref = "refs/heads/" + branch;
        if (headCommit != null) {
            patchJson(URI.create(API + "/repos/" + owner + "/" + repo + "/git/refs/" + ref),
                    shaBody(commitSha));
        } else {
            JsonObject create = new JsonObject();
            create.addProperty("ref", ref);
            create.addProperty("sha", commitSha);
            postJson(URI.create(API + "/repos/" + owner + "/" + repo + "/git/refs"), create);
        }
        return files.size();
    }

    private String createBlob(byte[] bytes) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("content", Base64.getEncoder().encodeToString(bytes));
        body.addProperty("encoding", "base64");
        JsonObject res = postJson(URI.create(API + "/repos/" + owner + "/" + repo + "/git/blobs"), body);
        return res.get("sha").getAsString();
    }

    private static JsonObject shaBody(String sha) {
        JsonObject o = new JsonObject();
        o.addProperty("sha", sha);
        return o;
    }

    // ---------------------------------------------------------------
    // Low-level HTTP + JSON helpers
    // ---------------------------------------------------------------

    private JsonObject getJson(URI uri) throws Exception {
        HttpResponse<String> r = send("GET", uri, null);
        return parseOrThrow(r);
    }

    private JsonObject postJson(URI uri, JsonObject body) throws Exception {
        HttpResponse<String> r = send("POST", uri, body);
        return parseOrThrow(r);
    }

    private JsonObject patchJson(URI uri, JsonObject body) throws Exception {
        HttpResponse<String> r = send("PATCH", uri, body);
        return parseOrThrow(r);
    }

    private JsonObject parseOrThrow(HttpResponse<String> r) throws GitHubApiException {
        if (r.statusCode() >= 300) {
            throw new GitHubApiException(r.statusCode(), r.body());
        }
        String body = r.body();
        try {
            JsonElement el = JsonParser.parseString(body);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
            // Response was not a JSON object (e.g. an array or empty).
            throw new GitHubApiException(r.statusCode(),
                    "Antwort ist kein JSON-Objekt (Status " + r.statusCode() + "): " + truncate(body));
        } catch (GitHubApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GitHubApiException(r.statusCode(),
                    "Unlesbare JSON-Antwort (Status " + r.statusCode() + "): " + truncate(body));
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    private HttpResponse<String> send(String method, URI uri, JsonObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(120));
        switch (method) {
            case "GET" -> b.GET();
            case "POST", "PATCH", "PUT" -> {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(
                        body == null ? "{}" : body.toString(), StandardCharsets.UTF_8));
            }
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
