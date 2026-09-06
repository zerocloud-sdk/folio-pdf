package net.zerocloud.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasImageCapabilities;
import net.zerocloud.pdf.composition.query.InspectCanvasImageCapabilities;
import net.zerocloud.pdf.composition.query.InspectLargeTable;
import net.zerocloud.pdf.composition.LargeTableState;
import net.zerocloud.pdf.query.Actions;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.EmbeddedFiles;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.OutlineTree;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import net.zerocloud.pdf.query.RenderPage;
import net.zerocloud.pdf.query.XmpMetadata;

/** Explicit versioned whitelist for all library-owned Document Queries. */
final class WorkerQueryCodec {

    private static final int QUERY_VERSION = 1;
    private static final int RESULT_VERSION = 1;

    private static final int PAGE_COUNT = 1;
    private static final int DOCUMENT_VERSION = 2;
    private static final int DOCUMENT_SECURITY = 3;
    private static final int CANVAS_IMAGE_CAPABILITIES = 4;
    private static final int PAGE_OBJECT_REFERENCE = 5;
    private static final int DOCUMENT_ROOT_REFERENCE = 6;
    private static final int INSPECT_OBJECT = 7;
    private static final int DOCUMENT_INFO = 8;
    private static final int XMP_METADATA = 9;
    private static final int NAMED_DESTINATIONS = 10;
    private static final int OUTLINE_TREE = 11;
    private static final int EMBEDDED_FILES = 12;
    private static final int READ_EMBEDDED_FILE = 13;
    private static final int ANNOTATIONS = 14;
    private static final int ACTIONS = 15;
    private static final int EXTRACT_TEXT_STRUCTURE = 16;
    private static final int EXTRACT_IMAGES_RESOURCES = 17;
    private static final int RENDER_PAGE = 18;
    private static final int RENDER_SNAPSHOT = 19;
    private static final int LARGE_TABLE_STATE = 20;

    private WorkerQueryCodec() {
    }

