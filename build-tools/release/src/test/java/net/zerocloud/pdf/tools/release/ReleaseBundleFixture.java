package net.zerocloud.pdf.tools.release;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ReleaseBundleFixture {

    static final String VERSION = "0.1.0";
    static final List<String> POM_MODULES = Arrays.asList("pdf-parent", "pdf-bom");
    static final List<String> JAR_MODULES = Arrays.asList(
            "pdf-provider-contract",
            "pdf-document",
            "pdf-conversion",
            "pdf-migration-itext7",
            "pdf-migration-itext7-preview");

    private ReleaseBundleFixture() {
    }

    static Path create(Path output, Path repositoryRoot) throws Exception {
        Files.createDirectories(output.resolve("audit"));
        Path work = Files.createDirectories(output.resolveSibling(output.getFileName() + "-work"));
        Path gpgHome = Files.createDirectories(work.resolve("gnupg"));
        assertEquals(true, gpgHome.toFile().setReadable(false, false));
        assertEquals(true, gpgHome.toFile().setWritable(false, false));
        assertEquals(true, gpgHome.toFile().setExecutable(false, false));
        assertEquals(true, gpgHome.toFile().setReadable(true, true));
        assertEquals(true, gpgHome.toFile().setWritable(true, true));
        assertEquals(true, gpgHome.toFile().setExecutable(true, true));

        Path batch = work.resolve("key.batch");
        write(batch,
                "Key-Type: RSA\n"
                + "Key-Length: 2048\n"
                + "Key-Usage: sign\n"
                + "Name-Real: Folio PDF Release Rehearsal\n"
                + "Name-Email: release-rehearsal@folio-pdf.invalid\n"
                + "Expire-Date: 1d\n"
                + "%no-protection\n"
                + "%commit\n");
        run(work, "gpg", "--homedir", gpgHome.toString(), "--batch", "--generate-key",
                batch.toString());
        String listing = run(work, "gpg", "--homedir", gpgHome.toString(),
                "--batch", "--with-colons", "--fingerprint", "--list-secret-keys");
        String fingerprint = firstFingerprint(listing);

        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        for (String module : POM_MODULES) {
            addPrimary(entries, module, module + "-" + VERSION + ".pom",
                    pom(module, "pom").getBytes(StandardCharsets.UTF_8));
        }
        for (String module : JAR_MODULES) {
            addPrimary(entries, module, module + "-" + VERSION + ".pom",
                    pom(module, "jar").getBytes(StandardCharsets.UTF_8));
            addPrimary(entries, module, module + "-" + VERSION + ".jar",
                    jar(module, "main"));
            addPrimary(entries, module, module + "-" + VERSION + "-sources.jar",
                    jar(module, "sources"));
            addPrimary(entries, module, module + "-" + VERSION + "-javadoc.jar",
                    jar(module, "javadoc"));
        }
        byte[] sbomXml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<bom xmlns=\"http://cyclonedx.org/schema/bom/1.6\" version=\"1\">"
                + "<metadata><component type=\"library\"><group>net.zerocloud</group>"
                + "<name>pdf-parent</name><version>" + VERSION
                + "</version></component></metadata><components/></bom>\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] sbomJson = ("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\","
                + "\"version\":1,\"metadata\":{\"component\":{\"type\":\"library\","
                + "\"group\":\"net.zerocloud\",\"name\":\"pdf-parent\","
                + "\"version\":\"" + VERSION + "\"}},\"components\":[]}\n")
                .getBytes(StandardCharsets.UTF_8);
        addPrimary(entries, "pdf-parent", "pdf-parent-" + VERSION + "-cyclonedx.xml",
                sbomXml);
        addPrimary(entries, "pdf-parent", "pdf-parent-" + VERSION + "-cyclonedx.json",
                sbomJson);

        Path signingInput = Files.createDirectories(work.resolve("signing-input"));
        List<String> primaryNames = new ArrayList<String>(entries.keySet());
        for (String name : primaryNames) {
            Path input = signingInput.resolve(Integer.toHexString(name.hashCode()) + ".artifact");
            Path signature = input.resolveSibling(input.getFileName() + ".asc");
            Files.write(input, entries.get(name));
            run(work, "gpg", "--homedir", gpgHome.toString(), "--batch", "--yes",
                    "--armor", "--local-user", fingerprint, "--detach-sign", "--output",
                    signature.toString(), input.toString());
            entries.put(name + ".asc", Files.readAllBytes(signature));
        }

        List<String> checksumInputs = new ArrayList<String>(entries.keySet());
        for (String name : checksumInputs) {
            addChecksum(entries, name, "MD5", ".md5");
            addChecksum(entries, name, "SHA-1", ".sha1");
            addChecksum(entries, name, "SHA-256", ".sha256");
            addChecksum(entries, name, "SHA-512", ".sha512");
        }

        Path centralBundle = output.resolve("central-bundle.zip");
        writeZip(centralBundle, entries);

        Path publicKey = output.resolve("audit/test-signing-public-key.asc");
        ProcessBuilder export = new ProcessBuilder("gpg", "--homedir", gpgHome.toString(),
                "--batch", "--armor", "--export", fingerprint);
        export.directory(work.toFile());
        export.redirectError(ProcessBuilder.Redirect.INHERIT);
        export.redirectOutput(publicKey.toFile());
        assertEquals("gpg public-key export failed", 0, export.start().waitFor());

        write(output.resolve("audit/rehearsal.properties"),
                "format-version=1\n"
                + "release-version=" + VERSION + "\n"
                + "signing-identity=NON_PRODUCTION_TEST\n"
                + "test-signing-fingerprint=" + fingerprint + "\n"
                + "source-commit=0123456789abcdef0123456789abcdef01234567\n"
                + "source-date-epoch=1788144000\n"
                + "source-worktree-status=dirty\n"
                + "source-tree-sha256="
                + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n"
                + "central-server-id=central\n"
                + "central-credentials=TEMPORARY_DUMMY_REHEARSAL\n"
                + "central-base-url=http://127.0.0.1:1\n"
                + "central-uploaded=false\n"
                + "maven-version=3.9.16\n"
                + "gpg-version=2.4.4\n"
                + "rsync-version=3.2.7\n"
                + "central-publishing-maven-plugin=0.11.0\n"
                + "maven-gpg-plugin=3.2.8\n"
                + "maven-source-plugin=3.4.0\n"
                + "maven-javadoc-plugin=3.12.0\n"
                + "cyclonedx-maven-plugin=2.9.2\n"
                + "license-maven-plugin=2.7.1\n"
                + "dependency-check-maven=12.2.2\n"
                + "flatten-maven-plugin=1.7.3\n");
        Files.write(output.resolve("audit/sbom.xml"), sbomXml);
        Files.write(output.resolve("audit/sbom.json"), sbomJson);
        write(output.resolve("audit/license-report.txt"),
                "org.apache.pdfbox--pdfbox--3.0.8=Apache License 2.0\n"
                + "org.apache.pdfbox--pdfbox-io--3.0.8=Apache License 2.0\n"
                + "org.apache.pdfbox--fontbox--3.0.8=Apache License 2.0\n"
                + "commons-logging--commons-logging--1.4.0=Apache License 2.0\n");
        write(output.resolve("audit/dependency-check-report.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<analysis><scanInfo><engineVersion>12.2.2</engineVersion></scanInfo>"
                + "<dependencies><dependency><fileName>pdfbox-3.0.8.jar</fileName>"
                + "<packages><package id=\"pkg:maven/org.apache.pdfbox/pdfbox@3.0.8\"/>"
                + "</packages></dependency></dependencies></analysis>\n");
        write(output.resolve("audit/dependency-check-report.json"),
                "{\"reportSchema\":\"1.1\",\"dependencies\":[{\"fileName\":"
                + "\"pdfbox-3.0.8.jar\",\"vulnerabilities\":[]}]}\n");
        write(output.resolve("audit/dependency-check-report.html"),
                "<!doctype html><title>Dependency-Check</title><p>No known vulnerabilities.</p>\n");
        Files.copy(repositoryRoot.resolve("release/dependency-check-suppressions.xml"),
                output.resolve("audit/dependency-check-suppressions.xml"));
        write(output.resolve("audit/build-a.log"), "[INFO] BUILD SUCCESS\n");
        write(output.resolve("audit/build-b.log"), "[INFO] BUILD SUCCESS\n");
        write(output.resolve("audit/central-bundle.sha256"),
                ReleaseChecksums.digestHex("SHA-256", Files.readAllBytes(centralBundle))
                + "  central-bundle.zip\n");

        StringBuilder reproducibility = new StringBuilder();
        reproducibility.append("format-version=1\nresult=PASS\n");
        reproducibility.append("excluded-reason=OpenPGP signatures contain randomized or "
                + "time-dependent packet material; signatures and their derived checksums "
                + "are verified separately.\n");
        List<String> names = new ArrayList<String>(entries.keySet());
        Collections.sort(names);
        for (String name : names) {
            if (name.contains(".asc")) {
                reproducibility.append("EXCLUDED\t").append(name).append('\n');
            } else {
                reproducibility.append("MATCH\t")
                        .append(ReleaseChecksums.digestHex("SHA-256", entries.get(name)))
                        .append('\t').append(name).append('\n');
            }
        }
        write(output.resolve("audit/reproducibility.txt"), reproducibility.toString());
        return output;
    }

    static Path createSignedBuildTree(Path buildTree, Path repositoryRoot)
            throws Exception {
        Path fixture = Files.createDirectories(buildTree.resolveSibling(
                buildTree.getFileName() + "-central-fixture"));
        create(fixture, repositoryRoot);
        Map<String, byte[]> entries = readZip(fixture.resolve("central-bundle.zip"));
        for (String module : POM_MODULES) {
            materializeModule(entries, buildTree, module, "pom");
        }
        for (String module : JAR_MODULES) {
            materializeModule(entries, buildTree, module, "jar");
        }
        materializeAuditArtifact(entries, buildTree, "sbom.xml",
                "pdf-parent-" + VERSION + "-cyclonedx.xml");
        materializeAuditArtifact(entries, buildTree, "sbom.json",
                "pdf-parent-" + VERSION + "-cyclonedx.json");
        return buildTree;
    }

    static void removeCentralEntry(Path output, String name) throws IOException {
        Map<String, byte[]> entries = readZip(output.resolve("central-bundle.zip"));
        entries.remove(name);
        writeZip(output.resolve("central-bundle.zip"), entries);
    }

    static void replaceCentralEntry(Path output, String name, byte[] content)
            throws IOException {
        Map<String, byte[]> entries = readZip(output.resolve("central-bundle.zip"));
        entries.put(name, content);
        writeZip(output.resolve("central-bundle.zip"), entries);
    }

    static void addCentralEntry(Path output, String name, byte[] content) throws IOException {
        replaceCentralEntry(output, name, content);
    }

    private static void addPrimary(Map<String, byte[]> entries, String module,
            String fileName, byte[] content) {
        entries.put("net/zerocloud/" + module + "/" + VERSION + "/" + fileName, content);
    }

    private static String pom(String artifactId, String packaging) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion><groupId>net.zerocloud</groupId>"
                + "<artifactId>" + artifactId + "</artifactId><version>" + VERSION
                + "</version><packaging>" + packaging + "</packaging>"
                + "<name>Folio PDF by ZeroCloud - " + artifactId + "</name>"
                + "<description>Release rehearsal fixture for " + artifactId + "</description>"
                + "<url>https://github.com/zerocloud-sdk/folio-pdf</url>"
                + "<licenses><license><name>Apache License, Version 2.0</name>"
                + "<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>"
                + "</license></licenses><developers><developer><id>zerocloud-sdk</id>"
                + "<name>Folio PDF by ZeroCloud contributors</name></developer></developers>"
                + "<scm><connection>scm:git:https://github.com/zerocloud-sdk/folio-pdf.git"
                + "</connection><developerConnection>scm:git:ssh://git@github.com/"
                + "zerocloud-sdk/folio-pdf.git</developerConnection>"
                + "<url>https://github.com/zerocloud-sdk/folio-pdf</url>"
                + "<tag>HEAD</tag></scm></project>\n";
    }

    private static byte[] jar(String artifactId, String kind) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        String entryName;
        byte[] content;
        if ("main".equals(kind) && "pdf-migration-itext7".equals(artifactId)) {
            entryName = "META-INF/folio-pdf/migration-itext7.edition";
            content = "stable\n".getBytes(StandardCharsets.UTF_8);
        } else if ("main".equals(kind)) {
            entryName = "net/zerocloud/pdf/Fixture.class";
            content = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe };
        } else if ("sources".equals(kind)) {
            entryName = "net/zerocloud/pdf/Fixture.java";
            content = "package net.zerocloud.pdf;\n".getBytes(StandardCharsets.UTF_8);
        } else {
            entryName = "index.html";
            content = "<!doctype html><title>Javadoc</title>\n"
                    .getBytes(StandardCharsets.UTF_8);
        }
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
        zip.close();
        return bytes.toByteArray();
    }

    private static void addChecksum(Map<String, byte[]> entries, String name,
            String algorithm, String suffix) {
        entries.put(name + suffix,
                (ReleaseChecksums.digestHex(algorithm, entries.get(name)) + "\n")
                        .getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeZip(Path path, Map<String, byte[]> entries) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path));
        List<String> names = new ArrayList<String>(entries.keySet());
        Collections.sort(names);
        for (String name : names) {
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write(entries.get(name));
            zip.closeEntry();
        }
        zip.close();
    }

    static Map<String, byte[]> readZip(Path path) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                Files.newInputStream(path));
        ZipEntry entry;
        byte[] buffer = new byte[4096];
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            int count;
            while ((count = zip.read(buffer)) != -1) {
                content.write(buffer, 0, count);
            }
            entries.put(entry.getName(), content.toByteArray());
        }
        zip.close();
        return entries;
    }

    private static String firstFingerprint(String listing) {
        for (String line : listing.split("\\r?\\n")) {
            if (line.startsWith("fpr:")) {
                String[] fields = line.split(":", -1);
                if (fields.length > 9 && fields[9].matches("[0-9A-F]{40}")) {
                    return fields[9];
                }
            }
        }
        throw new IllegalStateException("GPG did not report a 40-character fingerprint");
    }

    private static String run(Path directory, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = read(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(Arrays.toString(command) + " failed:\n" + output);
        }
        return output;
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void materializeModule(Map<String, byte[]> entries, Path buildTree,
            String module, String packaging) throws IOException {
        String base = module + "-" + VERSION;
        materializeArtifact(entries, buildTree, module, base + ".pom");
        if ("jar".equals(packaging)) {
            materializeArtifact(entries, buildTree, module, base + ".jar");
            materializeArtifact(entries, buildTree, module, base + "-sources.jar");
            materializeArtifact(entries, buildTree, module, base + "-javadoc.jar");
        }
    }

    private static void materializeArtifact(Map<String, byte[]> entries, Path buildTree,
            String module, String fileName) throws IOException {
        Path target = "pdf-parent".equals(module) ? buildTree.resolve("target")
                : buildTree.resolve(module).resolve("target");
        String path = "net/zerocloud/" + module + "/" + VERSION + "/" + fileName;
        writeBytes(target.resolve(fileName), requiredEntry(entries, path));
        writeBytes(target.resolve(fileName + ".asc"),
                requiredEntry(entries, path + ".asc"));
    }

    private static void materializeAuditArtifact(Map<String, byte[]> entries,
            Path buildTree, String auditName, String centralName) throws IOException {
        String path = "net/zerocloud/pdf-parent/" + VERSION + "/" + centralName;
        writeBytes(buildTree.resolve("target").resolve("release-audit")
                        .resolve(auditName),
                requiredEntry(entries, path));
        writeBytes(buildTree.resolve("target").resolve("gpg").resolve("release-audit")
                        .resolve(auditName + ".asc"),
                requiredEntry(entries, path + ".asc"));
    }

    private static byte[] requiredEntry(Map<String, byte[]> entries, String path) {
        byte[] content = entries.get(path);
        if (content == null) {
            throw new IllegalStateException("fixture omitted " + path);
        }
        return content;
    }

    private static void writeBytes(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }
}
