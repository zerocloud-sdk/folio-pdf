package net.zerocloud.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.zerocloud.pdf.DocumentResource.Kind;
import net.zerocloud.pdf.FontResource.Embedding;
import net.zerocloud.pdf.FontResource.FontKind;
import net.zerocloud.pdf.ImageResource.ByteAvailability;
import net.zerocloud.pdf.ImageResource.ColorFamily;
import net.zerocloud.pdf.ImageResource.ColorStatus;
import net.zerocloud.pdf.ImageResource.DecodeSupport;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Private PDFBox adapter for the deep image/resource extraction module. */
final class PdfBoxImageResourceExtractionOperations {

    static final String CAPABILITY_ID = "document.images-resources.extract";

    private static final COSName EXT_G_STATE = COSName.getPDFName("ExtGState");
    private static final COSName COLOR_SPACE = COSName.getPDFName("ColorSpace");
    private static final COSName PATTERN = COSName.getPDFName("Pattern");
    private static final COSName SHADING = COSName.getPDFName("Shading");
    private static final COSName XOBJECT = COSName.getPDFName("XObject");
    private static final COSName FONT = COSName.getPDFName("Font");
    private static final COSName PROC_SET = COSName.getPDFName("ProcSet");
    private static final COSName PROPERTIES = COSName.getPDFName("Properties");
    private static final COSName IMAGE = COSName.getPDFName("Image");
    private static final COSName FORM = COSName.getPDFName("Form");
    private static final COSName IMAGE_MASK = COSName.getPDFName("ImageMask");
    private static final COSName MASK = COSName.getPDFName("Mask");
    private static final COSName S_MASK = COSName.getPDFName("SMask");
    private static final COSName S_MASK_IN_DATA =
            COSName.getPDFName("SMaskInData");
    private static final COSName MATTE = COSName.getPDFName("Matte");
    private static final COSName INTERPOLATE = COSName.getPDFName("Interpolate");
    private static final COSName FILESPEC = COSName.getPDFName("Filespec");
    private static final COSName FILE_SYSTEM = COSName.getPDFName("FS");
    private static final COSName UNICODE_FILE = COSName.getPDFName("UF");
    private static final COSName DOS_FILE = COSName.getPDFName("DOS");
    private static final COSName MAC_FILE = COSName.getPDFName("Mac");
    private static final COSName UNIX_FILE = COSName.getPDFName("Unix");
    private static final COSName BITS_PER_COMPONENT =
            COSName.getPDFName("BitsPerComponent");
    private static final COSName BITS_PER_SAMPLE =
            COSName.getPDFName("BitsPerSample");
    private static final COSName DECODE_PARMS = COSName.getPDFName("DecodeParms");
    private static final COSName F_FILTER = COSName.getPDFName("FFilter");
    private static final COSName F_DECODE_PARMS =
            COSName.getPDFName("FDecodeParms");
    private static final COSName PREDICTOR = COSName.getPDFName("Predictor");
    private static final COSName COLORS = COSName.getPDFName("Colors");
    private static final COSName COLUMNS = COSName.getPDFName("Columns");
    private static final COSName EARLY_CHANGE = COSName.getPDFName("EarlyChange");
    private static final COSName BASE_FONT = COSName.getPDFName("BaseFont");
    private static final COSName DESCENDANT_FONTS =
            COSName.getPDFName("DescendantFonts");
    private static final COSName FONT_DESCRIPTOR =
            COSName.getPDFName("FontDescriptor");
    private static final COSName FONT_FILE = COSName.getPDFName("FontFile");
    private static final COSName FONT_FILE_2 = COSName.getPDFName("FontFile2");
    private static final COSName FONT_FILE_3 = COSName.getPDFName("FontFile3");
    private static final COSName CHAR_PROCS = COSName.getPDFName("CharProcs");
    private static final COSName BBOX = COSName.getPDFName("BBox");
    private static final COSName MATRIX = COSName.getPDFName("Matrix");
    private static final COSName FORM_TYPE = COSName.getPDFName("FormType");
    private static final COSName N = COSName.getPDFName("N");
    private static final COSName WHITE_POINT = COSName.getPDFName("WhitePoint");
    private static final COSName BLACK_POINT = COSName.getPDFName("BlackPoint");
    private static final COSName GAMMA = COSName.getPDFName("Gamma");
    private static final COSName RANGE = COSName.getPDFName("Range");
    private static final COSName ALTERNATE = COSName.getPDFName("Alternate");
    private static final COSName FUNCTION_TYPE = COSName.getPDFName("FunctionType");
    private static final COSName DOMAIN = COSName.getPDFName("Domain");
    private static final COSName SIZE = COSName.getPDFName("Size");
    private static final COSName ORDER = COSName.getPDFName("Order");
    private static final COSName ENCODE = COSName.getPDFName("Encode");
    private static final COSName DECODE = COSName.getPDFName("Decode");
    private static final COSName C0 = COSName.getPDFName("C0");
    private static final COSName C1 = COSName.getPDFName("C1");
    private static final COSName FUNCTIONS = COSName.getPDFName("Functions");
    private static final COSName BOUNDS = COSName.getPDFName("Bounds");
    private static final COSName DEVICE_N_COLOR = COSName.getPDFName("DeviceN");
    private static final COSName N_CHANNEL = COSName.getPDFName("NChannel");
    private static final COSName ALL_COLORANTS = COSName.getPDFName("All");
    private static final COSName NO_COLORANT = COSName.getPDFName("None");

    private static final Comparator<Map.Entry<COSName, COSBase>> ENTRY_ORDER =
            new Comparator<Map.Entry<COSName, COSBase>>() {
                @Override
                public int compare(
                        Map.Entry<COSName, COSBase> left,
                        Map.Entry<COSName, COSBase> right) {
                    return left.getKey().getName().compareTo(
                            right.getKey().getName());
                }
            };

    private final PDDocument document;
    private final PdfBoxValueAdapter valueAdapter;

    PdfBoxImageResourceExtractionOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter) {
        this.document = document;
        this.valueAdapter = valueAdapter;
    }

    boolean supportsQuery(DocumentQuery<?> query) {
        return query instanceof ExtractImagesAndResources;
    }

    DocumentResourceInventory evaluate(ExtractImagesAndResources query)
            throws DocumentFailure {
        State state = new State(valueAdapter, query);
        try {
            COSBase catalog = State.direct(
                    document.getDocument().getTrailer().getItem(COSName.ROOT));
            if (!(catalog instanceof COSDictionary)
                    || catalog instanceof COSStream) {
                throw new IOException("Document catalog is malformed");
            }
            COSBase pageTree = State.direct(
                    ((COSDictionary) catalog).getItem(COSName.PAGES));
            List<PdfBoxPageTreePreflight.PageView> pages = state.pages(pageTree);
            for (int index = 0; index < pages.size(); index++) {
                COSBase resources = State.direct(pages.get(index).effective()
                        .getItem(COSName.RESOURCES));
                if (resources != null) {
                    if (!(resources instanceof COSDictionary)
                            || resources instanceof COSStream) {
                        throw new IOException("Page Resources is malformed");
                    }
                    state.visitResources(new State.ResourceContext(
                            (COSDictionary) resources,
                            index + 1,
                            Collections.<ResourceDeclaration.Segment>emptyList(),
                            1));
                }
            }
            return state.result();
        } catch (ResourceLimitException | ResourceLimitIOException exhausted) {
            throw failure(
                    DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    "The image and resource extraction limit was exceeded.");
        } catch (IOException malformed) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document images and resources could not be extracted safely.");
        }
    }

    private static final class State {

        private final PdfBoxValueAdapter valueAdapter;
        private final ResourceExtractionLimits limits;
        private final ImageByteAccess byteAccess;
        private final List<RecordBuilder> ordered = new ArrayList<RecordBuilder>();
        private final Map<ObjectReference, RecordBuilder> indirect =
                new HashMap<ObjectReference, RecordBuilder>();
        private final IdentityHashMap<COSStream, Boolean> activeForms =
                new IdentityHashMap<COSStream, Boolean>();
        private final IdentityHashMap<COSStream, Boolean> activeImages =
                new IdentityHashMap<COSStream, Boolean>();
        private long traversedValues;
        private long decodedPixels;
        private long decompressedBytes;
        private long returnedBytes;

        State(
                PdfBoxValueAdapter valueAdapter,
                ExtractImagesAndResources query) {
            this.valueAdapter = valueAdapter;
            this.limits = query.getLimits();
            this.byteAccess = query.getByteAccess();
        }

        private static final class ResourceContext {

            private final COSDictionary resources;
            private final int page;
            private final List<ResourceDeclaration.Segment> path;
            private final int depth;

            private ResourceContext(
                    COSDictionary resources,
                    int page,
                    List<ResourceDeclaration.Segment> path,
                    int depth) {
                this.resources = resources;
                this.page = page;
                this.path = path;
                this.depth = depth;
            }

            private ResourceContext member(COSName category, COSName name) {
                return new ResourceContext(
                        resources,
                        page,
                        append(path, segment(category, name)),
                        depth);
            }

            private ResourceContext nestedResources(COSDictionary nested) {
                return new ResourceContext(nested, page, path, depth + 1);
            }

            private ResourceContext related(COSName relationship) {
                return new ResourceContext(
                        resources,
                        page,
                        append(path, segment(relationship, relationship)),
                        depth + 1);
            }

            private ResourceDeclaration declaration() {
                return State.declaration(page, path);
            }
        }

        List<PdfBoxPageTreePreflight.PageView> pages(COSBase value)
                throws IOException, ResourceLimitException {
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Page-tree root is malformed");
            }
            try {
                return PdfBoxPageTreePreflight.pages(
                        (COSDictionary) value,
                        limits.getMaximumPages(),
                        limits.getMaximumPageTreeNodes());
            } catch (PdfBoxPageTreePreflight.LimitExceededException exhausted) {
                throw new ResourceLimitException();
            }
        }

        void visitResources(ResourceContext context)
                throws IOException, ResourceLimitException {
            List<Map.Entry<COSName, COSBase>> categories =
                    sorted(context.resources);
            for (Map.Entry<COSName, COSBase> categoryEntry : categories) {
                accountValues(1L);
                COSName category = categoryEntry.getKey();
                COSBase rawCategory = categoryEntry.getValue();
                if (PROC_SET.equals(category)) {
                    visitProcSet(rawCategory, context);
                } else if (isNamedCategory(category)) {
                    visitNamedCategory(
                            category,
                            rawCategory,
                            context);
                } else {
                    visitUnknownCategory(
                            category,
                            rawCategory,
                            context);
                }
            }
        }

        private void visitNamedCategory(
                COSName category,
                COSBase rawCategory,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            COSBase value = direct(rawCategory);
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Resources category is malformed");
            }
            for (Map.Entry<COSName, COSBase> entry
                    : sorted((COSDictionary) value)) {
                ResourceContext member = context.member(
                        category, entry.getKey());
                checkDepth(member.depth);
                accountValues(1L);
                if (XOBJECT.equals(category)) {
                    visitXObject(entry.getValue(), member);
                } else if (FONT.equals(category)) {
                    visitFont(entry.getValue(), member.declaration());
                } else {
                    addGeneric(
                            entry.getValue(),
                            kind(category),
                            member.declaration());
                }
            }
        }

        private void visitProcSet(
                COSBase raw,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSArray)) {
                throw new IOException("ProcSet is malformed");
            }
            COSArray values = (COSArray) value;
            for (int index = 0; index < values.size(); index++) {
                accountValues(1L);
                COSBase member = direct(values.get(index));
                if (!(member instanceof COSName)) {
                    throw new IOException("ProcSet member is malformed");
                }
                COSName name = (COSName) member;
                ResourceContext declaration = context.member(PROC_SET, name);
                checkDepth(declaration.depth);
                addGeneric(
                        values.get(index),
                        Kind.PROCEDURE_SET,
                        declaration.declaration());
            }
        }

        private void visitUnknownCategory(
                COSName category,
                COSBase raw,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (value instanceof COSDictionary && !(value instanceof COSStream)) {
                for (Map.Entry<COSName, COSBase> entry
                        : sorted((COSDictionary) value)) {
                    ResourceContext member = context.member(
                            category, entry.getKey());
                    checkDepth(member.depth);
                    accountValues(1L);
                    addGeneric(
                            entry.getValue(),
                            Kind.OTHER,
                            member.declaration());
                }
            } else {
                ResourceContext member = context.member(category, category);
                checkDepth(member.depth);
                addGeneric(
                        raw,
                        Kind.OTHER,
                        member.declaration());
            }
        }

        private void visitXObject(
                COSBase raw,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            checkDepth(context.depth);
            COSBase value = direct(raw);
            if (!(value instanceof COSStream)) {
                throw new IOException("XObject is not a stream");
            }
            COSStream stream = (COSStream) value;
            COSBase type = direct(stream.getItem(COSName.TYPE));
            COSBase subtype = direct(stream.getItem(COSName.SUBTYPE));
            if ((type != null && !COSName.XOBJECT.equals(type))
                    || !(subtype instanceof COSName)) {
                throw new IOException("XObject kind is malformed");
            }
            if (IMAGE.equals(subtype)) {
                visitImage(raw, context);
                return;
            }
            if (FORM.equals(subtype)) {
                addGeneric(raw, Kind.FORM, context.declaration());
                visitForm(stream, context);
                return;
            }
            addGeneric(raw, Kind.XOBJECT_OTHER, context.declaration());
        }

        private void visitForm(
                COSStream form,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            if (activeForms.put(form, Boolean.TRUE) != null) {
                throw new IOException("Form graph is cyclic");
            }
            try {
                requireNumberArray(form.getItem(BBOX), 4, "Form BBox");
                COSBase matrix = direct(form.getItem(MATRIX));
                if (matrix != null) {
                    requireNumberArray(matrix, 6, "Form Matrix");
                }
                COSBase formType = direct(form.getItem(FORM_TYPE));
                if (formType != null
                        && (!(formType instanceof COSInteger)
                                || ((COSInteger) formType).longValue() != 1L)) {
                    throw new IOException("FormType is malformed");
                }
                COSBase resources = direct(form.getItem(COSName.RESOURCES));
                if (resources != null) {
                    if (!(resources instanceof COSDictionary)
                            || resources instanceof COSStream) {
                        throw new IOException("Form Resources is malformed");
                    }
                    visitResources(context.nestedResources(
                            (COSDictionary) resources));
                }
            } finally {
                activeForms.remove(form);
            }
        }

        private ImageBuilder visitImage(
                COSBase raw,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            return visitImage(raw, context, null);
        }

        private ImageBuilder visitImage(
                COSBase raw,
                ResourceContext context,
                ImageBuilder existingDirect)
                throws IOException, ResourceLimitException {
            checkDepth(context.depth);
            COSBase value = direct(raw);
            if (!(value instanceof COSStream)) {
                throw new IOException("Image is not a stream");
            }
            COSStream stream = (COSStream) value;
            Registration<ImageBuilder> registration;
            if (existingDirect == null) {
                registration = imageBuilder(raw, context.declaration());
            } else {
                if (raw instanceof COSObject) {
                    throw new IOException("Indirect image reuse is inconsistent");
                }
                existingDirect.add(context.declaration());
                registration = new Registration<ImageBuilder>(
                        existingDirect,
                        false);
            }
            ImageMetadata metadata = imageMetadata(stream, context.resources);
            if (registration.created) {
                registration.builder.metadata = metadata;
                materializeSelectedBytes(registration.builder, stream, metadata);
            } else if (!registration.builder.metadata.same(metadata)) {
                throw new IOException("Shared image metadata is context-dependent");
            }

            if (activeImages.put(stream, Boolean.TRUE) != null) {
                throw new IOException("Image-mask graph is cyclic");
            }
            try {
                visitMasks(
                        registration.builder,
                        stream,
                        metadata,
                        context);
            } finally {
                activeImages.remove(stream);
            }
            return registration.builder;
        }

        private void visitMasks(
                ImageBuilder owner,
                COSStream stream,
                ImageMetadata metadata,
                ResourceContext context)
                throws IOException, ResourceLimitException {
            COSBase rawMask = stream.getItem(MASK);
            COSBase mask = direct(rawMask);
            COSBase rawSoftMask = stream.getItem(S_MASK);
            COSBase softMask = direct(rawSoftMask);
            if (metadata.imageMask
                    && (mask != null
                            || softMask != null
                            || metadata.embeddedSoftMask
                                    != ImageResource.EmbeddedSoftMask.NONE)) {
                throw new IOException("An Image Mask cannot declare another mask");
            }

            if (mask instanceof COSArray) {
                COSArray ranges = (COSArray) mask;
                accountValues(ranges.size());
                if (metadata.components == null
                        || metadata.bits == null
                        || ranges.size()
                                != metadata.components.intValue() * 2) {
                    throw new IOException("Color-key mask is malformed");
                }
                List<PdfNumber> values = new ArrayList<PdfNumber>(ranges.size());
                long maximum = (1L << metadata.bits.intValue()) - 1L;
                for (int index = 0; index < ranges.size(); index += 2) {
                    long low = maskSample(ranges.get(index), maximum);
                    long high = maskSample(ranges.get(index + 1), maximum);
                    if (low > high) {
                        throw new IOException("Color-key mask range is reversed");
                    }
                    values.add(PdfNumber.of(low));
                    values.add(PdfNumber.of(high));
                }
                owner.setExplicit(MaskLink.colorKey(values));
            } else if (mask != null) {
                if (!(mask instanceof COSStream)) {
                    throw new IOException("Explicit mask is malformed");
                }
                ResourceContext maskContext = context.related(MASK);
                ImageBuilder target = visitImage(
                        rawMask,
                        maskContext,
                        reusableDirectTarget(
                                rawMask,
                                owner.explicitMask,
                                ImageResource.Mask.Kind.EXPLICIT_IMAGE));
                if (!target.metadata.imageMask) {
                    throw new IOException("Explicit mask is not an Image Mask");
                }
                owner.setExplicit(MaskLink.image(
                        ImageResource.Mask.Kind.EXPLICIT_IMAGE,
                        target));
            }

            if (softMask != null) {
                if (!(softMask instanceof COSStream)) {
                    throw new IOException("Soft mask is malformed");
                }
                COSStream softMaskStream = (COSStream) softMask;
                if (direct(softMaskStream.getItem(BITS_PER_COMPONENT)) == null
                        || direct(softMaskStream.getItem(MASK)) != null
                        || direct(softMaskStream.getItem(S_MASK)) != null) {
                    throw new IOException("Soft mask dictionary is malformed");
                }
                ResourceContext maskContext = context.related(S_MASK);
                ImageBuilder target = visitImage(
                        rawSoftMask,
                        maskContext,
                        reusableDirectTarget(
                                rawSoftMask,
                                owner.softMask,
                                ImageResource.Mask.Kind.SOFT_IMAGE));
                if (target.metadata.imageMask
                        || target.metadata.bits == null
                        || target.metadata.color.status != ColorStatus.SUPPORTED
                        || target.metadata.color.family
                                != ColorFamily.DEVICE_GRAY) {
                    throw new IOException("Soft mask is not a grayscale image");
                }
                validateSoftMaskDictionary(
                        softMaskStream,
                        metadata,
                        target.metadata);
                owner.setSoft(MaskLink.image(
                        ImageResource.Mask.Kind.SOFT_IMAGE,
                        target));
            }
        }

        private void validateSoftMaskDictionary(
                COSStream stream,
                ImageMetadata owner,
                ImageMetadata target)
                throws IOException, ResourceLimitException {
            COSBase decode = direct(stream.getItem(DECODE));
            if (decode != null && numberArray(decode, 2) == null) {
                throw new IOException("Soft mask Decode is malformed");
            }
            COSBase interpolate = direct(stream.getItem(INTERPOLATE));
            if (interpolate != null) {
                booleanValue(interpolate, false);
            }
            COSBase matte = direct(stream.getItem(MATTE));
            if (matte != null) {
                double[] ranges = owner.color.matteRanges;
                double[] values = ranges == null
                        ? null
                        : numberArray(matte, ranges.length / 2);
                if (values == null
                        || !validColorComponents(values, ranges)
                        || owner.width != target.width
                        || owner.height != target.height) {
                    throw new IOException("Soft mask Matte is malformed");
                }
            }
        }

        private static ImageBuilder reusableDirectTarget(
                COSBase raw,
                MaskLink existing,
                ImageResource.Mask.Kind kind) throws IOException {
            if (raw instanceof COSObject || existing == null) {
                return null;
            }
            if (existing.kind != kind || existing.target == null) {
                throw new IOException("Shared direct mask is inconsistent");
            }
            return existing.target;
        }

        private static long maskSample(COSBase raw, long maximum)
                throws IOException {
            COSBase value = direct(raw);
            if (!(value instanceof COSInteger)) {
                throw new IOException("Color-key mask contains a non-integer");
            }
            long sample = ((COSInteger) value).longValue();
            if (sample < 0L || sample > maximum) {
                throw new IOException("Color-key mask sample is out of range");
            }
            return sample;
        }

        private ImageMetadata imageMetadata(
                COSStream stream,
                COSDictionary resources) throws IOException, ResourceLimitException {
            COSBase type = direct(stream.getItem(COSName.TYPE));
            COSBase subtype = direct(stream.getItem(COSName.SUBTYPE));
            if ((type != null && !COSName.XOBJECT.equals(type))
                    || !IMAGE.equals(subtype)) {
                throw new IOException("Image kind is malformed");
            }
            int width = positiveInt(stream.getItem(COSName.WIDTH), "Image Width");
            int height = positiveInt(stream.getItem(COSName.HEIGHT), "Image Height");
            boolean imageMask = booleanValue(stream.getItem(IMAGE_MASK), false);
            boolean external = hasExternalFile(stream);
            FilterSequence filters = filters(stream, external);
            boolean jpx = filters.has(FilterKind.JPX);
            ImageResource.EmbeddedSoftMask embeddedSoftMask = jpx
                    ? embeddedSoftMask(stream.getItem(S_MASK_IN_DATA))
                    : ImageResource.EmbeddedSoftMask.NONE;
            if (embeddedSoftMask != ImageResource.EmbeddedSoftMask.NONE
                    && direct(stream.getItem(S_MASK)) != null) {
                throw new IOException(
                        "Embedded and subsidiary soft masks conflict");
            }

            Integer bits;
            COSBase rawBits = direct(stream.getItem(BITS_PER_COMPONENT));
            if (imageMask) {
                bits = Integer.valueOf(1);
                if (rawBits != null
                        && (!(rawBits instanceof COSInteger)
                                || ((COSInteger) rawBits).longValue() != 1L)) {
                    throw new IOException("Image Mask bit depth is malformed");
                }
            } else if (rawBits == null && jpx) {
                bits = null;
            } else {
                bits = Integer.valueOf(positiveInt(
                        rawBits,
                        "Image BitsPerComponent"));
                int value = bits.intValue();
                if (value != 1 && value != 2 && value != 4
                        && value != 8 && value != 16) {
                    throw new IOException("Image bit depth is unsupported");
                }
            }

            ColorInfo color;
            COSBase rawColor = stream.getItem(COSName.COLORSPACE);
            if (imageMask) {
                if (direct(rawColor) != null) {
                    throw new IOException("Image Mask color space is malformed");
                }
                color = ColorInfo.imageMask();
            } else if (direct(rawColor) == null && jpx) {
                color = ColorInfo.unsupported(null);
            } else {
                color = color(
                        rawColor,
                        resources,
                        null,
                        1,
                        new LinkedHashSet<String>(),
                        new IdentityHashMap<COSBase, Boolean>());
            }
            validateFilterSampleGeometry(
                    filters,
                    bits,
                    color.components,
                    width);
            return new ImageMetadata(
                    width,
                    height,
                    bits,
                    color.components,
                    imageMask,
                    embeddedSoftMask,
                    color,
                    filters,
                    external);
        }

        private static ImageResource.EmbeddedSoftMask embeddedSoftMask(
                COSBase raw) throws IOException {
            COSBase value = direct(raw);
            if (value == null) {
                return ImageResource.EmbeddedSoftMask.NONE;
            }
            if (!(value instanceof COSInteger)) {
                throw new IOException("SMaskInData is malformed");
            }
            long code = ((COSInteger) value).longValue();
            if (code == 0L) {
                return ImageResource.EmbeddedSoftMask.NONE;
            }
            if (code == 1L) {
                return ImageResource.EmbeddedSoftMask.SOFT_MASK;
            }
            if (code == 2L) {
                return ImageResource.EmbeddedSoftMask.PREBLENDED_SOFT_MASK;
            }
            throw new IOException("SMaskInData is out of range");
        }

        private boolean hasExternalFile(COSStream stream)
                throws IOException, ResourceLimitException {
            COSBase file = direct(stream.getItem(COSName.F));
            if (file == null) {
                return false;
            }
            if (file instanceof COSString) {
                return true;
            }
            if (!(file instanceof COSDictionary) || file instanceof COSStream) {
                throw new IOException("External file specification is malformed");
            }
            COSDictionary specification = (COSDictionary) file;
            accountValues(specification.size());
            COSBase type = direct(specification.getItem(COSName.TYPE));
            if (type != null && !FILESPEC.equals(type)) {
                throw new IOException("External file specification type is malformed");
            }
            COSBase fileSystem = direct(specification.getItem(FILE_SYSTEM));
            if (fileSystem != null && !(fileSystem instanceof COSName)) {
                throw new IOException("External file system is malformed");
            }
            boolean hasLocation = false;
            COSName[] locationKeys = {
                COSName.F,
                UNICODE_FILE,
                DOS_FILE,
                MAC_FILE,
                UNIX_FILE
            };
            for (COSName key : locationKeys) {
                COSBase location = direct(specification.getItem(key));
                if (location != null) {
                    if (!(location instanceof COSString)) {
                        throw new IOException("External file location is malformed");
                    }
                    hasLocation = true;
                }
            }
            if (!hasLocation) {
                throw new IOException("External file specification has no location");
            }
            return true;
        }

        private FilterSequence filters(COSStream stream, boolean external)
                throws IOException, ResourceLimitException {
            COSName filterKey = external ? F_FILTER : COSName.FILTER;
            COSName paramsKey = external ? F_DECODE_PARMS : DECODE_PARMS;
            COSBase rawFilters = direct(stream.getItem(filterKey));
            COSBase rawParameters = direct(stream.getItem(paramsKey));
            if (rawFilters == null) {
                if (rawParameters != null) {
                    throw new IOException("DecodeParms has no Filter");
                }
                return new FilterSequence(
                        Collections.<FilterSpec>emptyList(),
                        true);
            }

            List<COSName> names = new ArrayList<COSName>();
            if (rawFilters instanceof COSName) {
                names.add((COSName) rawFilters);
                accountValues(1L);
            } else if (rawFilters instanceof COSArray) {
                COSArray array = (COSArray) rawFilters;
                accountValues(array.size());
                for (int index = 0; index < array.size(); index++) {
                    COSBase value = direct(array.get(index));
                    if (!(value instanceof COSName)) {
                        throw new IOException("Filter array is malformed");
                    }
                    names.add((COSName) value);
                }
            } else {
                throw new IOException("Filter is malformed");
            }

            List<COSDictionary> parameters = decodeParameters(
                    rawParameters,
                    names.size());
            List<FilterSpec> specs = new ArrayList<FilterSpec>(names.size());
            boolean supported = true;
            for (int index = 0; index < names.size(); index++) {
                FilterSpec spec = filterSpec(names.get(index), parameters.get(index));
                specs.add(spec);
                supported &= spec.kind.decodeSupport() == DecodeSupport.SUPPORTED;
            }
            return new FilterSequence(specs, supported);
        }

        private static void validateFilterSampleGeometry(
                FilterSequence filters,
                Integer bits,
                Integer components,
                int width) throws IOException {
            for (FilterSpec filter : filters.specs) {
                int requiredBits = filter.kind.requiredBits();
                if (requiredBits != 0
                        && (bits == null
                                || bits.intValue() != requiredBits)) {
                    throw new IOException("Image filter bit depth is malformed");
                }
                if (filter.kind.hasPredictorParameters()
                        && filter.predictor != null
                        && filter.predictor.intValue() > 1
                        && (bits == null
                                || components == null
                                || bits.intValue() != filter.bits.intValue()
                                || components.intValue()
                                        != filter.colors.intValue()
                                || width != filter.columns.intValue())) {
                    throw new IOException("Image predictor geometry is malformed");
                }
            }
        }

        private List<COSDictionary> decodeParameters(
                COSBase raw,
                int filterCount) throws IOException, ResourceLimitException {
            List<COSDictionary> result = new ArrayList<COSDictionary>(filterCount);
            if (raw == null || raw instanceof COSNull) {
                for (int index = 0; index < filterCount; index++) {
                    result.add(null);
                }
                return result;
            }
            if (filterCount == 1) {
                if (!(raw instanceof COSDictionary) || raw instanceof COSStream) {
                    throw new IOException("DecodeParms is malformed");
                }
                result.add((COSDictionary) raw);
                return result;
            }
            if (!(raw instanceof COSArray)) {
                throw new IOException("DecodeParms array is malformed");
            }
            COSArray array = (COSArray) raw;
            accountValues(array.size());
            if (array.size() != filterCount) {
                throw new IOException("DecodeParms arity is malformed");
            }
            for (int index = 0; index < array.size(); index++) {
                COSBase value = direct(array.get(index));
                if (value == null || value instanceof COSNull) {
                    result.add(null);
                } else if (value instanceof COSDictionary
                        && !(value instanceof COSStream)) {
                    result.add((COSDictionary) value);
                } else {
                    throw new IOException("DecodeParms member is malformed");
                }
            }
            return result;
        }

        private FilterSpec filterSpec(COSName declared, COSDictionary parameters)
                throws IOException, ResourceLimitException {
            FilterKind kind = FilterKind.fromName(declared.getName());
            if (kind == null) {
                throw new IOException("Unknown image filter");
            }
            if (parameters != null) {
                accountValues(parameters.size());
            }
            Integer predictor = null;
            Integer colors = null;
            Integer bits = null;
            Integer columns = null;
            Integer earlyChange = null;
            if (kind.hasPredictorParameters()) {
                predictor = Integer.valueOf(parameter(
                        parameters, PREDICTOR, 1, 1, 15));
                int predictorValue = predictor.intValue();
                if (predictorValue != 1 && predictorValue != 2
                        && (predictorValue < 10 || predictorValue > 15)) {
                    throw new IOException("Predictor is malformed");
                }
                colors = Integer.valueOf(parameter(parameters, COLORS, 1, 1, 32));
                bits = Integer.valueOf(parameter(
                        parameters, BITS_PER_COMPONENT, 8, 1, 16));
                int bitsValue = bits.intValue();
                if (bitsValue != 1 && bitsValue != 2 && bitsValue != 4
                        && bitsValue != 8 && bitsValue != 16) {
                    throw new IOException("Predictor bit depth is malformed");
                }
                columns = Integer.valueOf(parameter(
                        parameters, COLUMNS, 1, 1, Integer.MAX_VALUE));
                if (kind.hasEarlyChange()) {
                    earlyChange = Integer.valueOf(parameter(
                            parameters, EARLY_CHANGE, 1, 0, 1));
                }
            }
            return new FilterSpec(
                    declared,
                    kind,
                    predictor,
                    colors,
                    bits,
                    columns,
                    earlyChange);
        }

        private int parameter(
                COSDictionary parameters,
                COSName name,
                int defaultValue,
                int minimum,
                int maximum) throws IOException {
            if (parameters == null || !parameters.containsKey(name)) {
                return defaultValue;
            }
            COSBase value = direct(parameters.getItem(name));
            if (value == null) {
                return defaultValue;
            }
            if (!(value instanceof COSInteger)) {
                throw new IOException("Decode parameter is not an integer");
            }
            long number = ((COSInteger) value).longValue();
            if (number < minimum || number > maximum) {
                throw new IOException("Decode parameter is out of range");
            }
            return (int) number;
        }

        private void preflightPredictor(
                int predictor,
                int colors,
                int bits,
                int columns) throws ResourceLimitException {
            if (predictor <= 1) {
                return;
            }
            long rowBits = multiply(multiply(colors, bits), columns);
            long rowBytes = (rowBits + 7L) / 8L;
            if (rowBytes > limits.getMaximumDecompressedBytes()
                    - decompressedBytes
                    || rowBytes > Integer.MAX_VALUE - 8L) {
                throw new ResourceLimitException();
            }
        }

        private void materializeSelectedBytes(
                ImageBuilder builder,
                COSStream stream,
                ImageMetadata metadata) throws IOException, ResourceLimitException {
            ByteAvailability encodedAvailability = metadata.external
                    ? ByteAvailability.EXTERNAL_STREAM
                    : ByteAvailability.AVAILABLE;
            ByteAvailability decodedAvailability;
            if (metadata.external) {
                decodedAvailability = ByteAvailability.EXTERNAL_STREAM;
            } else if (!metadata.filters.supported) {
                decodedAvailability = ByteAvailability.UNSUPPORTED_FILTER;
            } else {
                decodedAvailability = ByteAvailability.AVAILABLE;
            }
            builder.encodedAvailability = encodedAvailability;
            builder.decodedAvailability = decodedAvailability;

            if (byteAccess.includesEncoded()
                    && encodedAvailability == ByteAvailability.AVAILABLE) {
                builder.encodedBytes = rawBytes(stream, true);
            }
            if (byteAccess.includesDecoded()
                    && decodedAvailability == ByteAvailability.AVAILABLE) {
                preflightDecodedCapacity(metadata);
                accountPixels(metadata.width, metadata.height);
                builder.decodedBytes = decodedBytes(stream, metadata);
            }
        }

        private void preflightDecodedCapacity(ImageMetadata metadata)
                throws ResourceLimitException {
            long expected = expectedDecodedBytes(metadata);
            if (expected < 0L) {
                return;
            }
            if (expected > Integer.MAX_VALUE - 8L
                    || expected > limits.getMaximumDecompressedBytes()
                            - decompressedBytes
                    || expected > limits.getMaximumReturnedBytes()
                            - returnedBytes) {
                throw new ResourceLimitException();
            }
            for (FilterSpec spec : metadata.filters.specs) {
                if (spec.predictor != null) {
                    preflightPredictor(
                            spec.predictor.intValue(),
                            spec.colors.intValue(),
                            spec.bits.intValue(),
                            spec.columns.intValue());
                }
            }
        }

        private byte[] rawBytes(COSStream stream, boolean returned)
                throws IOException, ResourceLimitException {
            AccountingOutput output = new AccountingOutput(this, false, returned);
            byte[] buffer = new byte[8192];
            try (InputStream input = stream.createRawInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        }

        private byte[] decodedBytes(
                COSStream stream,
                ImageMetadata metadata) throws IOException, ResourceLimitException {
            byte[] current = null;
            if (metadata.filters.specs.isEmpty()) {
                AccountingOutput output = new AccountingOutput(this, true, true);
                byte[] buffer = new byte[8192];
                try (InputStream input = stream.createRawInputStream()) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
                current = output.toByteArray();
            } else {
                for (int index = 0; index < metadata.filters.specs.size(); index++) {
                    FilterSpec spec = metadata.filters.specs.get(index);
                    try (InputStream validation = index == 0
                            ? stream.createRawInputStream()
                            : new ByteArrayInputStream(current)) {
                        spec.kind.validate(this, spec, validation);
                    }
                    AccountingOutput output = new AccountingOutput(
                            this,
                            true,
                            index == metadata.filters.specs.size() - 1);
                    InputStream input = index == 0
                            ? stream.createRawInputStream()
                            : new ByteArrayInputStream(current);
                    try (InputStream owned = input) {
                        org.apache.pdfbox.filter.Filter filter =
                                FilterFactory.INSTANCE.getFilter(
                                        COSName.getPDFName(spec.declared.getValue()));
                        filter.decode(
                                owned,
                                output,
                                stream,
                                index,
                                DecodeOptions.DEFAULT);
                    }
                    current = output.toByteArray();
                }
            }
            long expected = expectedDecodedBytes(metadata);
            if (expected >= 0L && current.length != expected) {
                throw new IOException("Decoded image length is malformed");
            }
            return current;
        }

        private void validateFlate(FilterSpec spec, InputStream input)
                throws IOException, ResourceLimitException {
            Inflater inflater = new Inflater();
            byte[] encoded = new byte[8192];
            byte[] decoded = new byte[8192];
            long decodedCount = 0L;
            long encodedRowLength = 0L;
            if (spec.predictor.intValue() > 1) {
                long rowLength = predictorRowLength(spec);
                encodedRowLength = spec.predictor.intValue() >= 10
                        ? rowLength + 1L
                        : rowLength;
            }
            long validationCeiling = limits.getMaximumDecompressedBytes()
                    - decompressedBytes;
            if (spec.predictor.intValue() >= 10) {
                if (validationCeiling > Long.MAX_VALUE
                        - validationCeiling) {
                    validationCeiling = Long.MAX_VALUE;
                } else {
                    validationCeiling += validationCeiling;
                }
            }
            try {
                while (!inflater.finished()) {
                    if (inflater.needsDictionary()) {
                        throw new IOException("Flate dictionary is unsupported");
                    }
                    if (inflater.needsInput()) {
                        int count = input.read(encoded);
                        if (count == -1) {
                            throw new IOException("Flate stream is truncated");
                        }
                        inflater.setInput(encoded, 0, count);
                    }
                    int count;
                    try {
                        count = inflater.inflate(decoded);
                    } catch (DataFormatException malformed) {
                        throw new IOException("Flate stream is malformed");
                    }
                    if (count == 0) {
                        if (inflater.finished()) {
                            break;
                        }
                        if (!inflater.needsInput() && !inflater.needsDictionary()) {
                            throw new IOException("Flate decoder made no progress");
                        }
                        continue;
                    }
                    if (decodedCount > validationCeiling - count) {
                        throw new ResourceLimitException();
                    }
                    if (spec.predictor.intValue() >= 10) {
                        for (int index = 0; index < count; index++) {
                            if ((decodedCount + index) % encodedRowLength == 0L) {
                                int predictor = decoded[index] & 0xff;
                                if (predictor > 4) {
                                    throw new IOException(
                                            "PNG predictor row is malformed");
                                }
                            }
                        }
                    }
                    decodedCount += count;
                }
                if (inflater.getRemaining() != 0 || input.read() != -1) {
                    throw new IOException("Flate stream has trailing data");
                }
                if (spec.predictor.intValue() > 1
                        && decodedCount % encodedRowLength != 0L) {
                    throw new IOException("Predictor row is truncated");
                }
            } finally {
                inflater.end();
            }
        }

        private long predictorRowLength(FilterSpec spec)
                throws ResourceLimitException {
            long bits = multiply(
                    multiply(spec.colors.intValue(), spec.bits.intValue()),
                    spec.columns.intValue());
            long bytes = (bits + 7L) / 8L;
            if (bytes < 1L || bytes > Integer.MAX_VALUE - 8L) {
                throw new ResourceLimitException();
            }
            return bytes;
        }

        private static void validateAsciiHex(InputStream input)
                throws IOException {
            int digits = 0;
            boolean ended = false;
            int value;
            while ((value = input.read()) != -1) {
                if (ended) {
                    if (!pdfWhitespace(value)) {
                        throw new IOException("ASCIIHex stream has trailing data");
                    }
                } else if (value == '>') {
                    ended = true;
                } else if (pdfWhitespace(value)) {
                    continue;
                } else if (hexValue(value) >= 0) {
                    digits++;
                } else {
                    throw new IOException("ASCIIHex stream is malformed");
                }
            }
            if (!ended || digits == 0) {
                throw new IOException("ASCIIHex stream is truncated");
            }
        }

        private static void validateAscii85(InputStream input)
                throws IOException {
            int group = 0;
            long value = 0L;
            boolean ended = false;
            int current;
            while ((current = input.read()) != -1) {
                if (ended) {
                    if (!pdfWhitespace(current)) {
                        throw new IOException("ASCII85 stream has trailing data");
                    }
                    continue;
                }
                if (pdfWhitespace(current)) {
                    continue;
                }
                if (current == '~') {
                    if (input.read() != '>') {
                        throw new IOException("ASCII85 terminator is malformed");
                    }
                    ended = true;
                    continue;
                }
                if (current == 'z') {
                    if (group != 0) {
                        throw new IOException("ASCII85 zero group is malformed");
                    }
                    continue;
                }
                if (current < '!' || current > 'u') {
                    throw new IOException("ASCII85 stream is malformed");
                }
                value = value * 85L + current - '!';
                group++;
                if (group == 5) {
                    if (value > 0xffffffffL) {
                        throw new IOException("ASCII85 group is out of range");
                    }
                    group = 0;
                    value = 0L;
                }
            }
            if (!ended || group == 1) {
                throw new IOException("ASCII85 stream is truncated");
            }
            if (group > 1) {
                while (group < 5) {
                    value = value * 85L + 84L;
                    group++;
                }
                if (value > 0xffffffffL) {
                    throw new IOException("ASCII85 group is out of range");
                }
            }
        }

        private static void validateRunLength(InputStream input)
                throws IOException {
            int length;
            while ((length = input.read()) != -1) {
                if (length == 128) {
                    if (input.read() != -1) {
                        throw new IOException("RunLength stream has trailing data");
                    }
                    return;
                }
                int required = length <= 127 ? length + 1 : 1;
                for (int index = 0; index < required; index++) {
                    if (input.read() == -1) {
                        throw new IOException("RunLength stream is truncated");
                    }
                }
            }
            throw new IOException("RunLength stream has no terminator");
        }

        private static int hexValue(int value) {
            if (value >= '0' && value <= '9') {
                return value - '0';
            }
            if (value >= 'A' && value <= 'F') {
                return value - 'A' + 10;
            }
            if (value >= 'a' && value <= 'f') {
                return value - 'a' + 10;
            }
            return -1;
        }

        private static boolean pdfWhitespace(int value) {
            return value == 0 || value == 9 || value == 10
                    || value == 12 || value == 13 || value == 32;
        }

        private long expectedDecodedBytes(ImageMetadata metadata)
                throws ResourceLimitException {
            if (metadata.bits == null || metadata.components == null) {
                return -1L;
            }
            long rowBits = multiply(
                    multiply(metadata.width, metadata.components.intValue()),
                    metadata.bits.intValue());
            long rowBytes = (rowBits + 7L) / 8L;
            return multiply(rowBytes, metadata.height);
        }

        private ColorInfo color(
                COSBase raw,
                COSDictionary resources,
                PdfName declared,
                int depth,
                Set<String> activeNames,
                IdentityHashMap<COSBase, Boolean> activeValues)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (value == null) {
                return ColorInfo.malformed(declared);
            }
            accountValues(1L);
            if (value instanceof COSName) {
                COSName name = (COSName) value;
                PdfName publicDeclared = declared == null
                        ? PdfName.of(name.getName())
                        : declared;
                ColorInfo builtin = builtinColor(name, publicDeclared);
                if (builtin != null) {
                    return builtin;
                }
                if (resources == null || !activeNames.add(name.getName())) {
                    return ColorInfo.malformed(publicDeclared);
                }
                try {
                    COSBase categories = direct(resources.getItem(COLOR_SPACE));
                    if (!(categories instanceof COSDictionary)
                            || categories instanceof COSStream) {
                        return ColorInfo.malformed(publicDeclared);
                    }
                    COSBase resolved = ((COSDictionary) categories).getItem(name);
                    if (resolved == null) {
                        return ColorInfo.malformed(publicDeclared);
                    }
                    checkDepth(depth + 1);
                    return color(
                            resolved,
                            resources,
                            publicDeclared,
                            depth + 1,
                            activeNames,
                            activeValues);
                } finally {
                    activeNames.remove(name.getName());
                }
            }
            if (!(value instanceof COSArray)) {
                return ColorInfo.malformed(declared);
            }
            if (activeValues.put(value, Boolean.TRUE) != null) {
                return ColorInfo.malformed(declared);
            }
            try {
                COSArray array = (COSArray) value;
                accountValues(array.size());
                if (array.size() == 0
                        || !(direct(array.get(0)) instanceof COSName)) {
                    return ColorInfo.malformed(declared);
                }
                COSName family = (COSName) direct(array.get(0));
                PdfName publicDeclared = declared == null
                        ? PdfName.of(family.getName())
                        : declared;
                return arrayColor(
                        array,
                        family,
                        publicDeclared,
                        resources,
                        depth,
                        activeNames,
                        activeValues);
            } finally {
                activeValues.remove(value);
            }
        }

        private ColorInfo arrayColor(
                COSArray array,
                COSName family,
                PdfName declared,
                COSDictionary resources,
                int depth,
                Set<String> activeNames,
                IdentityHashMap<COSBase, Boolean> activeValues)
                throws IOException, ResourceLimitException {
            String name = family.getName();
            if (COSName.DEVICEGRAY.equals(family)
                    || COSName.DEVICERGB.equals(family)
                    || COSName.DEVICECMYK.equals(family)) {
                if (array.size() != 1) {
                    return ColorInfo.malformed(declared);
                }
                return builtinColor(family, declared);
            }
            if ("CalGray".equals(name)) {
                return calibratedArrayColor(
                        array, declared, family, ColorFamily.CAL_GRAY, 1);
            }
            if ("CalRGB".equals(name)) {
                return calibratedArrayColor(
                        array, declared, family, ColorFamily.CAL_RGB, 3);
            }
            if ("Lab".equals(name)) {
                return calibratedArrayColor(
                        array, declared, family, ColorFamily.LAB, 3);
            }
            if ("ICCBased".equals(name)) {
                if (array.size() != 2) {
                    return ColorInfo.malformed(declared);
                }
                COSBase profile = direct(array.get(1));
                if (!(profile instanceof COSStream)) {
                    return ColorInfo.malformed(declared);
                }
                COSBase count = direct(((COSStream) profile).getItem(N));
                if (!(count instanceof COSInteger)) {
                    return ColorInfo.malformed(declared);
                }
                long components = ((COSInteger) count).longValue();
                if (components != 1L
                        && components != 3L
                        && components != 4L) {
                    return ColorInfo.malformed(declared);
                }
                int componentCount = (int) components;
                double[] matteRanges = componentRanges(
                        ((COSStream) profile).getItem(RANGE),
                        componentCount);
                if (matteRanges == null) {
                    return ColorInfo.malformed(declared);
                }
                ColorStatus status = ColorStatus.UNSUPPORTED;
                COSBase alternateValue = direct(
                        ((COSStream) profile).getItem(ALTERNATE));
                if (alternateValue != null) {
                    ColorInfo alternate = childColor(
                            ((COSStream) profile).getItem(ALTERNATE),
                            resources,
                            depth,
                            activeNames,
                            activeValues);
                    if (alternate.status == ColorStatus.MALFORMED
                            || (alternate.components != null
                                    && alternate.components.intValue()
                                            != componentCount)) {
                        return ColorInfo.malformed(declared);
                    }
                }
                return new ColorInfo(
                        status,
                        ColorFamily.ICC_BASED,
                        declared,
                        PdfName.of(family.getName()),
                        Integer.valueOf(componentCount),
                        matteRanges);
            }
            if ("I".equals(name)) {
                return ColorInfo.malformed(declared);
            }
            if ("Indexed".equals(name)) {
                if (array.size() != 4
                        || !(direct(array.get(2)) instanceof COSInteger)) {
                    return ColorInfo.malformed(declared);
                }
                long high = ((COSInteger) direct(array.get(2))).longValue();
                COSBase lookup = direct(array.get(3));
                if (high < 0L || high > 255L
                        || (!(lookup instanceof org.apache.pdfbox.cos.COSString)
                                && !(lookup instanceof COSStream))) {
                    return ColorInfo.malformed(declared);
                }
                ColorInfo base = childColor(
                        array.get(1), resources, depth, activeNames, activeValues);
                if (base.status == ColorStatus.MALFORMED
                        || base.family == ColorFamily.INDEXED) {
                    return ColorInfo.malformed(declared);
                }
                ColorStatus status = base.status;
                if (lookup instanceof org.apache.pdfbox.cos.COSString
                        && base.components != null) {
                    long expectedLength = (high + 1L)
                            * base.components.intValue();
                    if (((org.apache.pdfbox.cos.COSString) lookup)
                                    .getBytes().length != expectedLength) {
                        return ColorInfo.malformed(declared);
                    }
                } else if (lookup instanceof COSStream) {
                    status = ColorStatus.UNSUPPORTED;
                } else if (base.components == null) {
                    status = ColorStatus.UNSUPPORTED;
                }
                return new ColorInfo(
                        status,
                        ColorFamily.INDEXED,
                        declared,
                        PdfName.of(family.getName()),
                        Integer.valueOf(1),
                        base.matteRanges);
            }
            if ("Separation".equals(name)) {
                if (array.size() != 4
                        || !(direct(array.get(1)) instanceof COSName)) {
                    return ColorInfo.malformed(declared);
                }
                ColorInfo alternate = childColor(
                        array.get(2), resources, depth, activeNames, activeValues);
                if (!validDeviceOrCieColor(alternate)
                        || !validTintFunction(
                                array.get(3), 1, alternate.components)) {
                    return ColorInfo.malformed(declared);
                }
                return new ColorInfo(
                        ColorStatus.UNSUPPORTED,
                        ColorFamily.SEPARATION,
                        declared,
                        PdfName.of(family.getName()),
                        Integer.valueOf(1),
                        ColorInfo.unitRanges(1));
            }
            if ("DeviceN".equals(name)) {
                if ((array.size() != 4 && array.size() != 5)
                        || !(direct(array.get(1)) instanceof COSArray)
                        || (array.size() == 5
                                && (!(direct(array.get(4))
                                        instanceof COSDictionary)
                                    || direct(array.get(4))
                                            instanceof COSStream))) {
                    return ColorInfo.malformed(declared);
                }
                boolean nChannel = false;
                if (array.size() == 5) {
                    COSDictionary attributes = (COSDictionary) direct(
                            array.get(4));
                    COSBase subtype = direct(attributes.getItem(COSName.SUBTYPE));
                    if (subtype != null
                            && !DEVICE_N_COLOR.equals(subtype)
                            && !N_CHANNEL.equals(subtype)) {
                        return ColorInfo.malformed(declared);
                    }
                    nChannel = N_CHANNEL.equals(subtype);
                }
                COSArray names = (COSArray) direct(array.get(1));
                accountValues(names.size());
                if (names.size() == 0 || names.size() > 32) {
                    return ColorInfo.malformed(declared);
                }
                Set<String> uniqueNames = new LinkedHashSet<String>();
                for (int index = 0; index < names.size(); index++) {
                    COSBase rawName = direct(names.get(index));
                    if (!(rawName instanceof COSName)) {
                        return ColorInfo.malformed(declared);
                    }
                    COSName componentName = (COSName) rawName;
                    if (ALL_COLORANTS.equals(componentName)
                            || (nChannel && NO_COLORANT.equals(componentName))
                            || (!NO_COLORANT.equals(componentName)
                                    && !uniqueNames.add(
                                            componentName.getName()))) {
                        return ColorInfo.malformed(declared);
                    }
                }
                ColorInfo alternate = childColor(
                        array.get(2), resources, depth, activeNames, activeValues);
                if (!validDeviceOrCieColor(alternate)
                        || !validTintFunction(
                                array.get(3), names.size(), alternate.components)) {
                    return ColorInfo.malformed(declared);
                }
                return new ColorInfo(
                        ColorStatus.UNSUPPORTED,
                        ColorFamily.DEVICE_N,
                        declared,
                        PdfName.of(family.getName()),
                        Integer.valueOf(names.size()),
                        ColorInfo.unitRanges(names.size()));
            }
            if ("Pattern".equals(name)) {
                return ColorInfo.malformed(declared);
            }
            return ColorInfo.unsupported(declared);
        }

        private static boolean validDeviceOrCieColor(ColorInfo color) {
            if (color.status == ColorStatus.MALFORMED) {
                return false;
            }
            ColorFamily family = color.family;
            return family == ColorFamily.DEVICE_GRAY
                    || family == ColorFamily.DEVICE_RGB
                    || family == ColorFamily.DEVICE_CMYK
                    || family == ColorFamily.CAL_GRAY
                    || family == ColorFamily.CAL_RGB
                    || family == ColorFamily.LAB
                    || family == ColorFamily.ICC_BASED
                    || family == ColorFamily.UNKNOWN;
        }

        private ColorInfo childColor(
                COSBase raw,
                COSDictionary resources,
                int depth,
                Set<String> activeNames,
                IdentityHashMap<COSBase, Boolean> activeValues)
                throws IOException, ResourceLimitException {
            checkDepth(depth + 1);
            return color(
                    raw,
                    resources,
                    null,
                    depth + 1,
                    activeNames,
                    activeValues);
        }

        private double[] componentRanges(
                COSBase raw,
                int components) throws IOException, ResourceLimitException {
            if (direct(raw) == null) {
                return ColorInfo.unitRanges(components);
            }
            double[] values = numberArray(raw, components * 2);
            return values != null && validPairs(values) ? values : null;
        }

        private boolean validTintFunction(
                COSBase raw,
                int inputComponents,
                Integer outputComponents)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSDictionary)) {
                return false;
            }
            COSDictionary function = (COSDictionary) value;
            COSBase rawType = direct(function.getItem(FUNCTION_TYPE));
            if (!(rawType instanceof COSInteger)) {
                return false;
            }
            long type = ((COSInteger) rawType).longValue();
            if (type < 0L || type > 4L || type == 1L) {
                return false;
            }
            double[] domain = numberArray(
                    function.getItem(DOMAIN), inputComponents * 2);
            if (domain == null || !validPairs(domain)) {
                return false;
            }

            COSBase rawRange = direct(function.getItem(RANGE));
            double[] range = null;
            if (rawRange != null) {
                range = outputComponents == null
                        ? numberVector(rawRange)
                        : numberArray(rawRange,
                                outputComponents.intValue() * 2);
                if (range == null
                        || range.length == 0
                        || range.length % 2 != 0
                        || !validPairs(range)) {
                    return false;
                }
            }
            if ((type == 0L || type == 4L) && range == null) {
                return false;
            }

            if (type == 0L) {
                return value instanceof COSStream
                        && validPositiveIntegerArray(
                                function.getItem(SIZE), inputComponents)
                        && validBitsPerSample(
                                function.getItem(BITS_PER_SAMPLE))
                        && validOrder(function.getItem(ORDER))
                        && validOptionalNumberArray(
                                function.getItem(ENCODE),
                                inputComponents * 2,
                                false)
                        && validOptionalNumberArray(
                                function.getItem(DECODE),
                                range.length,
                                false);
            }
            if (type == 2L) {
                return !(value instanceof COSStream)
                        && inputComponents == 1
                        && validTypeTwoFunction(
                                function,
                                outputComponents);
            }
            if (type == 3L) {
                return !(value instanceof COSStream)
                        && inputComponents == 1
                        && validTypeThreeFunction(function, domain);
            }
            return value instanceof COSStream;
        }

        private boolean validTypeTwoFunction(
                COSDictionary function,
                Integer outputComponents)
                throws IOException, ResourceLimitException {
            COSBase exponent = direct(function.getItem(N));
            if (!(exponent instanceof COSNumber)
                    || !finite(((COSNumber) exponent).floatValue())) {
                return false;
            }
            COSBase rawC0 = direct(function.getItem(C0));
            COSBase rawC1 = direct(function.getItem(C1));
            double[] c0 = rawC0 == null
                    ? new double[] {0.0d}
                    : numberVector(rawC0);
            double[] c1 = rawC1 == null
                    ? new double[] {1.0d}
                    : numberVector(rawC1);
            return c0 != null
                    && c1 != null
                    && c0.length > 0
                    && c0.length == c1.length
                    && (outputComponents == null
                            || c0.length == outputComponents.intValue());
        }

        private boolean validTypeThreeFunction(
                COSDictionary function,
                double[] domain) throws IOException, ResourceLimitException {
            COSBase rawFunctions = direct(function.getItem(FUNCTIONS));
            if (!(rawFunctions instanceof COSArray)
                    || ((COSArray) rawFunctions).size() == 0) {
                return false;
            }
            COSArray functions = (COSArray) rawFunctions;
            accountValues(functions.size());
            for (int index = 0; index < functions.size(); index++) {
                if (!(direct(functions.get(index)) instanceof COSDictionary)) {
                    return false;
                }
            }
            double[] bounds = numberArray(
                    function.getItem(BOUNDS), functions.size() - 1);
            double[] encode = numberArray(
                    function.getItem(ENCODE), functions.size() * 2);
            if (bounds == null || encode == null) {
                return false;
            }
            double previous = domain[0];
            for (double bound : bounds) {
                if (bound <= previous || bound >= domain[1]) {
                    return false;
                }
                previous = bound;
            }
            return true;
        }

        private boolean validPositiveIntegerArray(COSBase raw, int size)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSArray)
                    || ((COSArray) value).size() != size) {
                return false;
            }
            COSArray values = (COSArray) value;
            accountValues(values.size());
            for (int index = 0; index < values.size(); index++) {
                COSBase member = direct(values.get(index));
                if (!(member instanceof COSInteger)
                        || ((COSInteger) member).longValue() < 1L) {
                    return false;
                }
            }
            return true;
        }

        private static boolean validBitsPerSample(COSBase raw)
                throws IOException {
            COSBase value = direct(raw);
            if (!(value instanceof COSInteger)) {
                return false;
            }
            long bits = ((COSInteger) value).longValue();
            return bits == 1L || bits == 2L || bits == 4L || bits == 8L
                    || bits == 12L || bits == 16L || bits == 24L
                    || bits == 32L;
        }

        private static boolean validOrder(COSBase raw) throws IOException {
            COSBase value = direct(raw);
            return value == null
                    || (value instanceof COSInteger
                            && (((COSInteger) value).longValue() == 1L
                                    || ((COSInteger) value).longValue() == 3L));
        }

        private double[] numberVector(COSBase raw)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSArray)) {
                return null;
            }
            return numberArray(raw, ((COSArray) value).size());
        }

        private static boolean validPairs(double[] values) {
            for (int index = 0; index < values.length; index += 2) {
                if (values[index] > values[index + 1]) {
                    return false;
                }
            }
            return true;
        }

        private static boolean validColorComponents(
                double[] values,
                double[] ranges) {
            if (ranges.length != values.length * 2) {
                return false;
            }
            for (int index = 0; index < values.length; index++) {
                if (values[index] < ranges[index * 2]
                        || values[index] > ranges[index * 2 + 1]) {
                    return false;
                }
            }
            return true;
        }

        private ColorInfo calibratedArrayColor(
                COSArray array,
                PdfName declared,
                COSName familyName,
                ColorFamily family,
                int components) throws IOException, ResourceLimitException {
            if (array.size() != 2
                    || !(direct(array.get(1)) instanceof COSDictionary)
                    || direct(array.get(1)) instanceof COSStream) {
                return ColorInfo.malformed(declared);
            }
            COSDictionary parameters = (COSDictionary) direct(array.get(1));
            if (!validWhitePoint(parameters.getItem(WHITE_POINT))
                    || !validOptionalNonnegativeTriple(
                            parameters.getItem(BLACK_POINT))) {
                return ColorInfo.malformed(declared);
            }
            if (family == ColorFamily.CAL_GRAY
                    && !validOptionalPositiveNumber(parameters.getItem(GAMMA))) {
                return ColorInfo.malformed(declared);
            }
            if (family == ColorFamily.CAL_RGB
                    && (!validOptionalNumberArray(
                            parameters.getItem(GAMMA), 3, true)
                            || !validOptionalNumberArray(
                                    parameters.getItem(MATRIX), 9, false))) {
                return ColorInfo.malformed(declared);
            }
            double[] matteRanges = ColorInfo.unitRanges(components);
            if (family == ColorFamily.LAB) {
                matteRanges = labComponentRanges(parameters.getItem(RANGE));
                if (matteRanges == null) {
                    return ColorInfo.malformed(declared);
                }
            }
            return ColorInfo.supported(
                    declared,
                    familyName,
                    family,
                    components,
                    matteRanges);
        }

        private boolean validWhitePoint(COSBase raw)
                throws IOException, ResourceLimitException {
            double[] values = numberArray(raw, 3);
            return values != null
                    && values[0] > 0.0d
                    && values[1] == 1.0d
                    && values[2] > 0.0d;
        }

        private boolean validOptionalNonnegativeTriple(COSBase raw)
                throws IOException, ResourceLimitException {
            if (direct(raw) == null) {
                return true;
            }
            double[] values = numberArray(raw, 3);
            return values != null
                    && values[0] >= 0.0d
                    && values[1] >= 0.0d
                    && values[2] >= 0.0d;
        }

        private boolean validOptionalPositiveNumber(COSBase raw)
                throws IOException {
            COSBase value = direct(raw);
            return value == null
                    || (value instanceof COSNumber
                            && finite(((COSNumber) value).floatValue())
                            && ((COSNumber) value).floatValue() > 0.0f);
        }

        private boolean validOptionalNumberArray(
                COSBase raw,
                int size,
                boolean positive)
                throws IOException, ResourceLimitException {
            if (direct(raw) == null) {
                return true;
            }
            double[] values = numberArray(raw, size);
            if (values == null) {
                return false;
            }
            if (positive) {
                for (double value : values) {
                    if (value <= 0.0d) {
                        return false;
                    }
                }
            }
            return true;
        }

        private double[] labComponentRanges(COSBase raw)
                throws IOException, ResourceLimitException {
            if (direct(raw) == null) {
                return new double[] {
                    0.0d, 100.0d,
                    -100.0d, 100.0d,
                    -100.0d, 100.0d
                };
            }
            double[] values = numberArray(raw, 4);
            if (values == null || !validPairs(values)) {
                return null;
            }
            return new double[] {
                0.0d, 100.0d,
                values[0], values[1],
                values[2], values[3]
            };
        }

        private double[] numberArray(COSBase raw, int size)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSArray)
                    || ((COSArray) value).size() != size) {
                return null;
            }
            COSArray array = (COSArray) value;
            accountValues(array.size());
            double[] result = new double[size];
            for (int index = 0; index < size; index++) {
                COSBase member = direct(array.get(index));
                if (!(member instanceof COSNumber)) {
                    return null;
                }
                double number = ((COSNumber) member).floatValue();
                if (!finite(number)) {
                    return null;
                }
                result[index] = number;
            }
            return result;
        }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }

        private ColorInfo builtinColor(COSName name, PdfName declared) {
            if (COSName.DEVICEGRAY.equals(name)) {
                return ColorInfo.supported(
                        declared, name, ColorFamily.DEVICE_GRAY, 1);
            }
            if (COSName.DEVICERGB.equals(name)) {
                return ColorInfo.supported(
                        declared, name, ColorFamily.DEVICE_RGB, 3);
            }
            if (COSName.DEVICECMYK.equals(name)) {
                return ColorInfo.supported(
                        declared, name, ColorFamily.DEVICE_CMYK, 4);
            }
            if (PATTERN.equals(name)) {
                return ColorInfo.malformed(declared);
            }
            return null;
        }

        private void visitFont(COSBase raw, ResourceDeclaration declaration)
                throws IOException, ResourceLimitException {
            COSBase value = direct(raw);
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Font resource is malformed");
            }
            FontInfo info = fontInfo((COSDictionary) value);
            ObjectReference reference = reference(raw);
            if (reference != null) {
                RecordBuilder existing = indirect.get(reference);
                if (existing != null) {
                    requireKind(existing, Kind.FONT);
                    existing.add(declaration);
                    return;
                }
            }
            FontBuilder builder = new FontBuilder(reference, info);
            builder.add(declaration);
            add(builder);
        }

        private FontInfo fontInfo(COSDictionary font)
                throws IOException, ResourceLimitException {
            COSBase type = direct(font.getItem(COSName.TYPE));
            COSBase subtypeValue = direct(font.getItem(COSName.SUBTYPE));
            if (!FONT.equals(type) || !(subtypeValue instanceof COSName)) {
                throw new IOException("Font kind is malformed");
            }
            COSName subtype = (COSName) subtypeValue;
            if (COSName.CID_FONT_TYPE0.equals(subtype)
                    || COSName.CID_FONT_TYPE2.equals(subtype)) {
                throw new IOException(
                        "A CIDFont must be a Type 0 descendant");
            }
            FontKind kind = fontKind(subtype);
            PdfName baseName = baseFont(font);
            String subset = subsetPrefix(baseName);
            if (kind == FontKind.OTHER) {
                return new FontInfo(
                        kind,
                        FontResource.Status.UNSUPPORTED,
                        Embedding.UNKNOWN,
                        baseName,
                        subset);
            }

            COSDictionary descriptorSource = font;
            if (kind == FontKind.TYPE_0) {
                COSBase descendants = direct(font.getItem(DESCENDANT_FONTS));
                if (!(descendants instanceof COSArray)
                        || ((COSArray) descendants).size() != 1) {
                    throw new IOException("Type 0 descendant is malformed");
                }
                COSBase descendant = direct(((COSArray) descendants).get(0));
                if (!(descendant instanceof COSDictionary)
                        || descendant instanceof COSStream) {
                    throw new IOException("Type 0 descendant is malformed");
                }
                descriptorSource = (COSDictionary) descendant;
                COSBase descendantType = direct(
                        descriptorSource.getItem(COSName.TYPE));
                COSBase descendantSubtype = direct(
                        descriptorSource.getItem(COSName.SUBTYPE));
                if (!FONT.equals(descendantType)
                        || (!(COSName.CID_FONT_TYPE0.equals(descendantSubtype)
                                || COSName.CID_FONT_TYPE2.equals(
                                        descendantSubtype)))) {
                    throw new IOException("Type 0 descendant kind is malformed");
                }
                PdfName descendantName = baseFont(descriptorSource);
                String descendantSubset = subsetPrefix(descendantName);
                if (!java.util.Objects.equals(subset, descendantSubset)) {
                    throw new IOException("Type 0 subset identity is inconsistent");
                }
            }

            Embedding embedding;
            if (kind == FontKind.TYPE_3) {
                COSBase charProcs = direct(font.getItem(CHAR_PROCS));
                if (!(charProcs instanceof COSDictionary)
                        || charProcs instanceof COSStream) {
                    throw new IOException("Type 3 CharProcs is malformed");
                }
                accountValues(((COSDictionary) charProcs).size());
                for (Map.Entry<COSName, COSBase> glyph
                        : ((COSDictionary) charProcs).entrySet()) {
                    if (!(direct(glyph.getValue()) instanceof COSStream)) {
                        throw new IOException("Type 3 glyph program is malformed");
                    }
                }
                embedding = Embedding.EMBEDDED;
            } else {
                embedding = embedding(descriptorSource);
            }
            return new FontInfo(
                    kind,
                    FontResource.Status.SUPPORTED,
                    embedding,
                    baseName,
                    subset);
        }

        private Embedding embedding(COSDictionary font) throws IOException {
            COSBase descriptorValue = direct(font.getItem(FONT_DESCRIPTOR));
            if (descriptorValue == null) {
                return Embedding.NOT_EMBEDDED;
            }
            if (!(descriptorValue instanceof COSDictionary)
                    || descriptorValue instanceof COSStream) {
                throw new IOException("FontDescriptor is malformed");
            }
            COSDictionary descriptor = (COSDictionary) descriptorValue;
            int programs = 0;
            COSName[] names = {FONT_FILE, FONT_FILE_2, FONT_FILE_3};
            for (COSName name : names) {
                COSBase value = direct(descriptor.getItem(name));
                if (value != null) {
                    if (!(value instanceof COSStream)) {
                        throw new IOException("Embedded font program is malformed");
                    }
                    programs++;
                }
            }
            if (programs > 1) {
                throw new IOException("FontDescriptor has conflicting programs");
            }
            return programs == 1 ? Embedding.EMBEDDED : Embedding.NOT_EMBEDDED;
        }

        private PdfName baseFont(COSDictionary font) throws IOException {
            COSBase value = direct(font.getItem(BASE_FONT));
            if (value == null) {
                return null;
            }
            if (!(value instanceof COSName)) {
                throw new IOException("BaseFont is malformed");
            }
            return PdfName.of(((COSName) value).getName());
        }

        private Registration<ImageBuilder> imageBuilder(
                COSBase raw,
                ResourceDeclaration declaration) throws IOException {
            ObjectReference reference = reference(raw);
            if (reference != null) {
                RecordBuilder existing = indirect.get(reference);
                if (existing != null) {
                    requireKind(existing, Kind.IMAGE);
                    existing.add(declaration);
                    return new Registration<ImageBuilder>(
                            (ImageBuilder) existing,
                            false);
                }
            }
            ImageBuilder builder = new ImageBuilder(reference);
            builder.add(declaration);
            add(builder);
            return new Registration<ImageBuilder>(builder, true);
        }

        private void addGeneric(
                COSBase raw,
                Kind kind,
                ResourceDeclaration declaration) throws IOException {
            ObjectReference reference = reference(raw);
            if (reference != null) {
                RecordBuilder existing = indirect.get(reference);
                if (existing != null) {
                    requireKind(existing, kind);
                    existing.add(declaration);
                    return;
                }
            }
            GenericBuilder builder = new GenericBuilder(kind, reference);
            builder.add(declaration);
            add(builder);
        }

        private void add(RecordBuilder builder) {
            ordered.add(builder);
            if (builder.reference != null) {
                indirect.put(builder.reference, builder);
            }
        }

        private ObjectReference reference(COSBase raw) throws IOException {
            if (!(raw instanceof COSObject)) {
                return null;
            }
            try {
                return valueAdapter.resourceReference((COSObject) raw);
            } catch (DocumentFailure failure) {
                throw new IOException("Indirect resource is unavailable");
            }
        }

        private DocumentResourceInventory result() throws IOException {
            IdentityHashMap<RecordBuilder, DocumentResource> built =
                    new IdentityHashMap<RecordBuilder, DocumentResource>();
            IdentityHashMap<RecordBuilder, Boolean> building =
                    new IdentityHashMap<RecordBuilder, Boolean>();
            List<DocumentResource> resources =
                    new ArrayList<DocumentResource>(ordered.size());
            for (RecordBuilder builder : ordered) {
                resources.add(build(builder, built, building));
            }
            return new DocumentResourceInventory(resources);
        }

        private DocumentResource build(
                RecordBuilder builder,
                IdentityHashMap<RecordBuilder, DocumentResource> built,
                IdentityHashMap<RecordBuilder, Boolean> building)
                throws IOException {
            DocumentResource existing = built.get(builder);
            if (existing != null) {
                return existing;
            }
            if (building.put(builder, Boolean.TRUE) != null) {
                throw new IOException("Resource relationship is cyclic");
            }
            try {
                DocumentResource result;
                if (builder instanceof ImageBuilder) {
                    ImageBuilder image = (ImageBuilder) builder;
                    ImageResource.Mask explicit = publicMask(
                            image.explicitMask, built, building);
                    ImageResource.Mask soft = publicMask(
                            image.softMask, built, building);
                    List<ImageResource.Filter> filters =
                            new ArrayList<ImageResource.Filter>(
                                    image.metadata.filters.specs.size());
                    for (FilterSpec spec : image.metadata.filters.specs) {
                        filters.add(spec.publicValue());
                    }
                    result = new ImageResource(
                            image.reference,
                            image.declarations,
                            image.pages(),
                            image.metadata.width,
                            image.metadata.height,
                            image.metadata.bits,
                            image.metadata.components,
                            image.metadata.imageMask,
                            image.metadata.embeddedSoftMask,
                            image.metadata.color.publicValue(),
                            filters,
                            explicit,
                            soft,
                            new ImageResource.ByteData(
                                    byteAccess.includesEncoded(),
                                    image.encodedAvailability,
                                    image.encodedBytes),
                            new ImageResource.ByteData(
                                    byteAccess.includesDecoded(),
                                    image.decodedAvailability,
                                    image.decodedBytes));
                } else if (builder instanceof FontBuilder) {
                    FontBuilder font = (FontBuilder) builder;
                    result = new FontResource(
                            font.reference,
                            font.declarations,
                            font.pages(),
                            font.info.kind,
                            font.info.status,
                            font.info.embedding,
                            font.info.baseName,
                            font.info.subsetPrefix);
                } else {
                    result = new DocumentResource(
                            builder.kind,
                            builder.reference,
                            builder.declarations,
                            builder.pages());
                }
                built.put(builder, result);
                return result;
            } finally {
                building.remove(builder);
            }
        }

        private ImageResource.Mask publicMask(
                MaskLink link,
                IdentityHashMap<RecordBuilder, DocumentResource> built,
                IdentityHashMap<RecordBuilder, Boolean> building)
                throws IOException {
            if (link == null) {
                return null;
            }
            ImageResource target = link.target == null
                    ? null
                    : (ImageResource) build(link.target, built, building);
            return new ImageResource.Mask(link.kind, target, link.colorKeyRanges);
        }

        private void accountValues(long count) throws ResourceLimitException {
            if (count < 0L
                    || traversedValues
                    > limits.getMaximumTraversedResourceValues() - count) {
                throw new ResourceLimitException();
            }
            traversedValues += count;
        }

        private void checkDepth(int depth) throws ResourceLimitException {
            if (depth > limits.getMaximumResourceTraversalDepth()) {
                throw new ResourceLimitException();
            }
        }

        private void accountPixels(int width, int height)
                throws ResourceLimitException {
            long pixels = multiply(width, height);
            if (decodedPixels > limits.getMaximumDecodedPixels() - pixels) {
                throw new ResourceLimitException();
            }
            decodedPixels += pixels;
        }

        private void accountDecompressed(long count)
                throws ResourceLimitException {
            if (count < 0L
                    || decompressedBytes
                    > limits.getMaximumDecompressedBytes() - count) {
                throw new ResourceLimitException();
            }
            decompressedBytes += count;
        }

        private void accountReturned(long count) throws ResourceLimitException {
            if (count < 0L
                    || returnedBytes > limits.getMaximumReturnedBytes() - count) {
                throw new ResourceLimitException();
            }
            returnedBytes += count;
        }

        private static long multiply(long left, long right)
                throws ResourceLimitException {
            if (left < 0L || right < 0L
                    || (left != 0L && right > Long.MAX_VALUE / left)) {
                throw new ResourceLimitException();
            }
            return left * right;
        }

        private List<Map.Entry<COSName, COSBase>> sorted(
                COSDictionary dictionary)
                throws IOException, ResourceLimitException {
            List<Map.Entry<COSName, COSBase>> entries =
                    new ArrayList<Map.Entry<COSName, COSBase>>();
            long remaining = limits.getMaximumTraversedResourceValues()
                    - traversedValues;
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                if (direct(entry.getValue()) == null) {
                    continue;
                }
                if (entries.size() >= remaining) {
                    throw new ResourceLimitException();
                }
                entries.add(entry);
            }
            Collections.sort(entries, ENTRY_ORDER);
            return entries;
        }

        private static COSBase direct(COSBase raw) throws IOException {
            COSBase value = raw;
            IdentityHashMap<COSObject, Boolean> seen =
                    new IdentityHashMap<COSObject, Boolean>();
            while (value instanceof COSObject) {
                COSObject object = (COSObject) value;
                if (seen.put(object, Boolean.TRUE) != null) {
                    throw new IOException("Indirect object chain is cyclic");
                }
                value = object.getObject();
            }
            return value instanceof COSNull ? null : value;
        }

        private static ResourceDeclaration declaration(
                int page,
                List<ResourceDeclaration.Segment> path) {
            return new ResourceDeclaration(page, path);
        }

        private static ResourceDeclaration.Segment segment(
                COSName category,
                COSName name) {
            return new ResourceDeclaration.Segment(
                    PdfName.of(category.getName()),
                    PdfName.of(name.getName()));
        }

        private static List<ResourceDeclaration.Segment> append(
                List<ResourceDeclaration.Segment> path,
                ResourceDeclaration.Segment segment) {
            List<ResourceDeclaration.Segment> result =
                    new ArrayList<ResourceDeclaration.Segment>(path.size() + 1);
            result.addAll(path);
            result.add(segment);
            return result;
        }

        private static boolean isNamedCategory(COSName name) {
            return EXT_G_STATE.equals(name)
                    || COLOR_SPACE.equals(name)
                    || PATTERN.equals(name)
                    || SHADING.equals(name)
                    || XOBJECT.equals(name)
                    || FONT.equals(name)
                    || PROPERTIES.equals(name);
        }

        private static Kind kind(COSName category) {
            if (EXT_G_STATE.equals(category)) {
                return Kind.EXTENDED_GRAPHICS_STATE;
            }
            if (COLOR_SPACE.equals(category)) {
                return Kind.COLOR_SPACE;
            }
            if (PATTERN.equals(category)) {
                return Kind.PATTERN;
            }
            if (SHADING.equals(category)) {
                return Kind.SHADING;
            }
            if (PROPERTIES.equals(category)) {
                return Kind.PROPERTIES;
            }
            return Kind.OTHER;
        }

        private static void requireKind(RecordBuilder builder, Kind kind)
                throws IOException {
            if (builder.kind != kind) {
                throw new IOException("Indirect resource has conflicting kinds");
            }
        }

        private static int positiveInt(COSBase raw, String label)
                throws IOException {
            COSBase value = direct(raw);
            if (!(value instanceof COSInteger)) {
                throw new IOException(label + " is not an integer");
            }
            long number = ((COSInteger) value).longValue();
            if (number < 1L || number > Integer.MAX_VALUE) {
                throw new IOException(label + " is out of range");
            }
            return (int) number;
        }

        private static boolean booleanValue(COSBase raw, boolean defaultValue)
                throws IOException {
            COSBase value = direct(raw);
            if (value == null) {
                return defaultValue;
            }
            if (!(value instanceof COSBoolean)) {
                throw new IOException("Boolean image metadata is malformed");
            }
            return ((COSBoolean) value).getValue();
        }

        private static void requireNumberArray(
                COSBase raw,
                int size,
                String label) throws IOException {
            COSBase value = direct(raw);
            if (!(value instanceof COSArray) || ((COSArray) value).size() != size) {
                throw new IOException(label + " is malformed");
            }
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                COSBase member = direct(array.get(index));
                if (!(member instanceof COSNumber)) {
                    throw new IOException(label + " contains a non-number");
                }
                double number = ((COSNumber) member).floatValue();
                if (Double.isNaN(number) || Double.isInfinite(number)) {
                    throw new IOException(label + " contains a non-finite number");
                }
            }
        }

        private static FontKind fontKind(COSName subtype) {
            if (COSName.TYPE0.equals(subtype)) {
                return FontKind.TYPE_0;
            }
            if (COSName.TYPE1.equals(subtype)) {
                return FontKind.TYPE_1;
            }
            if (COSName.MM_TYPE1.equals(subtype)) {
                return FontKind.MM_TYPE_1;
            }
            if (COSName.TRUE_TYPE.equals(subtype)) {
                return FontKind.TRUE_TYPE;
            }
            if (COSName.TYPE3.equals(subtype)) {
                return FontKind.TYPE_3;
            }
            if (COSName.CID_FONT_TYPE0.equals(subtype)) {
                return FontKind.CID_FONT_TYPE_0;
            }
            if (COSName.CID_FONT_TYPE2.equals(subtype)) {
                return FontKind.CID_FONT_TYPE_2;
            }
            return FontKind.OTHER;
        }

        private static String subsetPrefix(PdfName baseName) {
            if (baseName == null) {
                return null;
            }
            String name = baseName.getValue();
            if (name.length() < 8 || name.charAt(6) != '+') {
                return null;
            }
            for (int index = 0; index < 6; index++) {
                char value = name.charAt(index);
                if (value < 'A' || value > 'Z') {
                    return null;
                }
            }
            return name.substring(0, 6);
        }

    }

    private enum FilterKind {
        FLATE("FlateDecode", true, 0, true, false) {
            @Override
            void validate(State state, FilterSpec spec, InputStream input)
                    throws IOException, ResourceLimitException {
                state.validateFlate(spec, input);
            }
        },
        LZW("LZWDecode", false, 0, true, true),
        ASCII_HEX("ASCIIHexDecode", true, 0, false, false) {
            @Override
            void validate(State state, FilterSpec spec, InputStream input)
                    throws IOException, ResourceLimitException {
                state.validateAsciiHex(input);
            }
        },
        ASCII_85("ASCII85Decode", true, 0, false, false) {
            @Override
            void validate(State state, FilterSpec spec, InputStream input)
                    throws IOException, ResourceLimitException {
                state.validateAscii85(input);
            }
        },
        RUN_LENGTH("RunLengthDecode", true, 8, false, false) {
            @Override
            void validate(State state, FilterSpec spec, InputStream input)
                    throws IOException, ResourceLimitException {
                state.validateRunLength(input);
            }
        },
        DCT("DCTDecode", false, 8, false, false),
        JPX("JPXDecode", false, 0, false, false),
        CCITT_FAX("CCITTFaxDecode", false, 1, false, false),
        JBIG_2("JBIG2Decode", false, 1, false, false),
        CRYPT("Crypt", false, 0, false, false);

        private final String pdfName;
        private final boolean decodable;
        private final int requiredBits;
        private final boolean predictorParameters;
        private final boolean earlyChange;

        FilterKind(
                String pdfName,
                boolean decodable,
                int requiredBits,
                boolean predictorParameters,
                boolean earlyChange) {
            this.pdfName = pdfName;
            this.decodable = decodable;
            this.requiredBits = requiredBits;
            this.predictorParameters = predictorParameters;
            this.earlyChange = earlyChange;
        }

        static FilterKind fromName(String name) {
            for (FilterKind kind : values()) {
                if (kind.pdfName.equals(name)) {
                    return kind;
                }
            }
            return null;
        }

        DecodeSupport decodeSupport() {
            return decodable
                    ? DecodeSupport.SUPPORTED
                    : DecodeSupport.UNSUPPORTED;
        }

        int requiredBits() { return requiredBits; }

        boolean hasPredictorParameters() { return predictorParameters; }

        boolean hasEarlyChange() { return earlyChange; }

        void validate(State state, FilterSpec spec, InputStream input)
                throws IOException, ResourceLimitException {
            // Unsupported filters are never passed to the bounded decoder.
        }
    }

    private abstract static class RecordBuilder {

        final Kind kind;
        final ObjectReference reference;
        final List<ResourceDeclaration> declarations =
                new ArrayList<ResourceDeclaration>();
        private final TreeSet<Integer> pageUsage = new TreeSet<Integer>();

        RecordBuilder(Kind kind, ObjectReference reference) {
            this.kind = kind;
            this.reference = reference;
        }

        void add(ResourceDeclaration declaration) {
            declarations.add(declaration);
            pageUsage.add(Integer.valueOf(declaration.getPageNumber()));
        }

        List<Integer> pages() {
            return new ArrayList<Integer>(pageUsage);
        }
    }

    private static final class GenericBuilder extends RecordBuilder {

        GenericBuilder(Kind kind, ObjectReference reference) {
            super(kind, reference);
        }
    }

    private static final class ImageBuilder extends RecordBuilder {

        private ImageMetadata metadata;
        private MaskLink explicitMask;
        private MaskLink softMask;
        private ByteAvailability encodedAvailability;
        private ByteAvailability decodedAvailability;
        private byte[] encodedBytes;
        private byte[] decodedBytes;

        ImageBuilder(ObjectReference reference) {
            super(Kind.IMAGE, reference);
        }

        void setExplicit(MaskLink value) throws IOException {
            if (explicitMask != null && !explicitMask.same(value)) {
                throw new IOException("Shared image explicit mask is inconsistent");
            }
            explicitMask = value;
        }

        void setSoft(MaskLink value) throws IOException {
            if (softMask != null && !softMask.same(value)) {
                throw new IOException("Shared image soft mask is inconsistent");
            }
            softMask = value;
        }
    }

    private static final class FontBuilder extends RecordBuilder {

        private final FontInfo info;

        FontBuilder(ObjectReference reference, FontInfo info) {
            super(Kind.FONT, reference);
            this.info = info;
        }
    }

    private static final class Registration<T extends RecordBuilder> {

        private final T builder;
        private final boolean created;

        Registration(T builder, boolean created) {
            this.builder = builder;
            this.created = created;
        }
    }

    private static final class ImageMetadata {

        private final int width;
        private final int height;
        private final Integer bits;
        private final Integer components;
        private final boolean imageMask;
        private final ImageResource.EmbeddedSoftMask embeddedSoftMask;
        private final ColorInfo color;
        private final FilterSequence filters;
        private final boolean external;

        ImageMetadata(
                int width,
                int height,
                Integer bits,
                Integer components,
                boolean imageMask,
                ImageResource.EmbeddedSoftMask embeddedSoftMask,
                ColorInfo color,
                FilterSequence filters,
                boolean external) {
            this.width = width;
            this.height = height;
            this.bits = bits;
            this.components = components;
            this.imageMask = imageMask;
            this.embeddedSoftMask = embeddedSoftMask;
            this.color = color;
            this.filters = filters;
            this.external = external;
        }

        boolean same(ImageMetadata other) {
            return width == other.width
                    && height == other.height
                    && java.util.Objects.equals(bits, other.bits)
                    && java.util.Objects.equals(components, other.components)
                    && imageMask == other.imageMask
                    && embeddedSoftMask == other.embeddedSoftMask
                    && external == other.external
                    && color.same(other.color)
                    && filters.same(other.filters);
        }
    }

    private static final class FilterSequence {

        private final List<FilterSpec> specs;
        private final boolean supported;

        FilterSequence(List<FilterSpec> specs, boolean supported) {
            this.specs = specs;
            this.supported = supported;
        }

        boolean has(FilterKind kind) {
            for (FilterSpec spec : specs) {
                if (kind == spec.kind) {
                    return true;
                }
            }
            return false;
        }

        boolean same(FilterSequence other) {
            if (supported != other.supported || specs.size() != other.specs.size()) {
                return false;
            }
            for (int index = 0; index < specs.size(); index++) {
                if (!specs.get(index).same(other.specs.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class FilterSpec {

        private final PdfName declared;
        private final FilterKind kind;
        private final Integer predictor;
        private final Integer colors;
        private final Integer bits;
        private final Integer columns;
        private final Integer earlyChange;

        FilterSpec(
                COSName declared,
                FilterKind kind,
                Integer predictor,
                Integer colors,
                Integer bits,
                Integer columns,
                Integer earlyChange) {
            this.declared = PdfName.of(declared.getName());
            this.kind = kind;
            this.predictor = predictor;
            this.colors = colors;
            this.bits = bits;
            this.columns = columns;
            this.earlyChange = earlyChange;
        }

        ImageResource.Filter publicValue() {
            return new ImageResource.Filter(
                    declared,
                    kind.decodeSupport(),
                    predictor,
                    colors,
                    bits,
                    columns,
                    earlyChange);
        }

        boolean same(FilterSpec other) {
            return declared.equals(other.declared)
                    && kind == other.kind
                    && java.util.Objects.equals(predictor, other.predictor)
                    && java.util.Objects.equals(colors, other.colors)
                    && java.util.Objects.equals(bits, other.bits)
                    && java.util.Objects.equals(columns, other.columns)
                    && java.util.Objects.equals(earlyChange, other.earlyChange);
        }
    }

    private static final class ColorInfo {

        private final ColorStatus status;
        private final ColorFamily family;
        private final PdfName declared;
        private final PdfName resolved;
        private final Integer components;
        private final double[] matteRanges;

        ColorInfo(
                ColorStatus status,
                ColorFamily family,
                PdfName declared,
                PdfName resolved,
                Integer components,
                double[] matteRanges) {
            this.status = status;
            this.family = family;
            this.declared = declared;
            this.resolved = resolved;
            this.components = components;
            this.matteRanges = matteRanges == null
                    ? null
                    : matteRanges.clone();
        }

        static ColorInfo imageMask() {
            return new ColorInfo(
                    ColorStatus.SUPPORTED,
                    ColorFamily.NONE,
                    null,
                    null,
                    Integer.valueOf(1),
                    unitRanges(1));
        }

        static ColorInfo malformed(PdfName declared) {
            return new ColorInfo(
                    ColorStatus.MALFORMED,
                    ColorFamily.UNKNOWN,
                    declared,
                    null,
                    null,
                    null);
        }

        static ColorInfo unsupported(PdfName declared) {
            return new ColorInfo(
                    ColorStatus.UNSUPPORTED,
                    ColorFamily.UNKNOWN,
                    declared,
                    null,
                    null,
                    null);
        }

        static ColorInfo supported(
                PdfName declared,
                COSName resolved,
                ColorFamily family,
                int components) {
            return supported(
                    declared,
                    resolved,
                    family,
                    components,
                    unitRanges(components));
        }

        static ColorInfo supported(
                PdfName declared,
                COSName resolved,
                ColorFamily family,
                int components,
                double[] matteRanges) {
            return new ColorInfo(
                    ColorStatus.SUPPORTED,
                    family,
                    declared,
                    PdfName.of(resolved.getName()),
                    Integer.valueOf(components),
                    matteRanges);
        }

        static double[] unitRanges(int components) {
            double[] result = new double[components * 2];
            for (int index = 0; index < components; index++) {
                result[index * 2 + 1] = 1.0d;
            }
            return result;
        }

        ImageResource.ColorSpace publicValue() {
            return new ImageResource.ColorSpace(
                    status,
                    family,
                    declared,
                    resolved,
                    components);
        }

        boolean same(ColorInfo other) {
            return status == other.status
                    && family == other.family
                    && java.util.Objects.equals(declared, other.declared)
                    && java.util.Objects.equals(resolved, other.resolved)
                    && java.util.Objects.equals(components, other.components)
                    && java.util.Arrays.equals(
                            matteRanges,
                            other.matteRanges);
        }
    }

    private static final class MaskLink {

        private final ImageResource.Mask.Kind kind;
        private final ImageBuilder target;
        private final List<PdfNumber> colorKeyRanges;

        MaskLink(
                ImageResource.Mask.Kind kind,
                ImageBuilder target,
                List<PdfNumber> colorKeyRanges) {
            this.kind = kind;
            this.target = target;
            this.colorKeyRanges = colorKeyRanges;
        }

        static MaskLink colorKey(List<PdfNumber> values) {
            return new MaskLink(
                    ImageResource.Mask.Kind.COLOR_KEY,
                    null,
                    new ArrayList<PdfNumber>(values));
        }

        static MaskLink image(
                ImageResource.Mask.Kind kind,
                ImageBuilder target) {
            return new MaskLink(
                    kind,
                    target,
                    Collections.<PdfNumber>emptyList());
        }

        boolean same(MaskLink other) {
            return kind == other.kind
                    && target == other.target
                    && colorKeyRanges.equals(other.colorKeyRanges);
        }
    }

    private static final class FontInfo {

        private final FontKind kind;
        private final FontResource.Status status;
        private final Embedding embedding;
        private final PdfName baseName;
        private final String subsetPrefix;

        FontInfo(
                FontKind kind,
                FontResource.Status status,
                Embedding embedding,
                PdfName baseName,
                String subsetPrefix) {
            this.kind = kind;
            this.status = status;
            this.embedding = embedding;
            this.baseName = baseName;
            this.subsetPrefix = subsetPrefix;
        }
    }

    private static final class AccountingOutput extends OutputStream {

        private static final int MAXIMUM_ARRAY_SIZE = Integer.MAX_VALUE - 8;

        private final State state;
        private final boolean decompressed;
        private final boolean returned;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        AccountingOutput(State state, boolean decompressed, boolean returned) {
            this.state = state;
            this.decompressed = decompressed;
            this.returned = returned;
        }

        @Override
        public void write(int value) throws IOException {
            account(1);
            output.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length)
                throws IOException {
            if (values == null
                    || offset < 0
                    || length < 0
                    || offset > values.length - length) {
                throw new IndexOutOfBoundsException();
            }
            account(length);
            output.write(values, offset, length);
        }

        private void account(int count) throws ResourceLimitIOException {
            if (count < 0 || output.size() > MAXIMUM_ARRAY_SIZE - count) {
                throw new ResourceLimitIOException();
            }
            try {
                if (decompressed) {
                    state.accountDecompressed(count);
                }
                if (returned) {
                    state.accountReturned(count);
                }
            } catch (ResourceLimitException exhausted) {
                throw new ResourceLimitIOException();
            }
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }
    }

    private static final class ResourceLimitException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class ResourceLimitIOException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }
}
