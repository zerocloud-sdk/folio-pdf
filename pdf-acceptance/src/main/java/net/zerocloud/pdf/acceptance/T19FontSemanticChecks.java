package net.zerocloud.pdf.acceptance;

/** One cohesive set of project-owned T19 semantic checks. */
final class T19FontSemanticChecks {

    private final boolean fontResources;
    private final boolean unicodeMappings;
    private final boolean sourceMetrics;
    private final boolean subsetPrograms;
    private final boolean resourceReuse;

    T19FontSemanticChecks(
            boolean fontResources,
            boolean unicodeMappings,
            boolean sourceMetrics,
            boolean subsetPrograms,
            boolean resourceReuse) {
        this.fontResources = fontResources;
        this.unicodeMappings = unicodeMappings;
        this.sourceMetrics = sourceMetrics;
        this.subsetPrograms = subsetPrograms;
        this.resourceReuse = resourceReuse;
    }

    static T19FontSemanticChecks notObserved() {
        return new T19FontSemanticChecks(false, false, false, false, false);
    }

    boolean allPass() {
        return fontResources
                && unicodeMappings
                && sourceMetrics
                && subsetPrograms
                && resourceReuse;
    }

    boolean fontResources() {
        return fontResources;
    }

    boolean unicodeMappings() {
        return unicodeMappings;
    }

    boolean sourceMetrics() {
        return sourceMetrics;
    }

    boolean subsetPrograms() {
        return subsetPrograms;
    }

    boolean resourceReuse() {
        return resourceReuse;
    }
}
