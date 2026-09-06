package net.zerocloud.pdf;

import java.time.Duration;
import java.util.List;
import net.zerocloud.pdf.provider.ProviderCatalog;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;
import net.zerocloud.pdf.provider.ProviderSelection;
import net.zerocloud.pdf.provider.ShapingRequest;
import net.zerocloud.pdf.provider.ShapingResult;

/** Transaction-scoped invocation of explicitly selected external shaping. */
final class ShapingCoordinator {
    private final ProviderCatalog catalog;
    private final WorkflowRequest workflow;
    private final ProviderSelection selected;

    ShapingCoordinator(ProviderCatalog catalog, WorkflowRequest workflow,
            List<ProviderSelection> selections) {
        this.catalog = catalog;
        this.workflow = workflow;
        ProviderSelection shaping = null;
        int index = 0;
        for (String capability : workflow.getProviderPreferences().keySet()) {
            if (ShapingRequest.CAPABILITY_ID.equals(capability)) { shaping = selections.get(index); }
            index++;
        }
        this.selected = shaping;
    }

    boolean enabled() { return selected != null; }
    ProviderMetadata metadata() { return selected.getMetadata(); }

    byte[] executeForWorker(byte[] payload, WorkflowResourceContext resources) throws DocumentFailure {
        if (!enabled() || payload.length > selected.getMetadata().getLimits().getMaximumInputBytes()
                || payload.length > ShapingRequest.MAXIMUM_PAYLOAD_BYTES) { throw failed(); }
        long maximumOutput = Math.min(32L + 24L * ShapingRequest.MAXIMUM_GLYPHS,
                metadata().getLimits().getMaximumOutputBytes());
        try (WorkflowResourceContext.MemoryReservation invocation = resources.reserveOwnedMemory(
                10L * payload.length + 10L * maximumOutput)) {
            ShapingRequest shaping = ShapingRequest.decode(payload);
            ProviderResult result = execute(payload, resources);
            ShapingResult validated = ShapingResult.decode(result, shaping);
            if (!metadata().getEngineVersion().equals(validated.getEngineVersion())) { throw failed(); }
            resources.retainOwnedMemory(result.getOutputLength());
            return result.getOutput();
        } catch (ProviderFailure failure) {
            resources.rethrowTerminalFailure();
            throw RenderingCoordinator.documentFailure(failure);
        } catch (IllegalArgumentException failure) { throw failed(); }
    }

    private ProviderResult execute(byte[] payload, WorkflowResourceContext resources)
            throws ProviderFailure, DocumentFailure {
        Duration timeout = metadata().getLimits().getMaximumDuration();
        Duration remaining = resources.remainingExecutionTime();
        if (timeout.compareTo(remaining) > 0) { timeout = remaining; }
        if (timeout.isZero() || timeout.isNegative()) { resources.checkpoint(); throw failed(); }
        ProviderRequest.Builder request = ProviderRequest.builder(ShapingRequest.CAPABILITY_ID, payload).timeout(timeout);
        if (workflow.isRemoteDisclosureAuthorized(ShapingRequest.CAPABILITY_ID)) { request.authorizeRemoteDisclosure(); }
        ProviderResult result = catalog.execute(request.build(), ProviderPreference.prefer(
                ShapingRequest.CAPABILITY_ID, metadata().getProviderId())).getResult();
        resources.checkpoint();
        return result;
    }

    ShapingResult shape(byte[] font, String text, String script, String language,
            ShapingRequest.Direction direction, WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership) throws DocumentFailure {
        ProviderMetadata metadata = selected.getMetadata();
        long payloadLength = 28L + font.length + 2L * text.length() + language.length();
        int glyphLimit = (int) Math.min(ShapingRequest.MAXIMUM_GLYPHS,
                Math.max(0, (metadata.getLimits().getMaximumOutputBytes() - 32L) / 24L));
        if (payloadLength > ShapingRequest.MAXIMUM_PAYLOAD_BYTES
                || payloadLength > metadata.getLimits().getMaximumInputBytes() || glyphLimit < 1) {
            throw failed();
        }
        long outputLimit = 32L + 24L * glyphLimit;
        // Covers request/encoding copies, adapter/result copies, and decoded
        // glyph/range storage while the external request remains live.
        try (WorkflowResourceContext.MemoryReservation invocation = resources.reserveOwnedMemory(
                10L * payloadLength + 4L * outputLimit + 128L * glyphLimit)) {
            ShapingRequest shaping = ShapingRequest.version1(font, text, script, language, direction, glyphLimit);
            ProviderResult output = execute(shaping.encode(), resources);
            ShapingResult result = ShapingResult.decode(output, shaping);
            if (!metadata.getEngineVersion().equals(result.getEngineVersion())) { throw failed(); }
            ownership.retain(128L * result.getGlyphs().size() + 2L * text.length());
            return result;
        } catch (ProviderFailure failure) {
            resources.rethrowTerminalFailure();
            throw RenderingCoordinator.documentFailure(failure);
        } catch (IllegalArgumentException failure) {
            throw failed();
        }
    }

    static DocumentFailure failed() {
        return new DocumentFailure(DocumentFailureCode.CAPABILITY_PROVIDER_FAILED,
                ShapingRequest.CAPABILITY_ID, "The Shaping Provider did not satisfy the declared profile.");
    }
}
