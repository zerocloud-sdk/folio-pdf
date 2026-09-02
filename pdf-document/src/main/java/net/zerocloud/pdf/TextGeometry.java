package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable geometry for one text-showing item in unrotated default page user
 * space.
 *
 * <p>The six values map {@code (x,y)} to
 * {@code (a*x+c*y+e,b*x+d*y+f)} and include the Form, graphics-state, text,
 * font-size, horizontal-scale, and rise transforms. Page display rotation and
 * physical {@link PageText#getUserUnit() UserUnit} scaling are reported
 * separately. The advance vector describes the transformed font glyph
 * displacement. It excludes character spacing, word spacing, and later
 * positioning adjustments, and is not an ink bounding box.</p>
 *
 * @since 0.1.0
 */
public final class TextGeometry {

    private final BigDecimal a;
    private final BigDecimal b;
    private final BigDecimal c;
    private final BigDecimal d;
    private final BigDecimal e;
    private final BigDecimal f;
    private final BigDecimal advanceX;
    private final BigDecimal advanceY;

    TextGeometry(
            BigDecimal a,
            BigDecimal b,
            BigDecimal c,
            BigDecimal d,
            BigDecimal e,
            BigDecimal f,
            BigDecimal advanceX,
            BigDecimal advanceY) {
        this.a = Objects.requireNonNull(a, "a");
        this.b = Objects.requireNonNull(b, "b");
        this.c = Objects.requireNonNull(c, "c");
        this.d = Objects.requireNonNull(d, "d");
        this.e = Objects.requireNonNull(e, "e");
        this.f = Objects.requireNonNull(f, "f");
        this.advanceX = Objects.requireNonNull(advanceX, "advanceX");
        this.advanceY = Objects.requireNonNull(advanceY, "advanceY");
    }

    /** @return matrix a */ public BigDecimal getA() { return a; }
    /** @return matrix b */ public BigDecimal getB() { return b; }
    /** @return matrix c */ public BigDecimal getC() { return c; }
    /** @return matrix d */ public BigDecimal getD() { return d; }
    /** @return matrix e */ public BigDecimal getE() { return e; }
    /** @return matrix f */ public BigDecimal getF() { return f; }
    /** @return transformed glyph advance x */
    public BigDecimal getAdvanceX() { return advanceX; }
    /** @return transformed glyph advance y */
    public BigDecimal getAdvanceY() { return advanceY; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof TextGeometry)) {
            return false;
        }
        TextGeometry other = (TextGeometry) candidate;
        return a.equals(other.a) && b.equals(other.b)
                && c.equals(other.c) && d.equals(other.d)
                && e.equals(other.e) && f.equals(other.f)
                && advanceX.equals(other.advanceX)
                && advanceY.equals(other.advanceY);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d, e, f, advanceX, advanceY);
    }
}
