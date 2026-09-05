package net.zerocloud.pdf;

import java.time.Duration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.query.RenderPage;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderCatalog;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ProviderSelection;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;

/** Transaction-scoped selection of the real rendering boundary. */
final class RenderingCoordinator {
    static final ProviderMetadata DEFAULT_METADATA = ProviderMetadata.builder(
            Rendering.DEFAULT_PROVIDER_ID, "3.0.8")
            .capability(Rendering.CAPABILITY_ID)
            .availability(ProviderAvailability.AVAILABLE)
            .executionMode(ProviderExecutionMode.IN_PROCESS)
            .distribution(ProviderDistribution.BUNDLED)
            .engineLicense("Apache-2.0", "Apache License 2.0")
            .limits(ProviderLimits.bounded(Long.MAX_VALUE, Long.MAX_VALUE, Duration.ofNanos(Long.MAX_VALUE)))
            .build();

    private final ProviderCatalog catalog;
    private final WorkflowRequest request;
    private final List<ProviderSelection> selections;
    private ProviderSelection selected;

    RenderingCoordinator(ProviderCatalog catalog, WorkflowRequest request,
            List<ProviderSelection> selections) {
        this.catalog = catalog; this.request = request; this.selections = selections;
        if (request.getProviderPreferences().containsKey(Rendering.CAPABILITY_ID)) {
            int index = 0;
            for (String capability : request.getProviderPreferences().keySet()) {
                if (Rendering.CAPABILITY_ID.equals(capability)) { selected = selections.get(index); break; }
                index++;
            }
        }
    }

    ProviderSelection select() throws DocumentFailure {
        if (selected == null) {
            try {
                selected = catalog.select(ProviderPreference.any(Rendering.CAPABILITY_ID),
                        request.isRemoteDisclosureAuthorized(Rendering.CAPABILITY_ID), DEFAULT_METADATA);
                selections.add(selected);
            } catch (ProviderFailure failure) {
                throw documentFailure(failure);
            }
        }
        return selected;
    }

    boolean usesDefault() throws DocumentFailure {
        return Rendering.DEFAULT_PROVIDER_ID.equals(select().getProviderId());
    }

    RenderedPage renderExternal(RenderPage query, RenderingSnapshot snapshot,
            WorkflowResourceContext resources) throws DocumentFailure {
        ProviderMetadata metadata = select().getMetadata();
        long outputLength = 16L + 4L * snapshot.width * snapshot.height;
        if (outputLength > Integer.MAX_VALUE
                || outputLength > metadata.getLimits().getMaximumOutputBytes()) { throw providerFailed(); }
        long inputLimit = Math.min(Integer.MAX_VALUE, metadata.getLimits().getMaximumInputBytes());
        byte[] payload;
        try {
            if (java.nio.file.Files.size(snapshot.file) > inputLimit - 28L) { throw providerFailed(); }
            payload = WorkerCodecIO.encode(resources, (int) inputLimit, out -> {
                out.writeInt(0x46525131); // FRQ1
                out.writeInt(snapshot.width);
                out.writeInt(snapshot.height);
                out.writeDouble(snapshot.scale);
                out.writeInt(query.getOptions().getAnnotationMode().ordinal());
                out.writeFile(snapshot.file);
            });
        } catch (IOException failure) { throw providerFailed(); }
        try {
            Duration timeout = metadata.getLimits().getMaximumDuration();
            Duration remaining = resources.remainingExecutionTime();
            if (timeout.compareTo(remaining) > 0) {
                timeout = remaining;
            }
            if (timeout.isZero()) { throw providerFailed(); }
            // Reserve the fixed reply before invoking caller-side code: the
            // accepted ProviderResult overlaps the still-live request copies.
            try (WorkflowResourceContext.MemoryReservation reply = resources.reserveOwnedMemory(outputLength)) {
                ProviderResult result;
                try (WorkflowResourceContext.MemoryReservation copies =
                        resources.reserveOwnedMemory(2L * payload.length)) {
                    ProviderRequest.Builder builder = ProviderRequest.builder(Rendering.CAPABILITY_ID, payload)
                            .timeout(timeout);
                    if (request.isRemoteDisclosureAuthorized(Rendering.CAPABILITY_ID)) {
                        builder.authorizeRemoteDisclosure();
                    }
                    ProviderRequest providerRequest = builder.build();
                    result = catalog.execute(providerRequest, ProviderPreference.prefer(
                            Rendering.CAPABILITY_ID, metadata.getProviderId())).getResult();
                    resources.checkpoint();
                    if (result.getOutputLength() != outputLength) { throw providerFailed(); }
                } catch (ProviderFailure failure) {
                    throw documentFailure(failure);
                }
                try (WorkflowResourceContext.MemoryReservation copy = resources.reserveOwnedMemory(outputLength)) {
                    return decodeExternal(query, snapshot, result.getOutput(), resources);
                }
            }
        } finally {
            WorkerCodecIO.clearRetained(resources, payload);
        }
    }

    private static RenderedPage decodeExternal(RenderPage query, RenderingSnapshot snapshot,
            byte[] bytes, WorkflowResourceContext resources) throws DocumentFailure {
        Path file = null;
        long pixels = (long) snapshot.width * snapshot.height;
        if (bytes.length != 16L + 4L * pixels) { throw providerFailed(); }
        try (WorkflowResourceContext.MemoryReservation raster = resources.reserveOwnedMemory(4L * pixels)) {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != 0x46525331 || input.readInt() != snapshot.width
                    || input.readInt() != snapshot.height) { throw providerFailed(); }
            int mask = input.readInt();
            if (mask < 0 || mask >= 1 << RenderDiagnostic.values().length) { throw providerFailed(); }
            List<RenderDiagnostic> diagnostics = new ArrayList<RenderDiagnostic>();
            for (RenderDiagnostic diagnostic : RenderDiagnostic.values()) {
                if ((mask & 1 << diagnostic.ordinal()) != 0) { diagnostics.add(diagnostic); }
            }
            BufferedImage image = new BufferedImage(snapshot.width, snapshot.height, BufferedImage.TYPE_INT_ARGB);
            try {
                for (int y = 0; y < snapshot.height; y++) {
                    resources.checkpoint();
                    for (int x = 0; x < snapshot.width; x++) {
                        int rgb = input.readUnsignedByte() << 16 | input.readUnsignedByte() << 8
                                | input.readUnsignedByte();
                        image.setRGB(x, y, input.readUnsignedByte() << 24 | rgb);
                    }
                }
                file = resources.createTemporaryFile("render-", ".png");
                try (OutputStream output = resources.openTemporaryOutput(file)) {
                    RenderingPngWriter.write(image, query.getOptions(), output, resources);
                }
            } finally { image.flush(); }
            RenderedPage result = new RenderedPage(query.getPageNumber(), snapshot.width,
                    snapshot.height, diagnostics, file, resources);
            file = null;
            return result;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw providerFailed();
        } finally { resources.releaseTemporaryFile(file); }
    }

    private static DocumentFailure providerFailed() {
        return RenderedPage.failure(DocumentFailureCode.CAPABILITY_PROVIDER_FAILED,
                "The Rendering Provider did not satisfy the declared byte and pixel profile.");
    }

    static DocumentFailure documentFailure(ProviderFailure failure) {
        return new DocumentFailure(DocumentWorkflow.documentFailureCode(failure.getCode()),
                failure.getCapabilityId(), failure.getDiagnostic());
    }
}
