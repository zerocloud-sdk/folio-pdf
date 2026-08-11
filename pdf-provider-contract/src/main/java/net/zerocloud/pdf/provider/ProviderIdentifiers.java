package net.zerocloud.pdf.provider;

import java.util.Objects;
import java.util.regex.Pattern;

final class ProviderIdentifiers {

    private static final Pattern STABLE_ID = Pattern.compile(
            "[a-z0-9]+(?:[.-][a-z0-9]+)*");

    private ProviderIdentifiers() {
    }

    static String requireStableId(String value, String name) {
        String required = Objects.requireNonNull(value, name);
        if (!STABLE_ID.matcher(required).matches()) {
            throw new IllegalArgumentException(
                    name + " must be a lowercase stable identifier");
        }
        return required;
    }

    static String requireText(String value, String name) {
        String required = Objects.requireNonNull(value, name);
        if (required.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }
}
