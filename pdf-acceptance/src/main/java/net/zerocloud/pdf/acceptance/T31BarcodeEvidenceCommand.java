package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only T31 recorder; never certifies compatibility or independent standards conformance. */
public final class T31BarcodeEvidenceCommand {
    static final String CAPABILITY="composition.barcodes.two-dimensional";
    static final String PROFILE="T31-two-dimensional-barcodes";
    private T31BarcodeEvidenceCommand() { }

    /** Receives a fresh output directory, three tool pins, profiles directory and release train. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length!=6) { throw new IllegalArgumentException("Expected six T31 evidence arguments"); }
        Path output=Paths.get(arguments[0]).toAbsolutePath().normalize(),artifacts=output.resolve("artifacts");
        requireFresh(output); requireFresh(artifacts); Files.createDirectories(artifacts);
        QpdfPin qpdf=QpdfPin.load(Paths.get(arguments[1])); PdfiumPin pdfium=PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator=ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles=Paths.get(arguments[4]).toAbsolutePath().normalize(),root=profiles.getParent().getParent(); String release=arguments[5];
        String sources=sources(root,profiles.resolve(PROFILE+".md"));
        EvidenceFiles.write(artifacts.resolve(PROFILE+"-sources.sha256"),sources);
        String provenance=metadata("Source declaration SHA-256",EvidenceFiles.sha256(sources))
                +metadata("ZXing core version","3.5.3")+metadata("ZXing JAR SHA-256","8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82")
                +metadata("OkapiBarcode version","0.5.6")+metadata("OkapiBarcode JAR SHA-256","fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a")
                +metadata("Input hash policy",EvidenceFiles.inputHashPolicy())+"[Source and input declarations](artifacts/"+PROFILE+"-sources.sha256)\n\n";
        EvidenceResult syntax=EvidenceResult.PASS,semantic=EvidenceResult.PASS,visual=EvidenceResult.PASS;
        int decoded=0,rasterDecoded=0,rejected=0,rasterRejected=0;
        StringBuilder semantics=new StringBuilder(),syntaxes=new StringBuilder(),visuals=new StringBuilder();
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            String stem=PROFILE+"-"+profile.name().toLowerCase(Locale.ROOT).replace('_','-');
            Path pdf=artifacts.resolve(stem+".pdf"),reference=artifacts.resolve(stem+"-reference.pdf");
            System.out.println("T31: generate and qualify "+profile+" "+T31BarcodeProfile.fixtures().size()+" pages");
            T31BarcodeProducts.create(pdf,profile);
            T31BarcodeAssertions.Observation observed=T31BarcodeAssertions.inspect(pdf);
            EvidenceFiles.write(artifacts.resolve(stem+"-semantic.txt"),observed.findings);
            semantic=combine(semantic,observed.passed ? EvidenceResult.PASS : EvidenceResult.FAIL); decoded+=observed.matrices.size();
            String hash=EvidenceFiles.idNeutralPdfSha256(pdf);
            String identity=metadata("Execution profile",profile.name())+metadata("Actual PDF SHA-256",EvidenceFiles.sha256(pdf))
                    +metadata("Input ID-neutral SHA-256",hash)+"[Published PDF](artifacts/"+stem+".pdf)\n\n";
            semantics.append(identity).append("[Every reopened Form, module, ECC, payload, control, quiet zone and placement](artifacts/").append(stem).append("-semantic.txt)\n\n");
            EvidenceResult checked=QpdfSyntaxRecorder.record(output,artifacts,qpdf,hash,release,
                    new QpdfSyntaxRecorder.Profile("T31",CAPABILITY,PROFILE,"capabilities/evidence/"+PROFILE+".md",stem+".pdf",stem+"-syntax.md",stem+"-qpdf.txt"));
            syntax=combine(syntax,checked); syntaxes.append(identity).append("[Pinned qpdf](").append(stem).append("-syntax.md)\n\n");
            if (observed.passed) {
                T31BarcodeReference.create(reference,observed);
                identity+=metadata("Qualified geometry reference PDF SHA-256",EvidenceFiles.sha256(reference))
                        +"[Independent Canvas geometry reference](artifacts/"+stem+"-reference.pdf)\n\n";
                T31RasterEvidence.Observation raster=T31RasterEvidence.record(pdf,reference,artifacts,pdfium,comparator);
                visual=combine(visual,raster.result); rasterDecoded+=raster.decodedPages;
                EvidenceFiles.write(artifacts.resolve(stem+"-visual.txt"),raster.findings);
                visuals.append(identity).append("[Actual raster decoding, PNG hashes and ImageMagick results](artifacts/").append(stem).append("-visual.txt)\n\n")
                        .append(rasterLinks(stem,artifacts));
            } else { visual=EvidenceResult.FAIL; visuals.append(identity).append("No geometry reference can be made from a failed matrix qualification.\n\n"); }
        }
        if (semantic==EvidenceResult.PASS) {
            List<T31NegativeControls.Control> controls=T31NegativeControls.create(artifacts.resolve(PROFILE+"-in-process.pdf"),artifacts);
            StringBuilder findings=new StringBuilder();
            for (T31NegativeControls.Control control : controls) {
                T31BarcodeAssertions.Observation observed=T31BarcodeAssertions.inspect(control.pdf);
                boolean refused=!observed.passed && observed.findings.contains(control.finding);
                if (refused) { rejected++; } else { semantic=EvidenceResult.FAIL; }
                String stem="T31-negative-"+control.id;
                EvidenceFiles.write(artifacts.resolve(stem+"-semantic.txt"),observed.findings);
                findings.append(control.id).append(": expected rejection=").append(control.finding).append("; result=").append(refused ? "pass" : "FAIL")
                        .append("; PDF SHA-256=").append(EvidenceFiles.sha256(control.pdf)).append('\n');
                semantics.append("- ").append(control.id).append(": [corrupted PDF](artifacts/").append(stem).append(".pdf), [actual rejection](artifacts/").append(stem).append("-semantic.txt)\n");
            }
            EvidenceFiles.write(artifacts.resolve(PROFILE+"-negative-controls.txt"),findings.toString());
            T31RasterEvidence.Observation negative=T31RasterEvidence.negatives(controls,artifacts,pdfium,comparator);
            visual=combine(visual,negative.result); rasterRejected=negative.decodedPages;
            EvidenceFiles.write(artifacts.resolve(PROFILE+"-negative-visual.txt"),negative.findings);
            visuals.append("[Negative PDFium raster decoding, AE metrics and hashes](artifacts/").append(PROFILE).append("-negative-visual.txt)\n\n");
            for (T31NegativeControls.Control control : controls) {
                String stem="T31-negative-"+control.id;
                if (Files.exists(artifacts.resolve(stem+"-actual.png"))) {
                    visuals.append("- ").append(control.id).append(": [actual raster](artifacts/").append(stem).append("-actual.png), [difference](artifacts/").append(stem).append("-difference.png)\n");
                }
            }
        }
        EvidenceFiles.write(output.resolve(PROFILE+"-semantic.md"),"# T31 independent semantic evidence\n\n"
                +record("semantic",semantic,"project-test","folio-pdf-t31-semantic-assertions",release,release)+provenance
                +metadata("Decoded pages",Integer.toString(decoded))+metadata("Rejected PDF negative controls",Integer.toString(rejected))
                +"Every published Form is decoded with independent ZXing algorithms and original structural assertions. Exact function modules, ECC without correction, compaction/control metadata, quiet margins, boxes, transforms, color and reuse are checked through public PDF Values.\n\n"+semantics);
        EvidenceFiles.write(output.resolve(PROFILE+"-syntax.md"),"# T31 independent syntax evidence\n\n"
                +record("syntax",syntax,"external-tool","qpdf",qpdf.version(),release)+provenance+syntaxes
                +"qpdf checks syntax; this is not independent PDF standards certification.\n");
        EvidenceFiles.write(output.resolve(PROFILE+"-visual.md"),"# T31 independent raster and visual evidence\n\n"
                +record("visual",visual,"external-tool","pdfium-cli",pdfium.producerVersion(),release)+provenance
                +metadata("Raster-decoded pages",Integer.toString(rasterDecoded))+metadata("Rejected raster negative controls",Integer.toString(rasterRejected))
                +"Actual PDFium pixels are sampled using declared inverse placement, decoded afresh and checked throughout module interiors and quiet zones. No vector answer or product metadata is passed to the raster decoder. "
                +"Each completely qualified matrix is separately painted with existing Canvas commands using literal geometry/color, allowing valid QR masks and automatic segmentations. "
                +"The reference cannot qualify its own matrix. Pinned ImageMagick "+comparator.version()+" compares every full page at AE 0, fuzz 0%; all fixture edges align at 288 DPI. "
                +"Every page is 612x792 points, rendered to opaque white 8-bit sRGB 2448x3168 PNG. Negative PDFs must fail raster decoding and have positive AE.\n\n"+visuals);
        EvidenceFiles.write(output.resolve(PROFILE+"-standards.md"),"# T31 standards evidence\n\n"
                +record("standards",EvidenceResult.INDETERMINATE,"project-test","folio-pdf-t31-evidence-recorder",release,release)
                +"Independent standards certification and compatible-status Canvas/Workflow Dependency Gates remain open. Barcode decoding, syntax and visual agreement do not grant compatible status or Foundation release certification. Capability status remains experimental.\n");
        System.out.println("T31: syntax="+syntax+", semantic="+semantic+", visual="+visual+", path-decoded="+decoded+", raster-decoded="+rasterDecoded
                +", negative PDFs="+rejected+", negative rasters="+rasterRejected+"; standards and compatibility remain indeterminate.");
        if (syntax==EvidenceResult.FAIL || semantic==EvidenceResult.FAIL || visual==EvidenceResult.FAIL) { throw new IllegalStateException("T31 acceptance has a failing evidence chain; retained findings identify it"); }
    }
    private static String rasterLinks(String stem,Path artifacts) {
        if (!Files.exists(artifacts.resolve(stem+"-actual-1.png"))) { return "Raster files unavailable; tool findings record the missing evidence.\n\n"; }
        StringBuilder links=new StringBuilder(); int page=0;
        for (T31BarcodeProfile.Fixture fixture : T31BarcodeProfile.fixtures()) {
            links.append("- Page ").append(++page).append(' ').append(fixture.id).append(':');
            for (String kind : new String[] {"actual","reference","difference"}) {
                String file=stem+"-"+kind+"-"+page+".png";
                if (Files.exists(artifacts.resolve(file))) { links.append(" [").append(kind).append("](artifacts/").append(file).append(')'); }
                else { links.append(' ').append(kind).append(" unavailable"); }
            }
            links.append('\n');
        }
        return links.append('\n').toString();
    }
    private static String record(String chain,EvidenceResult result,String kind,String producer,String version,String release) {
        return metadata("Capability",CAPABILITY)+metadata("Acceptance Profile",PROFILE)+metadata("Profile record","capabilities/evidence/"+PROFILE+".md")
                +metadata("Release train",release)+metadata("Chain",chain)+metadata("Result",result.recordValue())+metadata("Producer kind",kind)+metadata("Producer",producer)+metadata("Producer version",version);
    }
    private static EvidenceResult combine(EvidenceResult current,EvidenceResult next) {
        if (current==EvidenceResult.FAIL || next==EvidenceResult.FAIL) { return EvidenceResult.FAIL; }
        return current==EvidenceResult.INDETERMINATE || next==EvidenceResult.INDETERMINATE ? EvidenceResult.INDETERMINATE : EvidenceResult.PASS;
    }
    private static void requireFresh(Path directory) throws IOException {
        if (!Files.exists(directory)) { return; }
        try (DirectoryStream<Path> files=Files.newDirectoryStream(directory,"T31*")) {
            if (files.iterator().hasNext()) { throw new IOException("Existing T31 evidence must be preserved; select a fresh directory"); }
        }
    }
    private static String sources(Path root,Path profile) throws IOException {
        List<Path> paths=new ArrayList<Path>(); paths.add(profile); paths.add(root.resolve("pom.xml"));
        for (String module : new String[] {"pdf-document","pdf-acceptance"}) {
            paths.add(root.resolve(module+"/pom.xml"));
            try (Stream<Path> files=Files.walk(root.resolve(module+"/src"))) {
                files.filter(Files::isRegularFile).forEach(paths::add);
            }
        }
        Collections.sort(paths); StringBuilder result=new StringBuilder();
        for (Path path : paths) { result.append(EvidenceFiles.sha256(path)).append("  ").append(root.relativize(path).toString().replace('\\','/')).append('\n'); }
        return result.toString();
    }
}
