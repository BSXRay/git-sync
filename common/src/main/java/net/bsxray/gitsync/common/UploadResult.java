package net.bsxray.gitsync.common;

/** Result of an upload operation. Author: bsxray */
public final class UploadResult {
    public final int uploaded;
    public final int errors;
    public final int total;

    public UploadResult(int uploaded, int errors, int total) {
        this.uploaded = uploaded;
        this.errors = errors;
        this.total = total;
    }
}
