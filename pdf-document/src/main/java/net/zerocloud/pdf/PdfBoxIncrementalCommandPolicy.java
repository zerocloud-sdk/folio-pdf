package net.zerocloud.pdf;

import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateDocumentInfo;

/** Closed version-1 classification of commands representable incrementally. */
final class PdfBoxIncrementalCommandPolicy {

    private PdfBoxIncrementalCommandPolicy() {
    }

    static boolean supports(DocumentCommand command) {
        return command == AddBlankPage.INSTANCE
                || command instanceof InsertBlankPage
                || command instanceof RemovePages
                || command instanceof MovePages
                || command instanceof CopyPages
                || command instanceof MergeDocuments
                || command instanceof UpdateDocumentInfo
                || command instanceof SetXmpMetadata
                || command instanceof ReplaceOutlineTree
                || command instanceof SetNamedDestinations
                || command instanceof EmbedFile
                || command instanceof UpdateAnnotations
                || command instanceof UpdateActions
                || command instanceof FlattenAnnotations
                || command instanceof DocumentPatch;
    }
}
