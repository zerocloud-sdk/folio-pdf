package net.zerocloud.pdf.command;

import net.zerocloud.pdf.DocumentCommand;

/**
 * Adds one library-default blank page to the current document.
 *
 * @since 0.1.0
 */
public final class AddBlankPage implements DocumentCommand {

    /** The immutable command instance. */
    public static final AddBlankPage INSTANCE = new AddBlankPage();

    private AddBlankPage() {
    }
}
