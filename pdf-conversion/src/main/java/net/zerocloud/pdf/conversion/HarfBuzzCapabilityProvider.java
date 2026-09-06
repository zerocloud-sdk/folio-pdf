package net.zerocloud.pdf.conversion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;
import net.zerocloud.pdf.provider.ShapingRequest;
import net.zerocloud.pdf.provider.ShapingResult;

/**
 * Project-owned HarfBuzz C adapter supervised by the bounded subprocess
 * Provider. The caller supplies an absolute helper path, an exact expected
 * native version and finite invocation limits. HarfBuzz and the helper are
 * installed separately; neither is discovered or bundled with this artifact.
 *
 * <p>Each invocation checks the native version and shaped payload. Only the
 * existing subprocess deadline, byte limits and staging cleanup are claimed;
 * native allocations are outside the PDF Hardened Worker's containment.</p>
 *
 * @since 0.1.0
 */
public final class HarfBuzzCapabilityProvider extends CapabilityProvider {

    /** Stable registration identity of the project-owned native adapter. */
    public static final String PROVIDER_ID = "harf-buzz.native";
    private final SubprocessCapabilityProvider subprocess;

    /**
     * Creates a registration for an explicitly installed native helper.
     * Registration alone does not opt a Workflow into Composition shaping.
     *
     * @param helper absolute executable path, invoked without a shell
     * @param stagingRoot caller-selected root for library-owned per-call staging
     * @param expectedVersion exact major.minor.micro native version; T29 fixes 10.2.0
     * @param limits finite input-byte, output-byte and elapsed-time bounds
     * @throws NullPointerException if a reference argument is null
     * @throws IllegalArgumentException for a relative helper path or malformed version
     */
    public HarfBuzzCapabilityProvider(Path helper, Path stagingRoot,
            String expectedVersion, ProviderLimits limits) {
        super(metadata(helper, expectedVersion, limits));
        this.subprocess = new SubprocessCapabilityProvider(getMetadata(),
                Collections.singletonList(helper.toString()), stagingRoot);
    }

    @Override
    protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
        ShapingRequest shaping;
        try {
            shaping = ShapingRequest.decode(request.getInput());
        } catch (IllegalArgumentException malformed) {
            throw failure(ProviderFailureCode.EXECUTION_FAILED);
        }
        ProviderResult output = subprocess.execute(request);
        ShapingResult result = ShapingResult.decode(output, shaping);
        if (!getMetadata().getEngineVersion().equals(result.getEngineVersion())) {
            throw failure(ProviderFailureCode.PROVIDER_UNAVAILABLE);
        }
        return output;
    }

    private static ProviderMetadata metadata(Path helper, String version, ProviderLimits limits) {
        Objects.requireNonNull(helper, "helper");
        if (!helper.isAbsolute()) {
            throw new IllegalArgumentException("The native helper path must be absolute");
        }
        if (!Objects.requireNonNull(version, "expectedVersion")
                .matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("An exact native engine version is required");
        }
        return ProviderMetadata.builder(PROVIDER_ID, version)
                .capability(ShapingRequest.CAPABILITY_ID)
                .executionMode(ProviderExecutionMode.SUBPROCESS)
                .availability(Files.isRegularFile(helper) && Files.isExecutable(helper)
                        ? ProviderAvailability.AVAILABLE : ProviderAvailability.UNAVAILABLE)
                .limits(limits).engineLicense("MIT-Old", "HarfBuzz MIT-Old")
                .distribution(ProviderDistribution.SEPARATELY_INSTALLED).build();
    }

    private ProviderFailure failure(ProviderFailureCode code) {
        return ProviderFailure.forProvider(code, PROVIDER_ID, ShapingRequest.CAPABILITY_ID);
    }
}
