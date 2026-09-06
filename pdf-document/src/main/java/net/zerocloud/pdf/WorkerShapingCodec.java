package net.zerocloud.pdf;

import java.io.IOException;
import java.time.Duration;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ShapingRequest;

/** Closed initialization facts for the parent's selected shaping Provider. */
final class WorkerShapingCodec {
    private WorkerShapingCodec() { }

    static void write(WorkerCodecIO.Output output, WorkflowRequest request,
            WorkflowResourceContext resources) throws IOException {
        ProviderMetadata metadata = resources != null && resources.shapesComposition()
                ? resources.shaping().metadata() : null;
        output.writeBoolean(metadata != null);
        if (metadata == null) { return; }
        output.writeString(metadata.getProviderId());
        output.writeString(metadata.getEngineVersion());
        output.writeString(metadata.getExecutionMode().name());
        output.writeString(metadata.getDistribution().name());
        output.writeString(metadata.getEngineLicenseSpdxIdentifier());
        output.writeString(metadata.getEngineLicenseName());
        output.writeLong(metadata.getLimits().getMaximumInputBytes());
        output.writeLong(metadata.getLimits().getMaximumOutputBytes());
        output.writeLong(metadata.getLimits().getMaximumDuration().getSeconds());
        output.writeInt(metadata.getLimits().getMaximumDuration().getNano());
        output.writeBoolean(request.isRemoteDisclosureAuthorized(ShapingRequest.CAPABILITY_ID));
    }

    static ProviderMetadata read(WorkerCodecIO.Input input, WorkflowRequest.Builder request)
            throws DocumentFailure {
        if (!input.readBoolean()) { return null; }
        input.accountDecodedMemory(1024);
        try {
            String id = input.readString();
            ProviderMetadata metadata = ProviderMetadata.builder(id, input.readString())
                    .capability(ShapingRequest.CAPABILITY_ID)
                    .executionMode(ProviderExecutionMode.valueOf(input.readString()))
                    .distribution(ProviderDistribution.valueOf(input.readString()))
                    .engineLicense(input.readString(), input.readString())
                    .availability(ProviderAvailability.AVAILABLE)
                    .limits(ProviderLimits.bounded(input.readLong(), input.readLong(),
                            Duration.ofSeconds(input.readLong(), input.readInt()))).build();
            request.providerPreference(ProviderPreference.prefer(ShapingRequest.CAPABILITY_ID, id));
            if (input.readBoolean()) { request.authorizeRemoteDisclosure(ShapingRequest.CAPABILITY_ID); }
            return metadata;
        } catch (IllegalArgumentException failure) {
            throw WorkerCodecIO.workerFailure(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker shaping value is invalid.");
        }
    }
}
