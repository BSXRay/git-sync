package net.bsxray.gitsync.common;

/** Result of a download operation. Author: bsxray */
public final class DownloadResult {
    public final int downloaded;
    public final int errors;
    public final int removed;

    public DownloadResult(int downloaded, int errors, int removed) {
        this.downloaded = downloaded;
        this.errors = errors;
        this.removed = removed;
    }
}
