package net.zerocloud.pdf.acceptance;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import com.google.zxing.common.BitMatrix;
import static net.zerocloud.pdf.acceptance.T31BarcodeAssertions.require;

/** Actual raster sampling uses declared geometry, never PDF paths, metadata or a reference matrix. */
final class T31RasterDecoder {
    private T31RasterDecoder() { }
    static String decode(BufferedImage raster,T31BarcodeProfile.Fixture fixture) throws Exception {
        require(raster!=null && raster.getWidth()==2448 && raster.getHeight()==3168,"T31 raster size");
        AffineTransform placement=T31BarcodeAssertions.transform(fixture);
        double quiet=fixture.barcode.getQuietZone(),unitX=fixture.barcode.getModuleWidth(),unitY=fixture.barcode.getModuleHeight();
        BitMatrix matrix=new BitMatrix(fixture.width,fixture.height);
        for (int y=0;y<fixture.height;y++) { for (int x=0;x<fixture.width;x++) {
            Point2D point=placement.transform(new Point2D.Double(quiet+(x+0.5)*unitX,quiet+(fixture.height-y-0.5)*unitY),null);
            int rgb=pixel(raster,(int)Math.floor(point.getX()*4),raster.getHeight()-1-(int)Math.floor(point.getY()*4));
            if (dark(rgb)) { matrix.set(x,y); }
        } }
        String decoded=T31MatrixOracle.inspect(matrix,fixture);
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for (double x : new double[] {0,fixture.widthPoints()}) { for (double y : new double[] {0,fixture.heightPoints()}) {
            Point2D point=placement.transform(new Point2D.Double(x,y),null);
            minX=Math.min(minX,point.getX()); maxX=Math.max(maxX,point.getX());
            minY=Math.min(minY,point.getY()); maxY=Math.max(maxY,point.getY());
        } }
        AffineTransform inverse=placement.createInverse(); double[] color=fixture.barcode.getForegroundRgb(); int[] channels=new int[3];
        for (int index=0;index<3;index++) { channels[index]=(int)Math.round(color[index]*255); }
        Point2D.Double world=new Point2D.Double(),local=new Point2D.Double(); long checked=0;
        for (int py=(int)Math.floor(minY*4);py<(int)Math.ceil(maxY*4);py++) {
            for (int px=(int)Math.floor(minX*4);px<(int)Math.ceil(maxX*4);px++) {
                world.setLocation((px+0.5)/4,(py+0.5)/4); inverse.transform(world,local);
                double x=local.getX(),y=local.getY();
                if (x<0 || y<0 || x>=fixture.widthPoints() || y>=fixture.heightPoints()) { continue; }
                int rgb=pixel(raster,px,raster.getHeight()-1-py);
                boolean margin=x<quiet || y<quiet || x>=fixture.widthPoints()-quiet || y>=fixture.heightPoints()-quiet;
                boolean expectedDark=!margin && matrix.get((int)((x-quiet)/unitX),fixture.height-1-(int)((y-quiet)/unitY));
                for (int channel=0;channel<3;channel++) {
                    int expected=expectedDark ? channels[channel] : 255,actual=(rgb>>((2-channel)*8))&255;
                    require(Math.abs(expected-actual)<=1,(margin ? "Paint in raster quiet zone" : "Raster module interior/edge/color mismatch")+" at "+px+","+py);
                }
                checked++;
            }
        }
        return decoded+"; raster module/quiet/color pixels="+checked+"; declared inverse placement=pass";
    }
    private static int pixel(BufferedImage image,int x,int y) {
        require(x>=0 && y>=0 && x<image.getWidth() && y<image.getHeight(),"Symbol outside raster"); return image.getRGB(x,y);
    }
    private static boolean dark(int rgb) {
        return (((rgb>>16)&255)*299+((rgb>>8)&255)*587+(rgb&255)*114)<128000;
    }
}
