package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class RepositoryFileResolver {

    private RepositoryFileResolver() {
    }

    static void validate(
            Path repositoryRoot, String value, String path, List<String> errors) {
        resolve(repositoryRoot, value, path, errors);
    }

    static Path resolveForRead(Path repositoryRoot, String value) {
        return resolve(repositoryRoot, value, null, null);
    }

    private static Path resolve(
            Path repositoryRoot, String value, String path, List<String> errors) {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\\') >= 0) {
            report(errors, path, "repository paths must use forward slashes");
            return null;
        }

        Path relative;
        try {
            relative = Paths.get(value);
        } catch (RuntimeException e) {
            report(errors, path, "invalid repository path " + value);
            return null;
        }
        if (relative.isAbsolute()) {
            report(errors, path, "repository path must be relative: " + value);
            return null;
        }

        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            report(errors, path, "repository path escapes the repository root: " + value);
            return null;
        }

        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path realFile = resolved.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                report(errors, path,
                        "repository path escapes the repository root through symbolic links: "
                                + value);
                return null;
            }
            if (!Files.isRegularFile(realFile)) {
                report(errors, path, "referenced path is not a regular file: " + value);
                return null;
            }
            return realFile;
        } catch (IOException e) {
            if (!Files.exists(resolved)) {
                report(errors, path, "referenced file does not exist: " + value);
            } else {
                report(errors, path,
                        "cannot resolve referenced file " + value + ": " + e.getMessage());
            }
            return null;
        }
    }

    private static void report(List<String> errors, String path, String message) {
        if (errors != null) {
            errors.add(path + ": " + message);
        }
    }
}
