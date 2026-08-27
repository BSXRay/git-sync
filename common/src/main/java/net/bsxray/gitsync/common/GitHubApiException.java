package net.bsxray.gitsync.common;

/**
 * Exception thrown when the GitHub API returns an error.
 * Author: bsxray
 */
public final class GitHubApiException extends Exception {
    public final int code;

    public GitHubApiException(int code, String message) {
        super("GitHub API (" + code + "): " + message);
        this.code = code;
    }
}
