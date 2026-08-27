package net.bsxray.gitsync.common;

/**
 * A single file (blob) listed in the repository git tree.
 * Author: bsxray
 */
public final class TreeEntry {
    private final String path;
    private final String sha;
    private final long size;

    public TreeEntry(String path, String sha, long size) {
        this.path = path;
        this.sha = sha;
        this.size = size;
    }

    public String path() { return path; }
    public String sha() { return sha; }
    public long size() { return size; }
}
