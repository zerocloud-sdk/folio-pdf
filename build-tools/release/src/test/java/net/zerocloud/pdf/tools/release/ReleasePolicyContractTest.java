package net.zerocloud.pdf.tools.release;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public final class ReleasePolicyContractTest {

    @Test
    public void productionReleaseIsProtectedSerializedAndLeastPrivilege() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path workflowPath = repositoryRoot.resolve(".github/workflows/release.yml");

        assertTrue("release workflow is missing", Files.isRegularFile(workflowPath));
        String workflow = new String(Files.readAllBytes(workflowPath), StandardCharsets.UTF_8);
        assertTrue("release workflow must use the protected maven-central environment",
                workflow.contains("environment: maven-central"));
        assertTrue("release workflow must serialize every release execution",
                workflow.contains("group: maven-central-release"));
        assertTrue("release workflow must retain queued release executions",
                workflow.contains("cancel-in-progress: false"));
        assertTrue("release workflow must grant only read access to contents",
                workflow.contains("permissions:\n  contents: read"));
        assertTrue("release runner image must be pinned instead of following latest",
                workflow.contains("runs-on: ubuntu-24.04"));

        int rehearsal = workflow.indexOf("Build and validate non-publishing rehearsal");
        int firstSecret = workflow.indexOf("secrets.");
        assertTrue("rehearsal must run before any production secret is referenced",
                rehearsal >= 0 && firstSecret > rehearsal);
    }

    @Test
    public void productionMavenConfigurationPinsIdentityAndManualCentralValidation()
            throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String pom = new String(Files.readAllBytes(repositoryRoot.resolve("pom.xml")),
                StandardCharsets.UTF_8);

        assertTrue("approved production fingerprint must be complete and pinned",
                pom.contains("C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8"));
        assertTrue("Central server id must be explicit",
                pom.contains("<publishingServerId>central</publishingServerId>"));
        assertTrue("automatic Central publication must be disabled",
                pom.contains("<autoPublish>false</autoPublish>"));
        assertTrue("direct profile use must default to bundle-only behavior",
                pom.contains("<central.skipPublishing>true</central.skipPublishing>"));
        assertTrue("Central upload must wait for validation",
                pom.contains("<waitUntil>validated</waitUntil>"));
        assertTrue("Central bundle output must be fixed at the reactor root",
                pom.contains("<forcedOutputDirectory>${maven.multiModuleProjectDirectory}"
                        + "/target/central-publishing</forcedOutputDirectory>"));
        assertTrue("Central staging directory must be fixed at the reactor root",
                pom.contains("<forcedStagingDirectory>${maven.multiModuleProjectDirectory}"
                        + "/target/central-staging</forcedStagingDirectory>"));
        assertTrue("GPG best-practices enforcement must be enabled",
                pom.contains("<bestPractices>true</bestPractices>"));
        assertTrue("source-tree copier must be exactly version-pinned",
                pom.contains("<release.rsync.version>3.2.7</release.rsync.version>"));
        assertTrue("Central Publisher must handle child module deploys",
                !pom.contains("<artifactId>central-publishing-maven-plugin</artifactId>\n"
                        + "            <extensions>true</extensions>\n"
                        + "            <inherited>false</inherited>"));

        String productionCommand = read(repositoryRoot.resolve("scripts/release-central"));
        assertTrue("only the protected production command may enable Central upload",
                productionCommand.contains("-Dcentral.skipPublishing=false"));

        String rehearsalCommand = read(repositoryRoot.resolve("scripts/release-rehearsal"));
        assertTrue("rehearsal must supply only a temporary dummy central server",
                rehearsalCommand.contains("<id>central</id>")
                        && rehearsalCommand.contains(
                                "folio-pdf-release-rehearsal-unused"));
        assertTrue("rehearsal evidence must disclose dummy Central credentials",
                rehearsalCommand.contains(
                        "central-credentials=TEMPORARY_DUMMY_REHEARSAL"));
    }

    @Test
    public void releaseVersionCanBeSelectedWithoutEditingTrackedPoms() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String parentPom = read(repositoryRoot.resolve("pom.xml"));
        assertTrue("root version must use Maven's CI-friendly revision placeholder",
                parentPom.contains("<version>${revision}</version>"));
        assertTrue("normal builds must retain the snapshot Release Train default",
                parentPom.contains("<revision>0.1.0-SNAPSHOT</revision>"));

        List<String> childPoms = Arrays.asList(
                "pdf-bom/pom.xml",
                "pdf-provider-contract/pom.xml",
                "pdf-document/pom.xml",
                "pdf-acceptance/pom.xml",
                "pdf-conversion/pom.xml",
                "pdf-migration-itext7/pom.xml",
                "pdf-migration-itext7-preview/pom.xml",
                "build-tools/inventory/pom.xml",
                "build-tools/release/pom.xml");
        for (String childPom : childPoms) {
            assertTrue(childPom + " must inherit the selected revision",
                    read(repositoryRoot.resolve(childPom))
                            .contains("<version>${revision}</version>"));
        }
    }

    @Test
    public void publishableSetExcludesRepositoryOnlyModulesAndMatchesTheBom() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String modules = read(repositoryRoot.resolve("release/modules.properties"));
        assertTrue(modules.contains("pdf-parent:pom"));
        assertTrue(modules.contains("pdf-bom:pom"));
        assertTrue(modules.contains("pdf-provider-contract:jar"));
        assertTrue(modules.contains("pdf-document:jar"));
        assertTrue(modules.contains("pdf-conversion:jar"));
        assertTrue(modules.contains("pdf-migration-itext7:jar"));
        assertTrue(modules.contains("pdf-migration-itext7-preview:jar"));
        assertTrue(modules.contains(
                "repository-only=pdf-acceptance,pdf-inventory-tool,pdf-release-tool"));

        String bom = read(repositoryRoot.resolve("pdf-bom/pom.xml"));
        assertTrue("Acceptance Evidence must not enter the BOM",
                !bom.contains("<artifactId>pdf-acceptance</artifactId>"));
        assertTrue("inventory tooling must not enter the BOM",
                !bom.contains("<artifactId>pdf-inventory-tool</artifactId>"));
        assertTrue("release tooling must not enter the BOM",
                !bom.contains("<artifactId>pdf-release-tool</artifactId>"));
    }

    @Test
    public void everyWorkflowActionIsImmutableAndPullRequestCiHasNoReleaseSecrets()
            throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String ci = read(repositoryRoot.resolve(".github/workflows/ci.yml"));
        String release = read(repositoryRoot.resolve(".github/workflows/release.yml"));

        assertTrue("pull-request CI must retain read-only contents permission",
                ci.contains("permissions:\n  contents: read"));
        assertTrue("pull-request CI must not reference protected secrets",
                !ci.contains("secrets."));
        assertTrue("release workflow must be manually dispatched",
                release.contains("workflow_dispatch:"));
        assertTrue("release workflow must not run for pull requests",
                !release.contains("pull_request:"));
        assertTrue("release workflow must not run for pushes",
                !release.contains("push:"));

        assertImmutableActionReferences(ci, ".github/workflows/ci.yml");
        assertImmutableActionReferences(release, ".github/workflows/release.yml");
    }

    @Test
    public void vulnerabilityGateIsFailClosedWithPublicExceptionAuthority() throws Exception {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        String pom = read(repositoryRoot.resolve("pom.xml"));
        String suppressions = read(repositoryRoot.resolve(
                "release/dependency-check-suppressions.xml"));

        assertTrue("high-severity findings must fail the release",
                pom.contains("<failBuildOnCVSS>7.0</failBuildOnCVSS>"));
        assertTrue("scanner failures must fail the release",
                pom.contains("<failOnError>true</failOnError>"));
        assertTrue("unused exceptions must fail the release",
                pom.contains("<failBuildOnUnusedSuppressionRule>true"
                        + "</failBuildOnUnusedSuppressionRule>"));
        assertTrue("the checked-in exception authority must be configured",
                pom.contains("release/dependency-check-suppressions.xml"));
        assertTrue("external hosted suppressions must not bypass the repository authority",
                pom.contains("<hostedSuppressionsEnabled>false"
                        + "</hostedSuppressionsEnabled>"));
        assertTrue("exception policy must name ADR-0032 acceptance",
                suppressions.contains("Lead Maintainer acceptance required by ADR-0032"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertImmutableActionReferences(String workflow, String path) {
        Matcher matcher = Pattern.compile("(?m)^[ \\t]*uses:[ \\t]+[^@\\s]+@([^\\s#]+)")
                .matcher(workflow);
        int actions = 0;
        while (matcher.find()) {
            actions++;
            assertTrue(path + " contains mutable Action reference " + matcher.group(1),
                    matcher.group(1).matches("[0-9a-f]{40}"));
        }
        assertTrue(path + " must contain at least one Action reference", actions > 0);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing system property " + name);
        }
        return value;
    }
}
