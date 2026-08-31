package net.zerocloud.pdf.tools.release;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class ReleaseBundleValidator {

    private static final String GROUP_PATH = "net/zerocloud/";
    private static final String PRODUCTION_FINGERPRINT =
            "C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8";
    private static final List<String> REQUIRED_AUDIT_FILES = Arrays.asList(
            "rehearsal.properties",
            "test-signing-public-key.asc",
            "sbom.xml",
            "sbom.json",
            "license-report.txt",
            "dependency-check-report.xml",
            "dependency-check-report.json",
            "dependency-check-report.html",
            "dependency-check-suppressions.xml",
            "reproducibility.txt",
            "build-a.log",
            "build-b.log",
            "central-bundle.sha256");

    Validation validate(Path output, Path repositoryRoot) {
        List<String> errors = new ArrayList<String>();
        ReleaseModuleSet modules = ReleaseModuleSet.read(repositoryRoot, errors);
        Path bundlePath = output.resolve("central-bundle.zip");
        if (!Files.isRegularFile(bundlePath)) {
            errors.add("central-bundle.zip is required in " + output);
            return new Validation(errors, 0, 0);
        }
        Map<String, byte[]> entries = readBundle(bundlePath, errors);
        Properties rehearsal = readProperties(output.resolve("audit/rehearsal.properties"),
                "audit/rehearsal.properties", errors);
        String version = rehearsal.getProperty("release-version", "").trim();
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
                || version.endsWith("-SNAPSHOT")) {
            errors.add("release-version must be a non-SNAPSHOT semantic version");
        }

        Set<String> primaryArtifacts = expectedPrimaryArtifacts(modules, version);
        Set<String> expectedEntries = expectedEntries(primaryArtifacts);
        for (String expected : expectedEntries) {
            if (!entries.containsKey(expected)) {
                errors.add("missing required Central artifact: " + expected);
            }
        }
        for (String actual : entries.keySet()) {
            if (!expectedEntries.contains(actual)) {
                errors.add("unexpected Central bundle entry: " + actual);
            }
            String module = moduleName(actual);
            if (module != null && modules.repositoryOnly.contains(module)) {
                errors.add("repository-only module entered Central bundle: " + module);
            }
        }

        validatePoms(entries, modules, version, errors);
        validateMainJars(entries, modules, version, errors);
        int checksumCount = validateChecksums(entries, primaryArtifacts, errors);
        validateAudit(output, repositoryRoot, entries, rehearsal, version, errors);
        int verifiedSignatures = verifySignatures(output, entries, primaryArtifacts,
                rehearsal, errors);
        return new Validation(errors, verifiedSignatures, checksumCount);
    }

    private Map<String, byte[]> readBundle(Path path, List<String> errors) {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        long total = 0L;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.startsWith("/") || name.startsWith("\\")
                        || name.contains("../") || name.contains("..\\")) {
                    errors.add("unsafe Central bundle entry: " + name);
                    continue;
                }
                if (entries.containsKey(name)) {
                    errors.add("duplicate Central bundle entry: " + name);
                    continue;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (content.size() + count > 128 * 1024 * 1024
                            || total > 1024L * 1024L * 1024L) {
                        throw new IOException("Central bundle exceeds validation size limits");
                    }
                    content.write(buffer, 0, count);
                }
                entries.put(name, content.toByteArray());
            }
        } catch (IOException e) {
            errors.add("cannot read central-bundle.zip: " + compact(e.getMessage()));
        }
        if (entries.isEmpty()) {
            errors.add("central-bundle.zip contains no artifacts");
        }
        return entries;
    }

    private Set<String> expectedPrimaryArtifacts(ReleaseModuleSet modules, String version) {
        Set<String> result = new HashSet<String>();
        for (ReleaseModuleSet.Module module : modules.publishable) {
            String directory = GROUP_PATH + module.artifactId + "/" + version + "/";
            String base = module.artifactId + "-" + version;
            result.add(directory + base + ".pom");
            if ("jar".equals(module.packaging)) {
                result.add(directory + base + ".jar");
                result.add(directory + base + "-sources.jar");
                result.add(directory + base + "-javadoc.jar");
            }
        }
        String parentDirectory = GROUP_PATH + "pdf-parent/" + version + "/pdf-parent-"
                + version;
        result.add(parentDirectory + "-cyclonedx.xml");
        result.add(parentDirectory + "-cyclonedx.json");
        return result;
    }

    private Set<String> expectedEntries(Set<String> primaryArtifacts) {
        Set<String> result = new HashSet<String>();
        for (String artifact : primaryArtifacts) {
            result.add(artifact);
            result.add(artifact + ".asc");
            for (ReleaseChecksums.Checksum checksum : ReleaseChecksums.CENTRAL_CHECKSUMS) {
                result.add(artifact + checksum.suffix);
                result.add(artifact + ".asc" + checksum.suffix);
            }
        }
        return result;
    }

    private void validatePoms(Map<String, byte[]> entries, ReleaseModuleSet modules,
            String version, List<String> errors) {
        Element parent = null;
        for (ReleaseModuleSet.Module module : modules.publishable) {
            String path = artifactPath(module.artifactId, version,
                    module.artifactId + "-" + version + ".pom");
            byte[] content = entries.get(path);
            if (content == null) {
                continue;
            }
            Element project = parseXml(content, "Central POM " + path, errors);
            if (project == null) {
                continue;
            }
            if ("pdf-parent".equals(module.artifactId)) {
                parent = project;
            }
            requireText(project, "modelVersion", "4.0.0", path, errors);
            String groupId = directText(project, "groupId");
            Element pomParent = directElement(project, "parent");
            if (groupId == null && pomParent != null) {
                groupId = directText(pomParent, "groupId");
            }
            if (!"net.zerocloud".equals(groupId)) {
                errors.add("incomplete POM metadata in " + path + ": groupId");
            }
            requireText(project, "artifactId", module.artifactId, path, errors);
            String pomVersion = directText(project, "version");
            if (pomVersion == null && pomParent != null) {
                pomVersion = directText(pomParent, "version");
            }
            if (!version.equals(pomVersion)) {
                errors.add("incomplete POM metadata in " + path + ": release version");
            }
            String packaging = directText(project, "packaging");
            if (packaging == null) {
                packaging = "jar";
            }
            if (!module.packaging.equals(packaging)) {
                errors.add("incomplete POM metadata in " + path + ": packaging");
            }
            requireNonEmpty(project, "name", path, errors);
            requireNonEmpty(project, "description", path, errors);
            if (pomParent != null) {
                String parentArtifact = directText(pomParent, "artifactId");
                if (!"pdf-parent".equals(parentArtifact)) {
                    errors.add("incomplete POM metadata in " + path + ": parent artifact");
                }
            }
        }
        if (parent == null) {
            return;
        }
        for (ReleaseModuleSet.Module module : modules.publishable) {
            String path = artifactPath(module.artifactId, version,
                    module.artifactId + "-" + version + ".pom");
            byte[] content = entries.get(path);
            if (content == null) {
                continue;
            }
            Element project = parseXml(content, "Central POM " + path, errors);
            if (project == null) {
                continue;
            }
            requireInherited(project, parent, "url", path, errors);
            requireInherited(project, parent, "licenses", path, errors);
            requireInherited(project, parent, "developers", path, errors);
            requireInherited(project, parent, "scm", path, errors);
        }
    }

    private void validateMainJars(Map<String, byte[]> entries, ReleaseModuleSet modules,
            String version, List<String> errors) {
        for (ReleaseModuleSet.Module module : modules.publishable) {
            if (!"jar".equals(module.packaging)) {
                continue;
            }
            String path = artifactPath(module.artifactId, version,
                    module.artifactId + "-" + version + ".jar");
            byte[] content = entries.get(path);
            if (content == null) {
                continue;
            }
            boolean hasClass = false;
            boolean hasResourceContract = false;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    hasClass |= !entry.isDirectory() && entry.getName().endsWith(".class");
                    if ("META-INF/folio-pdf/migration-itext7.edition"
                            .equals(entry.getName())) {
                        hasResourceContract = "stable".equals(
                                new String(readAll(zip, 1024), StandardCharsets.UTF_8).trim());
                    }
                }
            } catch (IOException e) {
                errors.add("invalid publishable JAR " + path + ": "
                        + compact(e.getMessage()));
                continue;
            }
            if (modules.resourceContracts.contains(module.artifactId)) {
                if (!hasResourceContract) {
                    errors.add("resource-contract artifact is incomplete: " + path);
                }
            } else if (!hasClass) {
                errors.add("unimplemented placeholder artifact is not publishable: " + path);
            }
        }
    }

    private int validateChecksums(Map<String, byte[]> entries, Set<String> primaryArtifacts,
            List<String> errors) {
        int validated = 0;
        for (String artifact : primaryArtifacts) {
            for (String input : Arrays.asList(artifact, artifact + ".asc")) {
                byte[] content = entries.get(input);
                if (content == null) {
                    continue;
                }
                for (ReleaseChecksums.Checksum checksum
                        : ReleaseChecksums.CENTRAL_CHECKSUMS) {
                    byte[] published = entries.get(input + checksum.suffix);
                    if (published == null) {
                        continue;
                    }
                    String value = new String(published, StandardCharsets.US_ASCII).trim();
                    int separator = value.indexOf(' ');
                    if (separator >= 0) {
                        value = value.substring(0, separator);
                    }
                    String expected = digestHex(checksum.algorithm, content, errors);
                    if (!expected.equalsIgnoreCase(value)) {
                        errors.add("invalid published checksum: " + input + checksum.suffix);
                    } else {
                        validated++;
                    }
                }
            }
        }
        return validated;
    }

    private void validateAudit(Path output, Path repositoryRoot, Map<String, byte[]> entries,
            Properties rehearsal, String version, List<String> errors) {
        Path audit = output.resolve("audit");
        for (String required : REQUIRED_AUDIT_FILES) {
            if (!Files.isRegularFile(audit.resolve(required))) {
                errors.add("missing audit report: audit/" + required);
            }
        }
        if (!"1".equals(rehearsal.getProperty("format-version"))) {
            errors.add("audit/rehearsal.properties format-version must be 1");
        }
        if (!"NON_PRODUCTION_TEST".equals(rehearsal.getProperty("signing-identity"))) {
            errors.add("rehearsal signing identity must be clearly non-production");
        }
        if (!"central".equals(rehearsal.getProperty("central-server-id"))) {
            errors.add("rehearsal must record central-server-id=central");
        }
        if (!"TEMPORARY_DUMMY_REHEARSAL"
                .equals(rehearsal.getProperty("central-credentials"))) {
            errors.add("rehearsal must use only temporary dummy Central credentials");
        }
        if (!"false".equals(rehearsal.getProperty("central-uploaded"))) {
            errors.add("rehearsal must record central-uploaded=false");
        }
        if (!"http://127.0.0.1:1".equals(rehearsal.getProperty("central-base-url"))) {
            errors.add("rehearsal must use the unreachable Central contact guard");
        }
        String fingerprint = rehearsal.getProperty("test-signing-fingerprint", "");
        if (!fingerprint.matches("[0-9A-F]{40}")) {
            errors.add("test-signing-fingerprint must be a complete uppercase fingerprint");
        }
        if (PRODUCTION_FINGERPRINT.equals(fingerprint)) {
            errors.add("production signing identity cannot be used by rehearsal");
        }
        if (!rehearsal.getProperty("source-commit", "").matches("[0-9a-f]{40}")) {
            errors.add("rehearsal source-commit must be a complete commit id");
        }
        if (!rehearsal.getProperty("source-date-epoch", "").matches("[0-9]{9,}")) {
            errors.add("rehearsal source-date-epoch is required");
        }
        String sourceStatus = rehearsal.getProperty("source-worktree-status", "");
        if (!("clean".equals(sourceStatus) || "dirty".equals(sourceStatus))) {
            errors.add("rehearsal source-worktree-status must be clean or dirty");
        }
        if (!rehearsal.getProperty("source-tree-sha256", "").matches("[0-9a-f]{64}")) {
            errors.add("rehearsal source-tree-sha256 must be a lowercase SHA-256 digest");
        }
        validateToolVersions(repositoryRoot, rehearsal, errors);

        String parent = artifactPath("pdf-parent", version, "pdf-parent-" + version);
        compareAuditCopy(audit.resolve("sbom.xml"), entries.get(parent + "-cyclonedx.xml"),
                "audit/sbom.xml", errors);
        compareAuditCopy(audit.resolve("sbom.json"), entries.get(parent + "-cyclonedx.json"),
                "audit/sbom.json", errors);
        validateXmlFile(audit.resolve("sbom.xml"), "CycloneDX SBOM", "bom", errors);
        validateLicenseReport(audit.resolve("license-report.txt"), errors);
        validateVulnerabilityReport(audit.resolve("dependency-check-report.xml"), errors);
        compareAuditCopy(audit.resolve("dependency-check-suppressions.xml"),
                readFile(repositoryRoot.resolve("release/dependency-check-suppressions.xml"),
                        errors),
                "audit/dependency-check-suppressions.xml", errors);
        validateSuccessfulLog(audit.resolve("build-a.log"), "first clean build", errors);
        validateSuccessfulLog(audit.resolve("build-b.log"), "second clean build", errors);
        validateBundleHash(output.resolve("central-bundle.zip"),
                audit.resolve("central-bundle.sha256"), errors);
        validateReproducibility(audit.resolve("reproducibility.txt"), entries, errors);
    }

    private void validateToolVersions(Path repositoryRoot, Properties rehearsal,
            List<String> errors) {
        Element project = parseXml(readFile(repositoryRoot.resolve("pom.xml"), errors),
                "repository pom.xml", errors);
        if (project == null) {
            return;
        }
        Element properties = directElement(project, "properties");
        Map<String, String> expected = new LinkedHashMap<String, String>();
        expected.put("central-publishing-maven-plugin",
                "central.publishing.maven.plugin.version");
        expected.put("maven-gpg-plugin", "maven.gpg.plugin.version");
        expected.put("maven-source-plugin", "maven.source.plugin.version");
        expected.put("maven-javadoc-plugin", "maven.javadoc.plugin.version");
        expected.put("cyclonedx-maven-plugin", "cyclonedx.maven.plugin.version");
        expected.put("license-maven-plugin", "license.maven.plugin.version");
        expected.put("dependency-check-maven", "dependency.check.maven.version");
        expected.put("flatten-maven-plugin", "flatten.maven.plugin.version");
        for (Map.Entry<String, String> item : expected.entrySet()) {
            String configured = properties == null ? null
                    : directText(properties, item.getValue());
            if (configured == null || !configured.equals(rehearsal.getProperty(item.getKey()))) {
                errors.add("rehearsal tool version does not match pinned " + item.getKey());
            }
        }
        if (!"3.9.16".equals(rehearsal.getProperty("maven-version"))) {
            errors.add("rehearsal Maven version must be 3.9.16");
        }
        String pinnedGpg = properties == null ? null
                : directText(properties, "release.gpg.version");
        if (pinnedGpg == null || !pinnedGpg.equals(rehearsal.getProperty("gpg-version"))) {
            errors.add("rehearsal GPG version does not match the pinned release.gpg.version");
        }
        String pinnedRsync = properties == null ? null
                : directText(properties, "release.rsync.version");
        if (pinnedRsync == null
                || !pinnedRsync.equals(rehearsal.getProperty("rsync-version"))) {
            errors.add("rehearsal rsync version does not match the pinned release.rsync.version");
        }
    }

    private void validateLicenseReport(Path path, List<String> errors) {
        String report = readUtf8(path, errors);
        for (String coordinate : Arrays.asList("pdfbox", "pdfbox-io", "fontbox",
                "commons-logging")) {
            if (!report.contains(coordinate)) {
                errors.add("license report omits required dependency " + coordinate);
            }
        }
        if (!report.toLowerCase(Locale.ROOT).contains("apache")) {
            errors.add("license report omits dependency license names");
        }
    }

    private void validateVulnerabilityReport(Path path, List<String> errors) {
        Element report = parseXml(readFile(path, errors), "known-vulnerability report", errors);
        if (report == null) {
            return;
        }
        if (report.getElementsByTagNameNS("*", "engineVersion").getLength() == 0
                && report.getElementsByTagName("engineVersion").getLength() == 0) {
            errors.add("known-vulnerability report omits scanner version");
        }
        int dependencies = report.getElementsByTagNameNS("*", "dependency").getLength();
        if (dependencies == 0) {
            dependencies = report.getElementsByTagName("dependency").getLength();
        }
        if (dependencies == 0) {
            errors.add("known-vulnerability report contains no required dependencies");
        }
        NodeList vulnerabilities = elements(report, "vulnerability");
        for (int index = 0; index < vulnerabilities.getLength(); index++) {
            Element vulnerability = (Element) vulnerabilities.item(index);
            String name = directText(vulnerability, "name");
            double highestScore = highestCvssScore(vulnerability, errors);
            String severity = directText(vulnerability, "severity");
            if (highestScore >= 7.0d || "HIGH".equalsIgnoreCase(severity)
                    || "CRITICAL".equalsIgnoreCase(severity)) {
                errors.add("unresolved high-severity vulnerability in release report: "
                        + (empty(name) ? "unnamed finding" : name));
            }
        }
    }

    private double highestCvssScore(Element vulnerability, List<String> errors) {
        double highest = -1.0d;
        for (String scoreName : Arrays.asList("baseScore", "score")) {
            NodeList scores = elements(vulnerability, scoreName);
            for (int index = 0; index < scores.getLength(); index++) {
                String value = scores.item(index).getTextContent();
                try {
                    highest = Math.max(highest, Double.parseDouble(value.trim()));
                } catch (RuntimeException e) {
                    errors.add("known-vulnerability report contains invalid CVSS score: "
                            + compact(value));
                }
            }
        }
        return highest;
    }

    private static NodeList elements(Element parent, String localName) {
        NodeList namespaced = parent.getElementsByTagNameNS("*", localName);
        return namespaced.getLength() == 0
                ? parent.getElementsByTagName(localName)
                : namespaced;
    }

    private void validateReproducibility(Path path, Map<String, byte[]> entries,
            List<String> errors) {
        String content = readUtf8(path, errors);
        if (!content.contains("result=PASS")) {
            errors.add("reproducibility evidence must record result=PASS");
        }
        if (!content.contains("excluded-reason=OpenPGP")) {
            errors.add("reproducibility evidence must explain excluded signature material");
        }
        Set<String> recorded = new HashSet<String>();
        for (String line : content.split("\\r?\\n")) {
            String[] fields = line.split("\\t", -1);
            if (fields.length == 3 && "MATCH".equals(fields[0])) {
                byte[] entry = entries.get(fields[2]);
                if (entry == null) {
                    errors.add("reproducibility evidence references unknown entry " + fields[2]);
                } else if (!digestHex("SHA-256", entry, errors).equals(fields[1])) {
                    errors.add("reproducibility hash mismatch for " + fields[2]);
                }
                recorded.add(fields[2]);
            } else if (fields.length == 2 && "EXCLUDED".equals(fields[0])) {
                if (!fields[1].contains(".asc")) {
                    errors.add("reproducibility evidence excludes deterministic material: "
                            + fields[1]);
                }
                recorded.add(fields[1]);
            }
        }
        for (String entry : entries.keySet()) {
            if (!recorded.contains(entry)) {
                errors.add("reproducibility evidence omits Central entry " + entry);
            }
        }
    }

    private int verifySignatures(Path output, Map<String, byte[]> entries,
            Set<String> primaryArtifacts, Properties rehearsal, List<String> errors) {
        Path publicKey = output.resolve("audit/test-signing-public-key.asc");
        if (!Files.isRegularFile(publicKey)) {
            return 0;
        }
        String expectedFingerprint = rehearsal.getProperty("test-signing-fingerprint", "");
        Path temporary = null;
        int verified = 0;
        try {
            temporary = Files.createTempDirectory("folio-pdf-release-verify-");
            Path home = Files.createDirectory(temporary.resolve("gnupg"));
            try {
                Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                // The release workflow runs on Linux; non-POSIX development systems use defaults.
            }
            Command importResult = run(Arrays.asList("gpg", "--homedir", home.toString(),
                    "--batch", "--import", publicKey.toString()), temporary);
            if (importResult.exitCode != 0) {
                errors.add("cannot import emitted test signing public key: "
                        + compact(importResult.output));
                return 0;
            }
            Command keys = run(Arrays.asList("gpg", "--homedir", home.toString(), "--batch",
                    "--with-colons", "--fingerprint", "--list-keys"), temporary);
            if (!keys.output.contains(expectedFingerprint)) {
                errors.add("emitted public key does not match test-signing-fingerprint");
                return 0;
            }
            List<String> artifacts = new ArrayList<String>(primaryArtifacts);
            Collections.sort(artifacts);
            int index = 0;
            for (String artifact : artifacts) {
                byte[] content = entries.get(artifact);
                byte[] signature = entries.get(artifact + ".asc");
                if (content == null || signature == null) {
                    continue;
                }
                Path input = temporary.resolve("artifact-" + index);
                Path signaturePath = temporary.resolve("artifact-" + index + ".asc");
                index++;
                Files.write(input, content);
                Files.write(signaturePath, signature);
                Command result = run(Arrays.asList("gpg", "--homedir", home.toString(),
                        "--batch", "--status-fd", "1", "--verify",
                        signaturePath.toString(), input.toString()), temporary);
                String valid = "[GNUPG:] VALIDSIG " + expectedFingerprint;
                if (result.exitCode != 0 || !result.output.contains(valid)) {
                    errors.add("invalid detached signature: " + artifact + ".asc");
                } else {
                    verified++;
                }
            }
        } catch (IOException e) {
            errors.add("cannot verify detached signatures: " + compact(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("detached signature verification was interrupted");
        } finally {
            if (temporary != null) {
                deleteTree(temporary);
            }
        }
        return verified;
    }

    private void validateBundleHash(Path bundle, Path publishedHash, List<String> errors) {
        byte[] content = readFile(bundle, errors);
        String value = readUtf8(publishedHash, errors).trim();
        int separator = value.indexOf(' ');
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        if (!digestHex("SHA-256", content, errors).equalsIgnoreCase(value)) {
            errors.add("audit/central-bundle.sha256 does not match central-bundle.zip");
        }
    }

    private void validateSuccessfulLog(Path path, String name, List<String> errors) {
        if (!readUtf8(path, errors).contains("BUILD SUCCESS")) {
            errors.add(name + " did not record BUILD SUCCESS");
        }
    }

    private void compareAuditCopy(Path path, byte[] expected, String name,
            List<String> errors) {
        byte[] actual = readFile(path, errors);
        if (expected != null && !Arrays.equals(expected, actual)) {
            errors.add(name + " does not match the signed Central artifact");
        }
    }

    private void validateXmlFile(Path path, String name, String rootName,
            List<String> errors) {
        Element root = parseXml(readFile(path, errors), name, errors);
        if (root != null && !rootName.equals(root.getLocalName())
                && !rootName.equals(root.getNodeName())) {
            errors.add(name + " has unexpected root element " + root.getNodeName());
        }
    }

    private Properties readProperties(Path path, String name, List<String> errors) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(path)) {
            errors.add("missing audit report: " + name);
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            errors.add("cannot read " + name + ": " + compact(e.getMessage()));
        }
        return properties;
    }

    private Element parseXml(byte[] content, String name, List<String> errors) {
        if (content == null || content.length == 0) {
            errors.add(name + " is empty");
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content));
            return document.getDocumentElement();
        } catch (Exception e) {
            errors.add("invalid " + name + ": " + compact(e.getMessage()));
            return null;
        }
    }

    private void requireText(Element parent, String name, String expected, String path,
            List<String> errors) {
        if (!expected.equals(directText(parent, name))) {
            errors.add("incomplete POM metadata in " + path + ": " + name);
        }
    }

    private void requireNonEmpty(Element parent, String name, String path,
            List<String> errors) {
        if (empty(directText(parent, name))) {
            errors.add("incomplete POM metadata in " + path + ": " + name);
        }
    }

    private void requireInherited(Element project, Element parent, String name, String path,
            List<String> errors) {
        if (directElement(project, name) == null && directElement(parent, name) == null) {
            errors.add("incomplete POM metadata in " + path + ": " + name);
        }
    }

    private static Element directElement(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String directText(Element parent, String localName) {
        Element child = directElement(parent, localName);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent();
        return value == null ? null : value.trim();
    }

    private static byte[] readAll(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maximum) {
                throw new IOException("entry exceeds expected size");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String artifactPath(String module, String version, String file) {
        return GROUP_PATH + module + "/" + version + "/" + file;
    }

    private static String moduleName(String path) {
        if (!path.startsWith(GROUP_PATH)) {
            return null;
        }
        int end = path.indexOf('/', GROUP_PATH.length());
        return end < 0 ? null : path.substring(GROUP_PATH.length(), end);
    }

    private static String digestHex(String algorithm, byte[] content, List<String> errors) {
        try {
            return ReleaseChecksums.digestHex(algorithm, content);
        } catch (IllegalStateException e) {
            errors.add("JDK does not provide required digest " + algorithm);
            return "";
        }
    }

    private static byte[] readFile(Path path, List<String> errors) {
        if (!Files.isRegularFile(path)) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            errors.add("cannot read " + path + ": " + compact(e.getMessage()));
            return new byte[0];
        }
    }

    private static String readUtf8(Path path, List<String> errors) {
        return new String(readFile(path, errors), StandardCharsets.UTF_8);
    }

    private static Command run(List<String> arguments, Path directory)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(readAll(input, 4 * 1024 * 1024), StandardCharsets.UTF_8);
        }
        return new Command(process.waitFor(), output);
    }

    private static void deleteTree(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                        throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Validation has already completed; temporary cleanup failure is non-evidence.
        }
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String compact(String value) {
        return value == null ? "unknown error"
                : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static final class Validation {
        final List<String> errors;
        final int verifiedSignatures;
        final int validatedChecksums;

        private Validation(List<String> errors, int verifiedSignatures,
                int validatedChecksums) {
            this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
            this.verifiedSignatures = verifiedSignatures;
            this.validatedChecksums = validatedChecksums;
        }

        boolean isValid() {
            return errors.isEmpty();
        }
    }

    private static final class Command {
        private final int exitCode;
        private final String output;

        private Command(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
