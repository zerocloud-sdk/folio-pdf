package net.zerocloud.pdf.composition;

/**
 * An immutable six-value affine matrix used by a {@link CanvasProgram}.
 *
 * <p>The values map {@code (x,y)} to
 * {@code (a*x+c*y+e,b*x+d*y+f)}. Numeric validity is checked with the complete
 * Canvas Program when a {@code DrawCanvas} command executes, so an invalid
 * program fails atomically through the Document Workflow.</p>
 *
 * @since 0.1.0
 */
public final class CanvasMatrix {

    /** The identity matrix. */
    public static final CanvasMatrix IDENTITY =
            new CanvasMatrix(1d, 0d, 0d, 1d, 0d, 0d);

    private final double a;
    private final double b;
    private final double c;
    private final double d;
    private final double e;
    private final double f;

    private CanvasMatrix(
            double a,
            double b,
            double c,
            double d,
            double e,
            double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    /**
     * Creates an affine matrix. Validation is deferred until command
     * execution so the whole program is accepted or rejected together.
     *
     * @return the immutable matrix
     */
    public static CanvasMatrix of(
            double a,
            double b,
            double c,
            double d,
            double e,
            double f) {
        return new CanvasMatrix(a, b, c, d, e, f);
    }

    /** @return matrix a */ public double getA() { return a; }
    /** @return matrix b */ public double getB() { return b; }
    /** @return matrix c */ public double getC() { return c; }
    /** @return matrix d */ public double getD() { return d; }
    /** @return matrix e */ public double getE() { return e; }
    /** @return matrix f */ public double getF() { return f; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasMatrix)) {
            return false;
        }
        CanvasMatrix other = (CanvasMatrix) candidate;
        return bits(a) == bits(other.a)
                && bits(b) == bits(other.b)
                && bits(c) == bits(other.c)
                && bits(d) == bits(other.d)
                && bits(e) == bits(other.e)
                && bits(f) == bits(other.f);
    }

    @Override
    public int hashCode() {
        int result = hash(a);
        result = 31 * result + hash(b);
        result = 31 * result + hash(c);
        result = 31 * result + hash(d);
        result = 31 * result + hash(e);
        result = 31 * result + hash(f);
        return result;
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static int hash(double value) {
        long bits = bits(value);
        return (int) (bits ^ (bits >>> 32));
    }
}
