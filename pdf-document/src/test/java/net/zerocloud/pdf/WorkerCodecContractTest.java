package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Internal fixed-point value-codec and resource-boundary contracts. */
public final class WorkerCodecContractTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void laterBatchMessageOverflowIdentifiesTheEncodablePrefix()
            throws Exception {
        List<DocumentCommand> commands = Arrays.<DocumentCommand>asList(
                AddBlankPage.INSTANCE,
                UpdateDocumentInfo.version1()
                        .set("large", PdfString.of(new byte[32]))
                        .build());

        try (WorkflowResourceContext resources = WorkflowResourceContext.open(
                WorkflowResourcePolicy.safeDefaults(),
                java.time.Clock.systemUTC(),
                CancellationToken.none(),
                null,
                temporaryFolder.getRoot().toPath());
                WorkerFontSourceCache fontSources =
                        new WorkerFontSourceCache(resources)) {
            try {
                WorkerCommandCodec.encodeBatch(
                        commands,
                        WorkerReferenceRegistry.forWorker(),
                        fontSources,
                        resources,
                        16);
                fail("Expected a later-entry message-size failure");
            } catch (WorkerCommandCodec.CommandEncodingFailure failure) {
                assertEquals(1, failure.getCommandIndex());
                assertEquals(
                        DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                        failure.getDocumentFailure().getCode());
            }
        }
    }

    @Test
    public void atomicBatchEligibilityAccountsFullTransportPeak() {
        List<DocumentCommand> singleton = Collections
                .<DocumentCommand>singletonList(AddBlankPage.INSTANCE);
        assertEquals(
                -1,
                WorkerCommandCodec.atomicEncodedLength(
                        singleton,
                        16,
                        39L));
        assertEquals(
                16,
                WorkerCommandCodec.atomicEncodedLength(
                        singleton,
                        16,
                        40L));

        List<DocumentCommand> commands = Collections
                .<DocumentCommand>singletonList(
                        InsertBlankPage.version1(1));

        assertEquals(
                -1,
                WorkerCommandCodec.atomicEncodedLength(
                        commands,
                        20,
                        51L));
        assertEquals(
                20,
                WorkerCommandCodec.atomicEncodedLength(
                        commands,
                        20,
                        52L));

        List<DocumentCommand> stagedFallback =
                Arrays.<DocumentCommand>asList(
                        AddBlankPage.INSTANCE,
                        AddBlankPage.INSTANCE);
        assertEquals(
                -1,
                WorkerCommandCodec.atomicEncodedLength(
                        stagedFallback,
                        24,
                        55L));
        assertEquals(
                24,
                WorkerCommandCodec.atomicEncodedLength(
                        stagedFallback,
                        24,
                        56L));
    }

    @Test
    public void finishedControlUsesExactAllowlistedTokens() throws Exception {
        List<String> capabilities = Arrays.asList(
                PdfBoxWorkflowEngine.CAPABILITY_ID,
                PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID,
                PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID,
                PdfBoxPageOperations.CAPABILITY_ID,
                PdfBoxMetadataOperations.CAPABILITY_ID,
                PdfBoxAnnotationOperations.CAPABILITY_ID,
                PdfBoxValueAdapter.CAPABILITY_ID,
                PdfBoxCanvasOperations.CAPABILITY_ID,
                PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                PdfBoxPositionedTextOperations.CAPABILITY_ID,
                PdfBoxTextStructureExtractionOperations.CAPABILITY_ID,
                PdfBoxImageResourceExtractionOperations.CAPABILITY_ID,
                Rendering.CAPABILITY_ID,
                PdfBoxParagraphOperations.CAPABILITY_ID);
        for (String capabilityId : capabilities) {
            byte[] payload = WorkerMessages.encodeFinished(
                    outcome(capabilityId),
                    8);
            try {
                assertEquals(8, payload.length);
                assertEquals(
                        capabilityId,
                        WorkerMessages.decodeFinished(payload));
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        }

        try {
            WorkerMessages.encodeFinished(outcome("unsupported"), 8);
            fail("Expected unsupported outcome capability rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
        try {
            WorkerMessages.encodeFinished(
                    outcome(PdfBoxWorkflowEngine.CAPABILITY_ID),
                    7);
            fail("Expected first-excess finished-control rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertEquals(PdfBoxParagraphOperations.CAPABILITY_ID,
                WorkerMessages.decodeFinished(values(output -> {
                    output.writeInt(1);
                    output.writeInt(14);
                })));
        try {
            WorkerMessages.decodeFinished(values(output -> {
                output.writeInt(1);
                output.writeInt(15);
            }));
            fail("Expected unknown outcome token rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
        assertInvalidResourceUsage(values(output -> {
            output.writeInt(2);
            for (int index = 0; index < 8; index++) {
                output.writeLong(0L);
            }
            output.writeInt(0);
        }));
        assertInvalidResourceUsage(values(output -> {
            output.writeInt(1);
            for (int index = 0; index < 7; index++) {
                output.writeLong(0L);
            }
            output.writeLong(1L);
            output.writeInt(-1);
        }));
        assertInvalidResourceUsage(values(output -> {
            output.writeInt(1);
            for (int index = 0; index < 8; index++) {
                output.writeLong(0L);
            }
            output.writeInt(1_000_000_000);
        }));
    }

    private static void assertInvalidResourceUsage(byte[] payload)
            throws Exception {
        try {
            WorkerMessages.decodeResourceUsage(payload, null);
            fail("Expected invalid resource usage rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        } finally {
            Arrays.fill(payload, (byte) 0);
        }
    }

    @Test
    public void resourceUsageControlIsFixedBoundedAndValidated()
            throws Exception {
        WorkflowResourceUsage usage = new WorkflowResourceUsage(
                11L,
                12L,
                13L,
                14L,
                15L,
                16L,
                17L,
                Duration.ofSeconds(18L, 19));
        byte[] payload = WorkerMessages.encodeResourceUsage(
                usage,
                WorkerMessages.RESOURCE_USAGE_BYTES);
        try {
            assertEquals(WorkerMessages.RESOURCE_USAGE_BYTES, payload.length);
            WorkflowResourceUsage decoded =
                    WorkerMessages.decodeResourceUsage(payload, null);
            assertEquals(11L, decoded.getAcceptedInputBytes());
            assertEquals(12L, decoded.getObservedPages());
            assertEquals(13L, decoded.getObservedObjects());
            assertEquals(14L, decoded.getDecompressedBytes());
            assertEquals(15L, decoded.getDecodedPixels());
            assertEquals(16L, decoded.getPeakOwnedMemoryBytes());
            assertEquals(17L, decoded.getPeakTemporaryStorageBytes());
            assertEquals(Duration.ofSeconds(18L, 19),
                    decoded.getElapsedTime());
        } finally {
            Arrays.fill(payload, (byte) 0);
        }

        try {
            WorkerMessages.encodeResourceUsage(
                    usage,
                    WorkerMessages.RESOURCE_USAGE_BYTES - 1);
            fail("Expected the first excess usage control");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        try {
            WorkerMessages.decodeResourceUsage(values(output -> {
                output.writeInt(1);
                output.writeLong(-1L);
                for (int index = 0; index < 6; index++) {
                    output.writeLong(0L);
                }
                output.writeLong(0L);
                output.writeInt(0);
            }), null);
            fail("Expected negative resource usage rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
    }

    @Test
    public void malformedScalarValuesAreRejected() throws Exception {
        try {
            WorkerCodecIO.input(values(output -> {
                output.writeInt(1);
                output.writeByte(0x80);
            })).readString();
            fail("Expected malformed string rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
        try {
            WorkerCodecIO.input(new byte[] {2}).readBoolean();
            fail("Expected noncanonical boolean rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
    }

    @Test
    public void outputPermissionsRequireTheCanonicalPublicMask()
            throws Exception {
        WorkerCodecIO.Input canonical = WorkerCodecIO.input(values(
                output -> output.writeInt(
                        DocumentPermissions.unrestricted()
                                .getStandardMask())));
        assertEquals(
                DocumentPermissions.unrestricted(),
                WorkerMessages.readCanonicalPermissions(canonical));
        canonical.requireFullyConsumed();

        WorkerCodecIO.Input forged = WorkerCodecIO.input(values(
                output -> output.writeInt(0)));
        try {
            WorkerMessages.readCanonicalPermissions(forged);
            fail("Expected a noncanonical permission mask rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
    }

    @Test
    public void recognizedFailurePartsCannotForgeAnUncatalogedTriple()
            throws Exception {
        DocumentFailure positioned = new DocumentFailure(
                DocumentFailureCode.PAGE_RANGE_INVALID,
                PdfBoxPositionedTextOperations.CAPABILITY_ID,
                "The positioned-text page selection is invalid.");
        int validToken = WorkerFailureCatalog.encode(positioned);
        int descriptorBase = validToken
                - validToken % WorkerFailureCatalog.capabilityCount();
        int forgedPageCapabilityToken = descriptorBase
                + WorkerFailureCatalog.capabilityIndex(
                        PdfBoxPageOperations.CAPABILITY_ID);

        try {
            WorkerFailureCatalog.decode(forgedPageCapabilityToken);
            fail("Expected exact failure-tuple rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
            assertEquals(HardenedWorkerEngine.CAPABILITY_ID,
                    failure.getCapabilityId());
        }
        try {
            WorkerFailureCatalog.encode(new DocumentFailure(
                    DocumentFailureCode.PAGE_RANGE_INVALID,
                    PdfBoxPageOperations.CAPABILITY_ID,
                    positioned.getDiagnostic()));
            fail("Expected exact failure-tuple rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        }
    }

    @Test
    public void decimalDecodeHonorsExactAndFirstExcessMemory()
            throws Exception {
        String lexical = "123456789012345678901234567890.0000000000";
        byte[] payload = values(output -> output.writeString(lexical));
        long stringBytes = 2L * lexical.length();
        long exactBudget = payload.length
                + stringBytes
                + WorkerCodecIO.decodedBigDecimalMemoryBytes(
                        lexical.length());
        assertDecimalMemoryBoundary(payload, exactBudget, true);
        assertDecimalMemoryBoundary(payload, exactBudget - 1L, false);
        Arrays.fill(payload, (byte) 0);
    }

    @Test
    public void defensiveBytesPreflightDoesNotInvokeOversizedSupplier()
            throws Exception {
        final boolean[] invoked = new boolean[1];
        try {
            WorkerCodecIO.encode(7, output -> output.writeDefensiveBytes(
                    4,
                    () -> {
                        invoked[0] = true;
                        return new byte[4];
                    }));
            fail("Expected message-size preflight rejection");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertFalse(invoked[0]);
    }

    @Test
    public void deepFixedPointTreesUseStackSafeCodecs() throws Exception {
        final int depth =
                WorkflowResourcePolicy.MAXIMUM_NESTING_DEPTH_VERSION_1;

        PdfValue nestedValue = PdfNull.INSTANCE;
        for (int index = 0; index < depth; index++) {
            nestedValue = PdfArray.of(nestedValue);
        }
        final PdfValue valueToEncode = nestedValue;
        byte[] valuePayload = WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerCommandCodec.writePdfValue(
                        output,
                        valueToEncode,
                        WorkerReferenceRegistry.forWorker(),
                        0));
        WorkerCodecIO.Input valueInput = WorkerCodecIO.input(valuePayload);
        PdfValue decodedValue = WorkerCommandCodec.readPdfValue(
                valueInput,
                WorkerReferenceRegistry.forWorker(),
                0);
        valueInput.requireFullyConsumed();
        int valueDepth = 0;
        while (decodedValue instanceof PdfArray) {
            decodedValue = ((PdfArray) decodedValue).get(0);
            valueDepth++;
        }
        assertEquals(depth, valueDepth);
        assertEquals(PdfNull.INSTANCE, decodedValue);

        OutlineItem outline = OutlineItem.grouping(
                "leaf",
                Collections.<OutlineItem>emptyList());
        for (int index = 0; index < depth; index++) {
            outline = OutlineItem.grouping(
                    "node-" + index,
                    Collections.singletonList(outline));
        }
        final OutlineItem outlineToEncode = outline;
        byte[] outlinePayload = WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerCommandCodec.writeOutlineItems(
                        output,
                        Collections.singletonList(outlineToEncode),
                        0));
        WorkerCodecIO.Input outlineInput = WorkerCodecIO.input(outlinePayload);
        OutlineItem decodedOutline = WorkerCommandCodec.readOutlineItems(
                outlineInput,
                0).get(0);
        outlineInput.requireFullyConsumed();
        int outlineDepth = 0;
        while (!decodedOutline.getChildren().isEmpty()) {
            decodedOutline = decodedOutline.getChildren().get(0);
            outlineDepth++;
        }
        assertEquals(depth, outlineDepth);

        LogicalStructureElement structure = structureElement(
                depth,
                Collections.<LogicalStructureItem>emptyList());
        for (int index = depth - 1; index >= 0; index--) {
            structure = structureElement(
                    index,
                    Collections.singletonList(
                            LogicalStructureItem.element(structure)));
        }
        final TextStructureExtraction extraction =
                new TextStructureExtraction(
                        Collections.<PageText>emptyList(),
                        Collections.singletonList(structure),
                        Collections.<ExtractionDiagnostic>emptyList());
        byte[] structurePayload = WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerTextExtractionCodec.write(
                        output,
                        extraction));
        WorkerCodecIO.Input structureInput = WorkerCodecIO.input(
                structurePayload);
        LogicalStructureElement decodedStructure =
                WorkerTextExtractionCodec.read(structureInput)
                        .getStructureRoots().get(0);
        structureInput.requireFullyConsumed();
        int structureDepth = 0;
        while (!decodedStructure.getChildren().isEmpty()) {
            decodedStructure = decodedStructure.getChildren().get(0)
                    .getElement().get();
            structureDepth++;
        }
        assertEquals(depth, structureDepth);
    }

    @Test
    public void fixedPointCodecsRejectTheFirstExcessNestingLevel()
            throws Exception {
        final int maximum =
                WorkflowResourcePolicy.MAXIMUM_NESTING_DEPTH_VERSION_1;

        PdfValue value = PdfNull.INSTANCE;
        for (int index = 0; index <= maximum; index++) {
            value = PdfArray.of(value);
        }
        final PdfValue excessiveValue = value;
        assertNestingFailure(() -> WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerCommandCodec.writePdfValue(
                        output,
                        excessiveValue,
                        WorkerReferenceRegistry.forWorker(),
                        0)));
        byte[] encodedValue = WorkerCodecIO.encode(
                8_000_000,
                output -> {
                    for (int index = 0; index <= maximum; index++) {
                        output.writeByte(5);
                        output.writeInt(1);
                    }
                    output.writeByte(0);
                });
        assertNestingFailure(() -> WorkerCommandCodec.readPdfValue(
                WorkerCodecIO.input(encodedValue),
                WorkerReferenceRegistry.forWorker(),
                0));

        OutlineItem outline = OutlineItem.grouping(
                "leaf",
                Collections.<OutlineItem>emptyList());
        for (int index = 0; index <= maximum; index++) {
            outline = OutlineItem.grouping(
                    "node",
                    Collections.singletonList(outline));
        }
        final OutlineItem excessiveOutline = outline;
        assertNestingFailure(() -> WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerCommandCodec.writeOutlineItems(
                        output,
                        Collections.singletonList(excessiveOutline),
                        0)));
        byte[] encodedOutline = WorkerCodecIO.encode(
                8_000_000,
                output -> {
                    output.writeInt(1);
                    for (int index = 0; index < maximum + 2; index++) {
                        output.writeString("");
                        output.writeByte(0);
                        output.writeInt(index == maximum + 1 ? 0 : 1);
                    }
                });
        assertNestingFailure(() -> WorkerCommandCodec.readOutlineItems(
                WorkerCodecIO.input(encodedOutline),
                0));

        LogicalStructureElement structure = structureElement(
                maximum + 1,
                Collections.<LogicalStructureItem>emptyList());
        for (int index = maximum; index >= 0; index--) {
            structure = structureElement(
                    index,
                    Collections.singletonList(
                            LogicalStructureItem.element(structure)));
        }
        final TextStructureExtraction excessiveStructure =
                new TextStructureExtraction(
                        Collections.<PageText>emptyList(),
                        Collections.singletonList(structure),
                        Collections.<ExtractionDiagnostic>emptyList());
        assertNestingFailure(() -> WorkerCodecIO.encode(
                8_000_000,
                output -> WorkerTextExtractionCodec.write(
                        output,
                        excessiveStructure)));
        byte[] encodedStructure = WorkerCodecIO.encode(
                8_000_000,
                output -> {
                    output.writeInt(0);
                    output.writeInt(1);
                    for (int index = 0; index < maximum + 2; index++) {
                        output.writeInt(index);
                        output.writeString("Div");
                        output.writeNullableString(null);
                        output.writeString("STANDARD");
                        output.writeNullableString(null);
                        output.writeNullableString(null);
                        output.writeString("NONE");
                        output.writeNullableString(null);
                        output.writeNullableString(null);
                        output.writeInt(index == maximum + 1 ? 0 : 1);
                        if (index != maximum + 1) {
                            output.writeString("ELEMENT");
                        }
                    }
                    output.writeInt(0);
                });
        assertNestingFailure(() -> WorkerTextExtractionCodec.read(
                WorkerCodecIO.input(encodedStructure)));
    }

    private static byte[] values(WorkerCodecIO.Encoder encoder)
            throws DocumentFailure {
        return WorkerCodecIO.encode(4096, encoder);
    }

    private static WorkflowOutcome<Void> outcome(String capabilityId) {
        return new WorkflowOutcome<Void>(
                null,
                capabilityId,
                WorkflowExecutionProfile.HARDENED_WORKER,
                SaveMode.REWRITE,
                Collections.<String>emptyList(),
                Collections.<PublicationReceipt>emptyList(),
                Collections.emptyList());
    }

    private void assertDecimalMemoryBoundary(
            byte[] payload,
            long maximumMemory,
            boolean succeeds) throws Exception {
        try (WorkflowResourceContext resources = WorkflowResourceContext.open(
                    policyWithMemory(maximumMemory),
                    java.time.Clock.systemUTC(),
                    CancellationToken.none(),
                    null,
                    temporaryFolder.getRoot().toPath())) {
            resources.retainOwnedMemory(payload.length);
            byte[] retained = payload.clone();
            WorkerCodecIO.Input input = WorkerCodecIO.retainedInput(
                    retained,
                    resources);
            try {
                PdfNumber number = PdfNumber.of(input.readBigDecimal());
                input.requireFullyConsumed();
                if (!succeeds) {
                    fail("Expected a first-excess decimal memory failure");
                }
                assertEquals(
                        0,
                        new java.math.BigDecimal(
                                "123456789012345678901234567890")
                                .compareTo(number.decimalValue()));
            } catch (DocumentFailure failure) {
                if (succeeds) {
                    throw failure;
                }
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        failure.getCode());
            } finally {
                input.releaseDecodedMemory();
                input.clear();
            }
        }
    }

    private static WorkflowResourcePolicy policyWithMemory(long memory) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy
                .safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(memory)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
    }

    private static void assertNestingFailure(FailingCall call)
            throws Exception {
        try {
            call.run();
            fail("Expected the first excess nesting level to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    failure.getCode());
            assertEquals("The workflow nesting-depth limit was exceeded.",
                    failure.getDiagnostic());
        }
    }

    private interface FailingCall {
        void run() throws Exception;
    }

    private static LogicalStructureElement structureElement(
            int id,
            List<LogicalStructureItem> children) {
        return new LogicalStructureElement(
                id,
                "Div",
                "Div",
                LogicalStructureElement.RoleResolution.STANDARD,
                null,
                null,
                LogicalStructureElement.LanguageSource.NONE,
                null,
                null,
                children);
    }
}
