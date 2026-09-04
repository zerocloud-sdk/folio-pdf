package net.zerocloud.pdf;

import java.io.IOException;
import org.apache.pdfbox.cos.COSString;

/** Accounted conversion between backend strings and detached PDF Values. */
final class PdfBoxStringSupport {

    private PdfBoxStringSupport() {
    }

    static COSString backendString(
            String source,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        int byteLength = pdfTextStringByteLength(source, resources);
        ownership.retain(byteLength);
        try {
            return new COSString(source);
        } catch (RuntimeException conversionFailure) {
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendString(
            String source,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope()) {
            COSString result = backendString(
                    source, resources, ownership, failureFactory);
            ownership.transfer();
            return result;
        }
    }

    static PdfString detached(
            COSString source,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            // PDFBox exposes raw string bytes only through a defensive copy.
            // Its hex view lets project-owned result bytes be reserved before
            // they are materialized, while preserving every raw byte.
            writeHexadecimal(
                    hexadecimal(source, failureFactory),
                    output,
                    resources,
                    failureFactory);
            return PdfString.fromOwnedBytes(output.finishRetained());
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw failureFactory.create();
        }
    }

    static PdfString detached(
            COSString source,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            writeHexadecimal(
                    hexadecimal(source, failureFactory),
                    output,
                    resources,
                    failureFactory);
            WorkflowResourceContext.OwnedBytes bytes = output.finishWorking();
            return PdfString.fromOwnedBytes(ownership.hold(bytes));
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw failureFactory.create();
        }
    }

    static WorkflowResourceContext.OwnedBytes workingBytes(
            COSString source,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            writeHexadecimal(
                    hexadecimal(source, failureFactory),
                    output,
                    resources,
                    failureFactory);
            return output.finishWorking();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw failureFactory.create();
        }
    }

    static COSString backendCopy(
            COSString source,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedBytes bytes = workingBytes(
                source, resources, failureFactory)) {
            resources.retainOwnedMemory(bytes.getBytes().length);
            try {
                return new COSString(bytes.getBytes());
            } catch (RuntimeException failure) {
                resources.releaseRetainedOwnedMemory(
                        bytes.getBytes().length);
                throw failure;
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException conversionFailure) {
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendCopy(
            COSString source,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedBytes bytes = workingBytes(
                source, resources, failureFactory)) {
            ownership.retain(bytes.getBytes().length);
            return new COSString(bytes.getBytes());
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException conversionFailure) {
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendCopy(
            PdfString source,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        byte[] bytes = source.bytesForWorkflow();
        resources.retainOwnedMemory(bytes.length);
        try {
            return new COSString(bytes);
        } catch (RuntimeException failure) {
            resources.releaseRetainedOwnedMemory(bytes.length);
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendBytes(
            byte[] source,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        ownership.retain(source.length);
        try {
            return new COSString(source);
        } catch (RuntimeException failure) {
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendCopy(
            PdfString source,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        byte[] bytes = source.bytesForWorkflow();
        ownership.retain(bytes.length);
        try {
            return new COSString(bytes);
        } catch (RuntimeException failure) {
            resources.rethrowTerminalFailure();
            throw failureFactory.create();
        }
    }

    static COSString backendCopyWithAsciiSuffix(
            COSString source,
            String suffix,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            writeHexadecimal(
                    hexadecimal(source, failureFactory),
                    output,
                    resources,
                    failureFactory);
            for (int index = 0; index < suffix.length(); index++) {
                char value = suffix.charAt(index);
                if (value > 0x7f) {
                    throw failureFactory.create();
                }
                output.write(value);
            }
            try (WorkflowResourceContext.OwnedBytes bytes =
                    output.finishWorking()) {
                resources.retainOwnedMemory(bytes.getBytes().length);
                try {
                    return new COSString(bytes.getBytes());
                } catch (RuntimeException failure) {
                    resources.releaseRetainedOwnedMemory(
                            bytes.getBytes().length);
                    throw failure;
                }
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw failureFactory.create();
        }
    }

    static COSString backendCopyWithAsciiSuffix(
            COSString source,
            String suffix,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            writeHexadecimal(
                    hexadecimal(source, failureFactory),
                    output,
                    resources,
                    failureFactory);
            for (int index = 0; index < suffix.length(); index++) {
                char value = suffix.charAt(index);
                if (value > 0x7f) {
                    throw failureFactory.create();
                }
                output.write(value);
            }
            try (WorkflowResourceContext.OwnedBytes bytes =
                    output.finishWorking()) {
                ownership.retain(bytes.getBytes().length);
                return new COSString(bytes.getBytes());
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException conversionFailure) {
            resources.rethrowResourceOrTerminalFailure(conversionFailure);
            throw failureFactory.create();
        }
    }

    static int byteLength(
            COSString source,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        return hexadecimal(source, failureFactory).length() / 2;
    }

    static int compare(
            COSString left,
            COSString right,
            WorkflowResourceContext resources) {
        resources.checkpointAsRuntimeException();
        String leftHexadecimal = left.toHexString();
        resources.checkpointAsRuntimeException();
        String rightHexadecimal = right.toHexString();
        int common = Math.min(
                leftHexadecimal.length(), rightHexadecimal.length());
        for (int index = 0; index < common; index += 2) {
            if ((index & 2047) == 0) {
                resources.checkpointAsRuntimeException();
            }
            int leftByte = (digit(leftHexadecimal.charAt(index)) << 4)
                    | digit(leftHexadecimal.charAt(index + 1));
            int rightByte = (digit(rightHexadecimal.charAt(index)) << 4)
                    | digit(rightHexadecimal.charAt(index + 1));
            int difference = leftByte - rightByte;
            if (difference != 0) {
                return difference;
            }
        }
        return leftHexadecimal.length() - rightHexadecimal.length();
    }

    static int compareHexadecimal(String left, String right) {
        int common = Math.min(left.length(), right.length());
        for (int index = 0; index < common; index += 2) {
            int leftByte = (digit(left.charAt(index)) << 4)
                    | digit(left.charAt(index + 1));
            int rightByte = (digit(right.charAt(index)) << 4)
                    | digit(right.charAt(index + 1));
            int difference = leftByte - rightByte;
            if (difference != 0) {
                return difference;
            }
        }
        return left.length() - right.length();
    }

    static int compareHexadecimal(
            String left,
            String right,
            WorkflowResourceContext resources) throws DocumentFailure {
        int common = Math.min(left.length(), right.length());
        for (int index = 0; index < common; index += 2) {
            if ((index & 2047) == 0) {
                resources.checkpoint();
            }
            int leftByte = (digit(left.charAt(index)) << 4)
                    | digit(left.charAt(index + 1));
            int rightByte = (digit(right.charAt(index)) << 4)
                    | digit(right.charAt(index + 1));
            int difference = leftByte - rightByte;
            if (difference != 0) {
                return difference;
            }
        }
        return left.length() - right.length();
    }

    private static int pdfTextStringByteLength(
            String value,
            WorkflowResourceContext resources) throws DocumentFailure {
        boolean pdfDocEncoded = true;
        for (int index = 0; index < value.length(); index++) {
            if ((index & 1023) == 0) {
                resources.checkpoint();
            }
            if (!isPdfDocEncodingCharacter(value.charAt(index))) {
                pdfDocEncoded = false;
            }
        }
        if (pdfDocEncoded) {
            return value.length();
        }
        long length = 2L + 2L * value.length();
        if (length > Integer.MAX_VALUE) {
            throw resources.policyFailure(
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    "The workflow owned-memory limit was exceeded.");
        }
        return (int) length;
    }

    private static boolean isPdfDocEncodingCharacter(char value) {
        if (value <= 0x17
                || (value >= 0x20 && value <= 0x7e)
                || (value >= 0xa1 && value <= 0xac)
                || (value >= 0xae && value <= 0xff)) {
            return true;
        }
        switch (value) {
            case '\u02d8':
            case '\u02c7':
            case '\u02c6':
            case '\u02d9':
            case '\u02dd':
            case '\u02db':
            case '\u02da':
            case '\u02dc':
            case '\ufffd':
            case '\u2022':
            case '\u2020':
            case '\u2021':
            case '\u2026':
            case '\u2014':
            case '\u2013':
            case '\u0192':
            case '\u2044':
            case '\u2039':
            case '\u203a':
            case '\u2212':
            case '\u2030':
            case '\u201e':
            case '\u201c':
            case '\u201d':
            case '\u2018':
            case '\u2019':
            case '\u201a':
            case '\u2122':
            case '\ufb01':
            case '\ufb02':
            case '\u0141':
            case '\u0152':
            case '\u0160':
            case '\u0178':
            case '\u017d':
            case '\u0131':
            case '\u0142':
            case '\u0153':
            case '\u0161':
            case '\u017e':
            case '\u20ac':
                return true;
            default:
                return false;
        }
    }

    private static String hexadecimal(
            COSString source,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        String hexadecimal;
        try {
            hexadecimal = source.toHexString();
        } catch (RuntimeException failure) {
            throw failureFactory.create();
        }
        if ((hexadecimal.length() & 1) != 0) {
            throw failureFactory.create();
        }
        return hexadecimal;
    }

    private static void writeHexadecimal(
            String hexadecimal,
            WorkflowResourceContext.OwnedByteAccumulator output,
            WorkflowResourceContext resources,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws IOException, DocumentFailure {
        for (int index = 0; index < hexadecimal.length(); index += 2) {
            if ((index & 2047) == 0) {
                resources.checkpoint();
            }
            int high = digit(hexadecimal.charAt(index));
            int low = digit(hexadecimal.charAt(index + 1));
            if (high < 0 || low < 0) {
                throw failureFactory.create();
            }
            output.write((high << 4) | low);
        }
    }

    private static int digit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }
}
