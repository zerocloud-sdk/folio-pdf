package net.zerocloud.pdf.migration.itext7.contract;

final class ContractTestProperties {

    private ContractTestProperties() {
    }

    static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }
}
