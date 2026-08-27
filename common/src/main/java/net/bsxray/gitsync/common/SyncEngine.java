package net.bsxray.gitsync.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Performs the actual file upload/download between the local server folders
 * and the GitHub repository.
 * Author: bsxray
 */
public final class SyncEngine {

    private SyncEngine() {
    }

    /** Recursively uploads the configured local folder to the repository. */
    public static UploadResult upload(Config cfg, Consumer<String> log) throws Exception {
        GitHubApi api = new GitHubApi(cfg.owner, cfg.repo, cfg.token, cfg.branch);
        Path local = Paths.get(cfg.localPath).toAbsolutePath().normalize();

        if (!Files.isDirectory(local)) {
            throw new IOException("Der konfigurierte Pfad ist kein gültiger Ordner: " + cfg.localPath);
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(local)) {
            s.filter(Files::isRegularFile).forEach(files::add);
        }

        int count = 0;
        int errors = 0;
        for (Path f : files) {
            String rel = local.relativize(f).toString().replace('\\', '/');
            String remote = join(cfg.repoPathOrRoot(), rel);
            try {
                byte[] data = Files.readAllBytes(f);
                api.uploadFile(remote, data, cfg.commitMessage);
                count++;
                log.accept("[GitSync] Hochgeladen: " + remote);
            } catch (Exception e) {
                errors++;
                log.accept("[GitSync] FEHLER bei " + remote + ": " + e.getMessage());
            }
        }
        return new UploadResult(count, errors, files.size());
    }

    /**
     * Downloads the (relevant part of the) repository into the configured folder,
     * overriding existing files and optionally removing files not present in the repo.
     */
    public static DownloadResult download(Config cfg, Consumer<String> log) throws Exception {
        GitHubApi api = new GitHubApi(cfg.owner, cfg.repo, cfg.token, cfg.branch);
        Path local = Paths.get(cfg.localPath).toAbsolutePath().normalize();
        Files.createDirectories(local);

        String prefix = cfg.repoPathOrRoot();
        List<TreeEntry> tree = api.getTree();
        Set<String> downloaded = new HashSet<>();

        int count = 0;
        int errors = 0;
        for (TreeEntry e : tree) {
            String path = e.path();
            if (!prefix.isEmpty() && !path.startsWith(prefix + "/")) {
                continue;
            }
            String rel = prefix.isEmpty() ? path : path.substring(prefix.length() + 1);
            if (rel.isEmpty()) continue;

            Path dest = local.resolve(rel).normalize();
            if (!dest.startsWith(local)) {
                errors++;
                log.accept("[GitSync] Sicherheitsblock: Pfad außerhalb des Zielordners: " + path);
                continue;
            }
            try {
                byte[] data = api.downloadBlob(e.sha());
                if (dest.getParent() != null) Files.createDirectories(dest.getParent());
                Files.write(dest, data);
                downloaded.add(rel);
                count++;
                log.accept("[GitSync] Heruntergeladen: " + rel);
            } catch (Exception ex) {
                errors++;
                log.accept("[GitSync] FEHLER bei " + path + ": " + ex.getMessage());
            }
        }

        int removed = 0;
        if (cfg.deleteExtraFiles) {
            try (Stream<Path> s = Files.walk(local)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    String rel = local.relativize(p).toString().replace('\\', '/');
                    if (!downloaded.contains(rel)) {
                        Files.deleteIfExists(p);
                        removed++;
                        log.accept("[GitSync] Entfernt (nicht in Repo): " + rel);
                    }
                }
            }
        }
        return new DownloadResult(count, errors, removed);
    }

    private static String join(String prefix, String rel) {
        if (prefix == null || prefix.isEmpty()) {
            return rel;
        }
        return prefix + "/" + rel;
    }
}