    static byte[] encodePreflight(
            final DocumentQuery<?> query,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(QUERY_VERSION);
            output.writeBoolean(requiresExtractionPermission(query));
        });
    }

    static void applyPreflight(
            byte[] payload,
            PdfBoxDocumentSession session,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            WorkerCommandCodec.requireVersion(
                    input.readInt(),
                    QUERY_VERSION);
            boolean extraction = input.readBoolean();
            input.requireFullyConsumed();
            session.preflightWorkerQuery(extraction);
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static boolean requiresExtractionPermission(
            DocumentQuery<?> query) {
        return query instanceof InspectObject
                || query == DocumentInfo.INSTANCE
                || query instanceof XmpMetadata
                || query instanceof NamedDestinations
                || query instanceof OutlineTree
                || query instanceof EmbeddedFiles
                || query instanceof ReadEmbeddedFile
                || query instanceof Annotations
                || query instanceof Actions
                || query instanceof ExtractTextAndStructure
                || query instanceof ExtractImagesAndResources
                || query instanceof RenderPage
                || query instanceof RenderSnapshotQuery;
    }

    static byte[] encodeQuery(
            final DocumentQuery<?> query,
            final WorkerReferenceRegistry references,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(QUERY_VERSION);
            writeQuery(output, query, references);
        });
    }

    static DocumentQuery<?> decodeQuery(
            byte[] payload,
            WorkerReferenceRegistry references) throws DocumentFailure {
        return decodeQuery(payload, references, null);
    }

    static DocumentQuery<?> decodeQuery(
            byte[] payload,
            WorkerReferenceRegistry references,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = resources == null
                ? WorkerCodecIO.input(payload)
                : WorkerCodecIO.accountedInput(payload, resources);
        return decodeQuery(input, references);
    }

    static DocumentQuery<?> decodeQuery(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                QUERY_VERSION);
        try {
            DocumentQuery<?> query = readQuery(input, references);
            input.requireFullyConsumed();
            return query;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw WorkerCommandCodec.rejected(
                    "A Worker Query value is invalid.");
        }
    }

    static byte[] encodeResult(
            final DocumentQuery<?> query,
            final Object result,
            final WorkerReferenceRegistry references,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(RESULT_VERSION);
            writeResult(output, query, result, references);
        });
    }

    @SuppressWarnings("unchecked")
    static <R> R decodeResult(
            DocumentQuery<R> query,
            byte[] payload,
            WorkerReferenceRegistry references,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                RESULT_VERSION);
        try {
            Object result = query instanceof RenderPage
                    ? WorkerRenderingCodec.readResult(input, (RenderPage) query, resources)
                    : query instanceof RenderSnapshotQuery
                            ? WorkerRenderingCodec.readSnapshot(input, resources)
                            : readResult(input, query, references);
            input.requireFullyConsumed();
            return (R) result;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw WorkerCommandCodec.rejected(
                    "A Worker Query result is invalid.");
        }
    }

    private static void writeQuery(
            WorkerCodecIO.Output output,
            DocumentQuery<?> query,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        if (query instanceof InspectLargeTable) {
            output.writeInt(LARGE_TABLE_STATE);
            output.writeInt(((InspectLargeTable) query).getVersion());
        } else if (query == PageCount.INSTANCE) {
            output.writeInt(PAGE_COUNT);
            output.writeInt(1);
        } else if (query == DocumentVersion.INSTANCE) {
            output.writeInt(DOCUMENT_VERSION);
            output.writeInt(1);
        } else if (query == DocumentSecurity.INSTANCE) {
            output.writeInt(DOCUMENT_SECURITY);
            output.writeInt(1);
        } else if (query instanceof InspectCanvasImageCapabilities) {
            output.writeInt(CANVAS_IMAGE_CAPABILITIES);
            output.writeInt(((InspectCanvasImageCapabilities) query).getVersion());
        } else if (query instanceof PageObjectReference) {
            PageObjectReference value = (PageObjectReference) query;
            output.writeInt(PAGE_OBJECT_REFERENCE);
            output.writeInt(value.getVersion());
            output.writeInt(value.getPageNumber());
        } else if (query == DocumentRootReference.INSTANCE) {
            output.writeInt(DOCUMENT_ROOT_REFERENCE);
            output.writeInt(DocumentRootReference.VERSION);
        } else if (query instanceof InspectObject) {
            InspectObject value = (InspectObject) query;
            output.writeInt(INSPECT_OBJECT);
            output.writeInt(value.getVersion());
            references.write(output, value.getReference());
            output.writeLong(value.getLimits().getMaximumTraversedValues());
            output.writeLong(value.getLimits().getMaximumDecodedStreamBytes());
        } else if (query == DocumentInfo.INSTANCE) {
            output.writeInt(DOCUMENT_INFO);
            output.writeInt(DocumentInfo.VERSION_1);
        } else if (query instanceof XmpMetadata) {
            XmpMetadata value = (XmpMetadata) query;
            output.writeInt(XMP_METADATA);
            output.writeInt(value.getVersion());
            output.writeLong(value.getMaximumBytes());
        } else if (query instanceof NamedDestinations) {
            NamedDestinations value = (NamedDestinations) query;
            output.writeInt(NAMED_DESTINATIONS);
            output.writeInt(value.getVersion());
            output.writeInt(value.getMaximumEntries());
        } else if (query instanceof OutlineTree) {
            OutlineTree value = (OutlineTree) query;
            output.writeInt(OUTLINE_TREE);
            output.writeInt(value.getVersion());
            output.writeInt(value.getMaximumItems());
        } else if (query instanceof EmbeddedFiles) {
            EmbeddedFiles value = (EmbeddedFiles) query;
            output.writeInt(EMBEDDED_FILES);
            output.writeInt(value.getVersion());
            output.writeInt(value.getMaximumEntries());
        } else if (query instanceof ReadEmbeddedFile) {
            ReadEmbeddedFile value = (ReadEmbeddedFile) query;
            output.writeInt(READ_EMBEDDED_FILE);
            output.writeInt(value.getVersion());
            output.writeString(value.getName());
            output.writeLong(value.getMaximumBytes());
        } else if (query instanceof Annotations) {
            Annotations value = (Annotations) query;
            output.writeInt(ANNOTATIONS);
            output.writeInt(value.getVersion());
            output.writeInt(value.getMaximumAnnotations());
            output.writeLong(value.getMaximumAppearanceBytes());
            output.writeLong(value.getMaximumAttachmentBytes());
        } else if (query instanceof Actions) {
            Actions value = (Actions) query;
            output.writeInt(ACTIONS);
            output.writeInt(value.getVersion());
            output.writeInt(value.getMaximumActions());
        } else if (query instanceof ExtractTextAndStructure) {
            output.writeInt(EXTRACT_TEXT_STRUCTURE);
            writeExtractionQuery(output, (ExtractTextAndStructure) query);
        } else if (query instanceof RenderSnapshotQuery) {
            output.writeInt(RENDER_SNAPSHOT);
            WorkerRenderingCodec.writeQuery(output, ((RenderSnapshotQuery) query).render);
        } else if (query instanceof RenderPage) {
            output.writeInt(RENDER_PAGE);
            WorkerRenderingCodec.writeQuery(output, (RenderPage) query);
        } else if (query instanceof ExtractImagesAndResources) {
            output.writeInt(EXTRACT_IMAGES_RESOURCES);
            writeResourceQuery(output, (ExtractImagesAndResources) query);
        } else {
            throw new DocumentFailure(
                    DocumentFailureCode.QUERY_REJECTED,
                    PdfBoxWorkflowEngine.CAPABILITY_ID,
                    "The query is not supported by this workflow version.");
        }
    }

    private static DocumentQuery<?> readQuery(
            WorkerCodecIO.Input input,
            WorkerReferenceRegistry references) throws DocumentFailure {
        int opcode = input.readInt();
        switch (opcode) {
            case LARGE_TABLE_STATE:
                WorkerCommandCodec.requireVersion(input.readInt(), InspectLargeTable.VERSION_1);
                return InspectLargeTable.version1();
            case RENDER_PAGE:
                return WorkerRenderingCodec.readQuery(input);
            case RENDER_SNAPSHOT:
                return new RenderSnapshotQuery(WorkerRenderingCodec.readQuery(input));
            case PAGE_COUNT:
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                return PageCount.INSTANCE;
            case DOCUMENT_VERSION:
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                return DocumentVersion.INSTANCE;
            case DOCUMENT_SECURITY:
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                return DocumentSecurity.INSTANCE;
            case CANVAS_IMAGE_CAPABILITIES:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        InspectCanvasImageCapabilities.VERSION_1);
                return InspectCanvasImageCapabilities.version1();
            case PAGE_OBJECT_REFERENCE:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        PageObjectReference.VERSION_1);
                return PageObjectReference.version1(input.readInt());
            case DOCUMENT_ROOT_REFERENCE:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        DocumentRootReference.VERSION);
                return DocumentRootReference.INSTANCE;
            case INSPECT_OBJECT:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        InspectObject.VERSION_1);
                return InspectObject.version1(
                        references.read(input),
                        PdfInspectionLimits.of(
                                input.readLong(),
                                input.readLong()));
            case DOCUMENT_INFO:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        DocumentInfo.VERSION_1);
                return DocumentInfo.INSTANCE;
            case XMP_METADATA:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        XmpMetadata.VERSION_1);
                return XmpMetadata.version1(input.readLong());
            case NAMED_DESTINATIONS:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        NamedDestinations.VERSION_1);
                return NamedDestinations.version1(input.readInt());
            case OUTLINE_TREE:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        OutlineTree.VERSION_1);
                return OutlineTree.version1(input.readInt());
            case EMBEDDED_FILES:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        EmbeddedFiles.VERSION_1);
                return EmbeddedFiles.version1(input.readInt());
            case READ_EMBEDDED_FILE:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        ReadEmbeddedFile.VERSION_1);
                return ReadEmbeddedFile.version1(
                        input.readString(),
                        input.readLong());
            case ANNOTATIONS:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        Annotations.VERSION_1);
                return Annotations.version1(
                        input.readInt(),
                        input.readLong(),
                        input.readLong());
            case ACTIONS:
                WorkerCommandCodec.requireVersion(
                        input.readInt(),
                        Actions.VERSION_1);
                return Actions.version1(input.readInt());
            case EXTRACT_TEXT_STRUCTURE:
                return readExtractionQuery(input);
            case EXTRACT_IMAGES_RESOURCES:
                return readResourceQuery(input);
            default:
                throw WorkerCommandCodec.rejected(
                        "The Worker Query opcode is unsupported.");
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeResult(
            WorkerCodecIO.Output output,
            DocumentQuery<?> query,
            Object result,
            WorkerReferenceRegistry references)
            throws IOException, DocumentFailure {
        if (query instanceof InspectLargeTable) {
            LargeTableState state = (LargeTableState) result;
            output.writeString(state.getStage().name());
            output.writeInt(state.getAcceptedRows()); output.writeInt(state.getRetainedRows());
        } else if (query == PageCount.INSTANCE) {
            output.writeInt(((Integer) result).intValue());
        } else if (query == DocumentVersion.INSTANCE) {
            writeVersionInfo(output, (PdfVersionInfo) result);
        } else if (query == DocumentSecurity.INSTANCE) {
            writeSecurityInfo(output, (PasswordSecurityInfo) result);
        } else if (query instanceof InspectCanvasImageCapabilities) {
            writeCanvasImageCapabilities(
                    output,
                    (CanvasImageCapabilities) result);
        } else if (query instanceof PageObjectReference
                || query == DocumentRootReference.INSTANCE) {
            references.write(output, (ObjectReference) result);
        } else if (query instanceof InspectObject
                || query == DocumentInfo.INSTANCE) {
            WorkerCommandCodec.writePdfValue(
                    output,
                    (PdfValue) result,
                    references,
                    0);
        } else if (query instanceof XmpMetadata) {
            output.writeNullableBytes((byte[]) result);
        } else if (query instanceof NamedDestinations) {
            Map<String, PageDestination> values =
                    (Map<String, PageDestination>) result;
            output.writeInt(values.size());
            for (Map.Entry<String, PageDestination> entry : values.entrySet()) {
                output.writeString(entry.getKey());
                WorkerCommandCodec.writePageDestination(
                        output,
                        entry.getValue());
            }
        } else if (query instanceof OutlineTree) {
            WorkerCommandCodec.writeOutlineItems(
                    output,
                    (List<OutlineItem>) result,
                    0);
        } else if (query instanceof EmbeddedFiles) {
            writeEmbeddedFileSummaries(
                    output,
                    (List<EmbeddedFileSummary>) result);
        } else if (query instanceof ReadEmbeddedFile) {
            writeEmbeddedFileData(
                    output,
                    (Optional<EmbeddedFileData>) result);
        } else if (query instanceof Annotations) {
            List<Annotation> annotations = (List<Annotation>) result;
            output.writeInt(annotations.size());
            for (Annotation annotation : annotations) {
                WorkerAnnotationCodec.writeAnnotation(output, annotation);
            }
        } else if (query instanceof Actions) {
            writeDocumentActions(output, (DocumentActions) result);
        } else if (query instanceof ExtractTextAndStructure) {
            WorkerTextExtractionCodec.write(
                    output,
                    (TextStructureExtraction) result);
        } else if (query instanceof RenderSnapshotQuery) {
            WorkerRenderingCodec.writeSnapshot(output, (RenderingSnapshot) result);
        } else if (query instanceof RenderPage) {
            WorkerRenderingCodec.writeResult(output, (RenderedPage) result);
        } else if (query instanceof ExtractImagesAndResources) {
            WorkerResourceInventoryCodec.write(
                    output,
                    (DocumentResourceInventory) result,
                    references);
        } else {
            throw WorkerCommandCodec.rejected(
                    "The Worker Query result opcode is unsupported.");
        }
    }

    private static Object readResult(
            WorkerCodecIO.Input input,
            DocumentQuery<?> query,
            WorkerReferenceRegistry references) throws DocumentFailure {
        if (query instanceof InspectLargeTable) {
            LargeTableState.Stage stage = WorkerCommandCodec.enumValue(LargeTableState.Stage.class,
                    input.readString(), "Large table stage");
            return LargeTableState.version1(stage, input.readInt(), input.readInt());
        }
        if (query == PageCount.INSTANCE) {
            return Integer.valueOf(input.readInt());
        }
        if (query == DocumentVersion.INSTANCE) {
            return readVersionInfo(input);
        }
        if (query == DocumentSecurity.INSTANCE) {
            return readSecurityInfo(input);
        }
        if (query instanceof InspectCanvasImageCapabilities) {
            return readCanvasImageCapabilities(input);
        }
        if (query instanceof PageObjectReference
                || query == DocumentRootReference.INSTANCE) {
            return references.read(input);
        }
        if (query instanceof InspectObject || query == DocumentInfo.INSTANCE) {
            return WorkerCommandCodec.readPdfValue(input, references, 0);
        }
        if (query instanceof XmpMetadata) {
            return input.readNullableBytes();
        }
        if (query instanceof NamedDestinations) {
            int count = WorkerCommandCodec.readCount(
                    input,
                    "named destination result");
            Map<String, PageDestination> values =
                    new LinkedHashMap<String, PageDestination>();
            for (int index = 0; index < count; index++) {
                values.put(
                        input.readString(),
                        WorkerCommandCodec.readPageDestination(input));
            }
            return java.util.Collections.unmodifiableMap(values);
        }
        if (query instanceof OutlineTree) {
            return java.util.Collections.unmodifiableList(
                    WorkerCommandCodec.readOutlineItems(input, 0));
        }
        if (query instanceof EmbeddedFiles) {
            return readEmbeddedFileSummaries(input);
        }
        if (query instanceof ReadEmbeddedFile) {
            return readEmbeddedFileData(input);
        }
        if (query instanceof Annotations) {
            int count = WorkerCommandCodec.readCount(
                    input,
                    "annotation result");
            List<Annotation> annotations = new ArrayList<Annotation>(count);
            for (int index = 0; index < count; index++) {
                annotations.add(WorkerAnnotationCodec.readAnnotation(input));
            }
            return java.util.Collections.unmodifiableList(annotations);
        }
        if (query instanceof Actions) {
            return readDocumentActions(input);
        }
        if (query instanceof ExtractTextAndStructure) {
            return WorkerTextExtractionCodec.read(input);
        }
        if (query instanceof ExtractImagesAndResources) {
            return WorkerResourceInventoryCodec.read(input, references);
        }
        throw WorkerCommandCodec.rejected(
                "The Worker Query result opcode is unsupported.");
    }

    private static void writeVersionInfo(
            WorkerCodecIO.Output output,
            PdfVersionInfo info) throws IOException {
        writeVersion(output, info.getHeaderVersion());
        output.writeBoolean(info.getCatalogVersion().isPresent());
        if (info.getCatalogVersion().isPresent()) {
            writeVersion(output, info.getCatalogVersion().get());
        }
        writeVersion(output, info.getEffectiveVersion());
    }

    private static PdfVersionInfo readVersionInfo(WorkerCodecIO.Input input)
            throws DocumentFailure {
        PdfVersion header = readVersion(input);
        PdfVersion catalog = input.readBoolean() ? readVersion(input) : null;
        PdfVersion effective = readVersion(input);
        return new PdfVersionInfo(header, catalog, effective);
    }

    private static void writeVersion(
            WorkerCodecIO.Output output,
            PdfVersion version) throws IOException {
        output.writeInt(version.getMajor());
        output.writeInt(version.getMinor());
    }

    private static PdfVersion readVersion(WorkerCodecIO.Input input)
            throws DocumentFailure {
        PdfVersion version = PdfVersion.from(input.readInt(), input.readInt());
        if (version == null) {
            throw WorkerCommandCodec.rejected(
                    "The Worker PDF version is unsupported.");
        }
        return version;
    }

    private static void writeSecurityInfo(
            WorkerCodecIO.Output output,
            PasswordSecurityInfo info) throws IOException {
        output.writeNullableString(info.getAlgorithm().isPresent()
                ? info.getAlgorithm().get().name() : null);
        output.writeInt(info.getSecurityHandlerRevision());
        output.writeString(info.getEncryptionScope().name());
        output.writeInt(info.getDeclaredUserPermissions().getStandardMask());
        output.writeInt(info.getEffectivePermissions().getStandardMask());
        output.writeString(info.getCredentialAuthority().name());
    }

    private static PasswordSecurityInfo readSecurityInfo(
            WorkerCodecIO.Input input) throws DocumentFailure {
        String algorithmName = input.readNullableString();
        PasswordEncryptionAlgorithm algorithm = algorithmName == null ? null
                : WorkerCommandCodec.enumValue(
                        PasswordEncryptionAlgorithm.class,
                        algorithmName,
                        "password algorithm");
        return new PasswordSecurityInfo(
                algorithm,
                input.readInt(),
                WorkerCommandCodec.enumValue(
                        PasswordEncryptionScope.class,
                        input.readString(),
                        "password scope"),
                DocumentPermissions.fromStandardMask(input.readInt()),
                DocumentPermissions.fromStandardMask(input.readInt()),
                WorkerCommandCodec.enumValue(
                        CredentialAuthority.class,
                        input.readString(),
                        "credential authority"));
    }

    private static void writeCanvasImageCapabilities(
            WorkerCodecIO.Output output,
            CanvasImageCapabilities capabilities) throws IOException {
        output.writeInt(capabilities.getVersion());
        CanvasImageCapabilities.Support tiff = capabilities.getSupport(
                CanvasImage.SourceKind.TIFF);
        output.writeString(tiff.getAvailability().name());
    }

    private static CanvasImageCapabilities readCanvasImageCapabilities(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                CanvasImageCapabilities.VERSION_1);
        return CanvasImageCapabilities.version1(WorkerCommandCodec.enumValue(
                CanvasImageCapabilities.Availability.class,
                input.readString(),
                "Canvas Image availability"));
    }

    private static void writeEmbeddedFileSummaries(
            WorkerCodecIO.Output output,
            List<EmbeddedFileSummary> values) throws IOException {
        output.writeInt(values.size());
        for (EmbeddedFileSummary value : values) {
            writeEmbeddedFileCommon(
                    output,
                    value.getName(),
                    value.getMimeSubtype().orElse(null),
                    value.getDescription().orElse(null),
                    value.declaredRelationshipForWorkflow(),
                    value.getSize(),
                    value.getMd5Hex().orElse(null));
        }
    }

    private static List<EmbeddedFileSummary> readEmbeddedFileSummaries(
            WorkerCodecIO.Input input) throws DocumentFailure {
        int count = WorkerCommandCodec.readCount(
                input,
                "embedded-file summary");
        List<EmbeddedFileSummary> values =
                new ArrayList<EmbeddedFileSummary>(count);
        for (int index = 0; index < count; index++) {
            EmbeddedFileCommon common = readEmbeddedFileCommon(input);
            values.add(new EmbeddedFileSummary(
                    common.name,
                    common.mime,
                    common.description,
                    common.relationship,
                    common.size,
                    common.md5));
        }
        return java.util.Collections.unmodifiableList(values);
    }

    private static void writeEmbeddedFileData(
            WorkerCodecIO.Output output,
            Optional<EmbeddedFileData> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (!value.isPresent()) {
            return;
        }
        EmbeddedFileData data = value.get();
        writeEmbeddedFileCommon(
                output,
                data.getName(),
                data.getMimeSubtype().orElse(null),
                data.getDescription().orElse(null),
                data.declaredRelationshipForWorkflow(),
                data.getSize(),
                data.getMd5Hex().orElse(null));
        output.writeString(data.getSha256Hex());
        output.writeBytes(data.contentForWorkflow());
    }

    private static Optional<EmbeddedFileData> readEmbeddedFileData(
            WorkerCodecIO.Input input) throws DocumentFailure {
        if (!input.readBoolean()) {
            return Optional.empty();
        }
        EmbeddedFileCommon common = readEmbeddedFileCommon(input);
        return Optional.of(new EmbeddedFileData(
                common.name,
                common.mime,
                common.description,
                common.relationship,
                common.size,
                common.md5,
                input.readString(),
                input.readBytes()));
    }

    private static void writeEmbeddedFileCommon(
            WorkerCodecIO.Output output,
            String name,
            String mime,
            String description,
            EmbeddedFile.Relationship relationship,
            long size,
            String md5) throws IOException {
        output.writeString(name);
        output.writeNullableString(mime);
        output.writeNullableString(description);
        output.writeBoolean(relationship != null);
        if (relationship != null) {
            output.writeString(relationship.name());
        }
        output.writeLong(size);
        output.writeNullableString(md5);
    }

    private static EmbeddedFileCommon readEmbeddedFileCommon(
            WorkerCodecIO.Input input) throws DocumentFailure {
        return new EmbeddedFileCommon(
                input.readString(),
                input.readNullableString(),
                input.readNullableString(),
                input.readBoolean() ? WorkerCommandCodec.enumValue(
                        EmbeddedFile.Relationship.class,
                        input.readString(),
                        "embedded-file relationship") : null,
                input.readLong(),
                input.readNullableString());
    }

    private static void writeDocumentActions(
            WorkerCodecIO.Output output,
            DocumentActions actions) throws IOException {
        output.writeBoolean(actions.getDocumentOpenAction().isPresent());
        if (actions.getDocumentOpenAction().isPresent()) {
            WorkerCommandCodec.writeGoToAction(
                    output,
                    actions.getDocumentOpenAction().get());
        }
        output.writeInt(actions.getPageActions().size());
        for (PageActions page : actions.getPageActions()) {
            output.writeInt(page.getPageNumber());
            output.writeBoolean(page.getOpenAction().isPresent());
            if (page.getOpenAction().isPresent()) {
                WorkerCommandCodec.writeGoToAction(
                        output,
                        page.getOpenAction().get());
            }
            output.writeBoolean(page.getCloseAction().isPresent());
            if (page.getCloseAction().isPresent()) {
                WorkerCommandCodec.writeGoToAction(
                        output,
                        page.getCloseAction().get());
            }
        }
    }

    private static DocumentActions readDocumentActions(
            WorkerCodecIO.Input input) throws DocumentFailure {
        GoToAction document = input.readBoolean()
                ? WorkerCommandCodec.readGoToAction(input) : null;
        int count = WorkerCommandCodec.readCount(input, "page Action result");
        List<PageActions> pages = new ArrayList<PageActions>(count);
        for (int index = 0; index < count; index++) {
            int pageNumber = input.readInt();
            GoToAction open = input.readBoolean()
                    ? WorkerCommandCodec.readGoToAction(input) : null;
            GoToAction close = input.readBoolean()
                    ? WorkerCommandCodec.readGoToAction(input) : null;
            pages.add(new PageActions(pageNumber, open, close));
        }
        return new DocumentActions(document, pages);
    }

    private static void writeExtractionQuery(
            WorkerCodecIO.Output output,
            ExtractTextAndStructure query) throws IOException {
        ExtractionLimits limits = query.getLimits();
        output.writeInt(query.getVersion());
        output.writeInt(limits.getVersion());
        output.writeInt(limits.getMaximumPages());
        output.writeInt(limits.getMaximumPageTreeNodes());
        output.writeInt(limits.getMaximumContentStreams());
        output.writeInt(limits.getMaximumContentStreamDepth());
        output.writeLong(limits.getMaximumDecodedBytes());
        output.writeInt(limits.getMaximumTextItems());
        output.writeInt(limits.getMaximumUnicodeCodePoints());
        output.writeInt(limits.getMaximumToUnicodeMappings());
        output.writeInt(limits.getMaximumFontDataEntries());
        output.writeInt(limits.getMaximumMarkedContentSequences());
        output.writeInt(limits.getMaximumMarkedContentDepth());
        output.writeInt(limits.getMaximumStructureElements());
        output.writeInt(limits.getMaximumStructureItems());
        output.writeInt(limits.getMaximumStructureDepth());
        output.writeInt(limits.getMaximumRoleMappings());
    }

    private static ExtractTextAndStructure readExtractionQuery(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                ExtractTextAndStructure.VERSION_1);
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                ExtractionLimits.VERSION_1);
        return ExtractTextAndStructure.version1(ExtractionLimits.builder()
                .maximumPages(input.readInt())
                .maximumPageTreeNodes(input.readInt())
                .maximumContentStreams(input.readInt())
                .maximumContentStreamDepth(input.readInt())
                .maximumDecodedBytes(input.readLong())
                .maximumTextItems(input.readInt())
                .maximumUnicodeCodePoints(input.readInt())
                .maximumToUnicodeMappings(input.readInt())
                .maximumFontDataEntries(input.readInt())
                .maximumMarkedContentSequences(input.readInt())
                .maximumMarkedContentDepth(input.readInt())
                .maximumStructureElements(input.readInt())
                .maximumStructureItems(input.readInt())
                .maximumStructureDepth(input.readInt())
                .maximumRoleMappings(input.readInt())
                .build());
    }

    private static void writeResourceQuery(
            WorkerCodecIO.Output output,
            ExtractImagesAndResources query) throws IOException {
        ResourceExtractionLimits limits = query.getLimits();
        output.writeInt(query.getVersion());
        output.writeInt(limits.getVersion());
        output.writeInt(limits.getMaximumPages());
        output.writeInt(limits.getMaximumPageTreeNodes());
        output.writeLong(limits.getMaximumTraversedResourceValues());
        output.writeInt(limits.getMaximumResourceTraversalDepth());
        output.writeLong(limits.getMaximumDecodedPixels());
        output.writeLong(limits.getMaximumDecompressedBytes());
        output.writeLong(limits.getMaximumReturnedBytes());
        output.writeString(query.getByteAccess().name());
    }

    private static ExtractImagesAndResources readResourceQuery(
            WorkerCodecIO.Input input) throws DocumentFailure {
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                ExtractImagesAndResources.VERSION_1);
        WorkerCommandCodec.requireVersion(
                input.readInt(),
                ResourceExtractionLimits.VERSION_1);
        ResourceExtractionLimits limits = ResourceExtractionLimits.builder()
                .maximumPages(input.readInt())
                .maximumPageTreeNodes(input.readInt())
                .maximumTraversedResourceValues(input.readLong())
                .maximumResourceTraversalDepth(input.readInt())
                .maximumDecodedPixels(input.readLong())
                .maximumDecompressedBytes(input.readLong())
                .maximumReturnedBytes(input.readLong())
                .build();
        return ExtractImagesAndResources.version1(
                limits,
                WorkerCommandCodec.enumValue(
                        ImageByteAccess.class,
                        input.readString(),
                        "Image byte access"));
    }

    private static final class EmbeddedFileCommon {
        private final String name;
        private final String mime;
        private final String description;
        private final EmbeddedFile.Relationship relationship;
        private final long size;
        private final String md5;

        private EmbeddedFileCommon(
                String name,
                String mime,
                String description,
                EmbeddedFile.Relationship relationship,
                long size,
                String md5) {
            this.name = name;
            this.mime = mime;
            this.description = description;
            this.relationship = relationship;
            this.size = size;
            this.md5 = md5;
        }
    }
}
