package net.zerocloud.pdf.acceptance;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.zxing.common.BitMatrix;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;

/** Actual published PDF Values, with no backend parser or encoder-private seam. */
final class T31BarcodeAssertions {
    private T31BarcodeAssertions() { }

    static Observation inspect(Path pdf) {
        try {
            return new DocumentWorkflow().execute(WorkflowRequest.open(pdf,SaveMode.REWRITE),session -> {
                List<BitMatrix> matrices=new ArrayList<BitMatrix>(); StringBuilder findings=new StringBuilder();
                try {
                    List<T31BarcodeProfile.Fixture> fixtures=T31BarcodeProfile.fixtures();
                    require(session.query(PageCount.INSTANCE)==fixtures.size(),"Unexpected T31 page count");
                    Map<String,PdfIndirectReference> shared=new HashMap<String,PdfIndirectReference>(); int reused=0;
                    int page=0;
                    for (T31BarcodeProfile.Fixture fixture : fixtures) {
                        PdfDictionary dictionary=page(session,++page);
                        require(dictionary.get(PdfName.of("Rotate"))==null,fixture.id+" unexpected page rotation");
                        PdfArray media=(PdfArray)resolve(session,dictionary.get(PdfName.of("MediaBox")));
                        numbers(media,new double[] {0,0,612,792},"page box");
                        PdfDictionary resources=(PdfDictionary)resolve(session,dictionary.get(PdfName.of("Resources")));
                        require(resources.size()==1,"Only barcode XObject resources are expected");
                        PdfDictionary objects=(PdfDictionary)resolve(session,resources.get(PdfName.of("XObject")));
                        require(objects.size()==1,fixture.id+" requires exactly one reusable Form");
                        PdfName name=objects.getEntry(0).getName(); PdfValue reference=objects.getEntry(0).getValue();
                        require(reference instanceof PdfIndirectReference,"Barcode Form must be indirect and reusable");
                        PdfStream form=(PdfStream)resolve(session,reference);
                        PdfDictionary properties=form.getDictionary();
                        require(PdfName.of("XObject").equals(properties.get(PdfName.of("Type")))
                                && PdfName.of("Form").equals(properties.get(PdfName.of("Subtype"))),"Expected path Form XObject");
                        require(PdfNumber.of(1).equals(properties.get(PdfName.of("FormType"))),"Expected Form version 1");
                        numbers((PdfArray)resolve(session,properties.get(PdfName.of("BBox"))),
                                new double[] {0,0,fixture.widthPoints(),fixture.heightPoints()},fixture.id+" Form bounds");
                        require(properties.get(PdfName.of("Matrix"))==null,"Unexpected Form matrix");
                        require(((PdfDictionary)resolve(session,properties.get(PdfName.of("Resources")))).size()==0,"Barcode must not depend on fonts/images/nested Forms");
                        String content=new String(form.readBytes(),StandardCharsets.US_ASCII);
                        BitMatrix matrix=modules(content,fixture);
                        placement(streamText(session,dictionary.get(PdfName.of("Contents"))),name,fixture);
                        String reuseKey=fixture.widthPoints()+"/"+fixture.heightPoints()+"/"+content;
                        PdfIndirectReference previous=shared.put(reuseKey,(PdfIndirectReference)reference);
                        if (previous!=null) { require(previous.equals(reference),"Equivalent vectors were not reused across pages"); reused++; }
                        findings.append("Page ").append(page).append(' ').append(T31MatrixOracle.inspect(matrix,fixture))
                                .append("; Form=").append(reference).append("; box=0,0,").append(fixture.widthPoints()).append(',').append(fixture.heightPoints())
                                .append("; module=").append(fixture.barcode.getModuleWidth()).append('x').append(fixture.barcode.getModuleHeight())
                                .append("; quiet=").append(fixture.barcode.getQuietZone()).append("; placement=")
                                .append(transform(fixture).toString()).append("; vector geometry/color/reuse=pass\n");
                        matrices.add(matrix);
                    }
                    require(reused>=8,"Expected repeated declarations to demonstrate Form reuse");
                    findings.append("Reused Form observations across pages: ").append(reused).append('\n');
                    return new Observation(true,matrices,findings.toString());
                } catch (Exception failure) {
                    return new Observation(false,matrices,findings+"FAIL at page "+(matrices.size()+1)+": "+failure.getClass().getSimpleName()+": "+failure.getMessage());
                }
            }).getResult();
        } catch (DocumentFailure failure) { return new Observation(false,Collections.<BitMatrix>emptyList(),"PDF observation failed: "+failure.getCode()); }
    }

