package net.zerocloud.pdf.tools.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ReleaseBundleAssembler {

    private static final String GROUP_PATH = "net/zerocloud/";

    Assembly assemble(Path buildRoot, Path outputZip, Path repositoryRoot, String version) {
        List<String> errors = new ArrayList<String>();
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
                || version.endsWith("-SNAPSHOT")) {
            errors.add("bundle version must be a non-SNAPSHOT semantic version");
        }
        if (!Files.isDirectory(buildRoot)) {
            errors.add("build tree is required: " + buildRoot);
        }
        ReleaseModuleSet modules = ReleaseModuleSet.read(repositoryRoot, errors);
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        for (ReleaseModuleSet.Module module : modules.publishable) {
            addModule(entries, buildRoot, module, version, errors);
        }
        addAttachedAuditArtifact(entries, buildRoot, version, "sbom.xml",
                "pdf-parent-" + version + "-cyclonedx.xml", errors);
        addAttachedAuditArtifact(entries, buildRoot, version, "sbom.json",
                "pdf-parent-" + version + "-cyclonedx.json", errors);
        addChecksums(entries, errors);
        if (!errors.isEmpty()) {
            return new Assembly(errors, 0);
        }
        try {
            Path parent = outputZip.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeZip(outputZip, entries);
        } catch (IOException e) {
            errors.add("cannot write Central bundle: " + compact(e.getMessage()));
            return new Assembly(errors, 0);
        }
        return new Assembly(errors, entries.size());
    }

    private void addModule(Map<String, byte[]> entries, Path buildRoot,
            ReleaseModuleSet.Module module, String version, List<String> errors) {
        Path target = targetDirectory(buildRoot, module.artifactId);
        String base = module.artifactId + "-" + version;
        addArtifact(entries, pomArtifact(buildRoot, module.artifactId, base + ".pom"),
                module.artifactId, version,
                base + ".pom", errors);
        if ("jar".equals(module.packaging)) {
            addArtifact(entries, target.resolve(base + ".jar"), module.artifactId, version,
                    base + ".jar", errors);
            addArtifact(entries, target.resolve(base + "-sources.jar"), module.artifactId,
                    version, base + "-sources.jar", errors);
            addArtifact(entries, target.resolve(base + "-javadoc.jar"), module.artifactId,
                    version, base + "-javadoc.jar", errors);
        }
    }

    private Path targetDirectory(Path buildRoot, String artifactId) {
        return moduleDirectory(buildRoot, artifactId).resolve("target");
    }

    private Path moduleDirectory(Path buildRoot, String artifactId) {
        return "pdf-parent".equals(artifactId) ? buildRoot : buildRoot.resolve(artifactId);
    }

    private Path pomArtifact(Path buildRoot, String artifactId, String fileName) {
        Path targetPom = targetDirectory(buildRoot, artifactId).resolve(fileName);
        if (Files.isRegularFile(targetPom)) {
            return targetPom;
        }
        return moduleDirectory(buildRoot, artifactId).resolve(".flattened-pom.xml");
    }

    private void addAttachedAuditArtifact(Map<String, byte[]> entries, Path buildRoot,
            String version, String auditName, String centralName, List<String> errors) {
        String path = GROUP_PATH + "pdf-parent/" + version + "/" + centralName;
        Path artifact = buildRoot.resolve("target").resolve("release-audit")
                .resolve(auditName);
        addRequired(entries, path, artifact, errors);
        Path signature = buildRoot.resolve("target").resolve("gpg")
                .resolve("release-audit").resolve(auditName + ".asc");
        addOptional(entries, path + ".asc", signature, errors);
    }

    private void addArtifact(Map<String, byte[]> entries, Path artifact,
            String artifactId, String version, String fileName, List<String> errors) {
        String path = GROUP_PATH + artifactId + "/" + version + "/" + fileName;
        addRequired(entries, path, artifact, errors);
        addOptional(entries, path + ".asc",
                artifact.resolveSibling(artifact.getFileName().toString() + ".asc"),
                errors);
    }

    private void addRequired(Map<String, byte[]> entries, String path, Path source,
            List<String> errors) {
        if (!Files.isRegularFile(source)) {
            errors.add("missing built release artifact: " + source);
            return;
        }
        addFile(entries, path, source, errors);
    }

    private void addOptional(Map<String, byte[]> entries, String path, Path source,
            List<String> errors) {
        if (Files.isRegularFile(source)) {
            addFile(entries, path, source, errors);
        }
    }

    private void addFile(Map<String, byte[]> entries, String path, Path source,
            List<String> errors) {
        try {
            if (entries.containsKey(path)) {
                errors.add("duplicate Central bundle entry: " + path);
                return;
            }
            entries.put(path, Files.readAllBytes(source));
        } catch (IOException e) {
            errors.add("cannot read built release artifact " + source + ": "
                    + compact(e.getMessage()));
        }
    }

    private void addChecksums(Map<String, byte[]> entries, List<String> errors) {
        List<String> names = new ArrayList<String>(entries.keySet());
        for (String name : names) {
            byte[] content = entries.get(name);
            for (ReleaseChecksums.Checksum checksum : ReleaseChecksums.CENTRAL_CHECKSUMS) {
                entries.put(name + checksum.suffix,
                        (ReleaseChecksums.digestHex(checksum.algorithm, content) + "\n")
                                .getBytes(StandardCharsets.US_ASCII));
            }
        }
    }

    private void writeZip(Path outputZip, Map<String, byte[]> entries) throws IOException {
        List<String> names = new ArrayList<String>(entries.keySet());
        Collections.sort(names);
        ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputZip,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        try {
            for (String name : names) {
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(entries.get(name));
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    private static String compact(String value) {
        return value == null ? "unknown error"
                : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static final class Assembly {
        final List<String> errors;
        final int entries;

        private Assembly(List<String> errors, int entries) {
            this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
            this.entries = entries;
        }

        boolean isValid() {
            return errors.isEmpty();
        }
    }

}
