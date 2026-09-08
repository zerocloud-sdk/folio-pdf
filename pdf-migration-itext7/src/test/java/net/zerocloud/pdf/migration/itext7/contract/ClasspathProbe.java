package net.zerocloud.pdf.migration.itext7.contract;

public final class ClasspathProbe {

    private ClasspathProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        Class.forName(arguments[0], true, ClasspathProbe.class.getClassLoader());
    }
}