    static BitMatrix modules(String content,T31BarcodeProfile.Fixture fixture) {
        BitMatrix matrix=new BitMatrix(fixture.width,fixture.height);
        List<Double> operands=new ArrayList<Double>(); double[] color={-1,-1,-1}; double[] rectangle=null; int painted=0;
        for (String token : content.trim().split("\\s+")) {
            if (number(token)) { operands.add(Double.valueOf(token)); continue; }
            if (token.equals("g")) { count(operands,1,"gray color"); color=new double[] {operands.get(0),operands.get(0),operands.get(0)}; }
            else if (token.equals("rg")) { count(operands,3,"RGB color"); color=new double[] {operands.get(0),operands.get(1),operands.get(2)}; }
            else if (token.equals("re")) {
                count(operands,4,"module rectangle"); require(rectangle==null,"Unpainted rectangle");
                rectangle=new double[4]; for (int index=0;index<4;index++) { rectangle[index]=operands.get(index); }
            } else if (token.equals("f")) {
                count(operands,0,"fill"); require(rectangle!=null,"Missing rectangle");
                double[] expected=fixture.barcode.getForegroundRgb();
                for (int index=0;index<3;index++) { near(expected[index],color[index],"Barcode foreground"); }
                int x=integer((rectangle[0]-fixture.barcode.getQuietZone())/fixture.barcode.getModuleWidth());
                int y=integer((rectangle[1]-fixture.barcode.getQuietZone())/fixture.barcode.getModuleHeight());
                int width=integer(rectangle[2]/fixture.barcode.getModuleWidth()),height=integer(rectangle[3]/fixture.barcode.getModuleHeight());
                require(x>=0 && y>=0 && width>0 && height>0 && x+width<=fixture.width && y+height<=fixture.height,"Paint outside matrix/inside quiet zone");
                for (int dy=0;dy<height;dy++) { for (int dx=0;dx<width;dx++) {
                    int row=fixture.height-1-y-dy;
                    require(!matrix.get(x+dx,row),"Duplicate/overlapping painted module"); matrix.set(x+dx,row);
                } }
                rectangle=null; painted++;
            } else { throw new IllegalArgumentException("Unexpected Form operator: "+token); }
            operands.clear();
        }
        require(operands.isEmpty() && rectangle==null && painted>0,"Incomplete vector content");
        return matrix;
    }

    private static void placement(String content,PdfName name,T31BarcodeProfile.Fixture fixture) {
        AffineTransform current=new AffineTransform(); Deque<AffineTransform> stack=new ArrayDeque<AffineTransform>();
        List<Double> operands=new ArrayList<Double>(); String resource=null; int draws=0;
        for (String token : content.trim().split("\\s+")) {
            if (number(token)) { operands.add(Double.valueOf(token)); continue; }
            if (token.startsWith("/")) { require(resource==null,"Duplicate resource operand"); resource=token.substring(1); continue; }
            if (token.equals("q")) { count(operands,0,"save state"); stack.push(new AffineTransform(current)); }
            else if (token.equals("Q")) { count(operands,0,"restore state"); require(!stack.isEmpty(),"Unbalanced placement"); current=stack.pop(); }
            else if (token.equals("cm")) {
                count(operands,6,"placement matrix"); current.concatenate(new AffineTransform(operands.get(0),operands.get(1),operands.get(2),operands.get(3),operands.get(4),operands.get(5)));
            } else if (token.equals("Do")) {
                count(operands,0,"Form invocation"); require(name.equals(PdfName.of(resource)),"Unknown Form invocation"); resource=null; draws++;
                double[] expected=new double[6],actual=new double[6]; transform(fixture).getMatrix(expected); current.getMatrix(actual);
                for (int index=0;index<6;index++) { near(expected[index],actual[index],fixture.id+" placement component "+index); }
            } else { throw new IllegalArgumentException("Unexpected page operator: "+token); }
            operands.clear();
        }
        require(stack.isEmpty() && operands.isEmpty() && resource==null && draws==1,"Incomplete/unexpected page content");
        for (double x : new double[] {0,fixture.widthPoints()}) { for (double y : new double[] {0,fixture.heightPoints()}) {
            Point2D point=transform(fixture).transform(new Point2D.Double(x,y),null);
            require(point.getX()>=0 && point.getX()<=612 && point.getY()>=0 && point.getY()<=792,"Profile symbol clipped by page");
        } }
    }
    static AffineTransform transform(T31BarcodeProfile.Fixture fixture) {
        return new AffineTransform(fixture.placement.getA(),fixture.placement.getB(),fixture.placement.getC(),fixture.placement.getD(),fixture.placement.getE(),fixture.placement.getF());
    }
    static PdfDictionary page(DocumentSession session,int page) throws DocumentFailure {
        return (PdfDictionary)session.query(InspectObject.version1(session.query(PageObjectReference.version1(page)),PdfInspectionLimits.of(200000,32<<20)));
    }
    static PdfValue resolve(DocumentSession session,PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(((PdfIndirectReference)value).getReference(),PdfInspectionLimits.of(200000,32<<20))) : value;
    }
    static String streamText(DocumentSession session,PdfValue value) throws DocumentFailure {
        value=resolve(session,value);
        if (value instanceof PdfStream) { return new String(((PdfStream)value).readBytes(),StandardCharsets.US_ASCII)+"\n"; }
        require(value instanceof PdfArray,"Expected content streams"); PdfArray array=(PdfArray)value; StringBuilder text=new StringBuilder();
        for (int index=0;index<array.size();index++) { text.append(streamText(session,array.get(index))); } return text.toString();
    }
    private static void numbers(PdfArray values,double[] expected,String label) throws DocumentFailure {
        require(values.size()==expected.length,label+" length");
        for (int index=0;index<expected.length;index++) { near(expected[index],((PdfNumber)values.get(index)).decimalValue().doubleValue(),label); }
    }
    private static boolean number(String token) { return token.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"); }
    private static void count(List<Double> operands,int count,String label) { require(operands.size()==count,label+" operand count"); }
    private static int integer(double value) { near(Math.rint(value),value,"Module grid alignment"); return (int)Math.rint(value); }
    static void near(double expected,double actual,String label) { require(Double.isFinite(actual) && Math.abs(expected-actual)<=0.0001,label+": "+expected+" != "+actual); }
    static void require(boolean value,String label) { if (!value) { throw new IllegalArgumentException(label); } }

    static final class Observation {
        final boolean passed;
        final List<BitMatrix> matrices;
        final String findings;
        Observation(boolean passed,List<BitMatrix> matrices,String findings) {
            this.passed=passed; this.matrices=Collections.unmodifiableList(new ArrayList<BitMatrix>(matrices)); this.findings=findings;
        }
    }
}
