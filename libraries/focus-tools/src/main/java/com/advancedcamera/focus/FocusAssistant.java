// 📁 libraries/focus-tools/src/main/java/com/advancedcamera/focus/FocusAssistant.java

package com.advancedcamera.focus;

public class FocusAssistant {
    
    // Focus Peaking - highlights in-focus areas
    public static Bitmap applyFocusPeaking(Bitmap frame, int threshold) {
        Bitmap result = frame.copy(frame.getConfig(), true);
        Mat mat = new Mat();
        Utils.bitmapToMat(frame, mat);
        
        // Edge detection for focus areas
        Mat edges = new Mat();
        Imgproc.Canny(mat, edges, threshold, threshold * 3);
        
        // Highlight edges in bright color
        Mat resultMat = new Mat();
        Utils.bitmapToMat(result, resultMat);
        resultMat.setTo(new Scalar(0, 255, 255), edges); // Cyan highlights
        
        Utils.matToBitmap(resultMat, result);
        return result;
    }
    
    // Focus Stacking for macro photography
    public static Bitmap focusStacking(List<Bitmap> frames) {
        List<Mat> focusedRegions = new ArrayList<>();
        
        for (Bitmap frame : frames) {
            Mat mat = new Mat();
            Utils.bitmapToMat(frame, mat);
            
            // Find sharpest regions
            Mat laplacian = new Mat();
            Imgproc.Laplacian(mat, laplacian, CvType.CV_64F);
            
            Core.mean(laplacian).val[0]; // Sharpness metric
            focusedRegions.add(extractSharpestRegion(mat, laplacian));
        }
        
        return blendFocusStack(focusedRegions);
    }
}
