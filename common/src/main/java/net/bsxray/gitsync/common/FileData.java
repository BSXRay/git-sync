package net.bsxray.gitsync.common;

/**
 * A file to be written to (or read from) the repository: repository-relative path + content.
 * Author: bsxray
 */
public final class FileData {
    private final String path;
    private final byte[] bytes;

    public FileData(String path, byte[] bytes) {
        this.path = path;
        this.bytes = bytes;
    }

    public String path() { return path; }
    public byte[] bytes() { return bytes; }
}
