// 📁 libraries/analytics/src/main/java/com/advancedcamera/analytics/ImageAnalytics.java

package com.advancedcamera.analytics;

public class ImageAnalytics {
    
    public static class HistogramData {
        public int[] luminance;
        public int[] redChannel;
        public int[] greenChannel; 
        public int[] blueChannel;
        public boolean isOverExposed;
        public boolean isUnderExposed;
    }
    
    public static HistogramData calculateLiveHistogram(Bitmap frame) {
        HistogramData data = new HistogramData();
        data.luminance = new int[256];
        data.redChannel = new int[256];
        data.greenChannel = new int[256];
        data.blueChannel = new int[256];
        
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] pixels = new int[width * height];
        frame.getPixels(pixels, 0, width, 0, 0, width, height);
        
        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            // Calculate luminance
            int lum = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            data.luminance[lum]++;
            data.redChannel[r]++;
            data.greenChannel[g]++;
            data.blueChannel[b]++;
        }
        
        // Exposure warnings
        data.isOverExposed = data.luminance[255] > (pixels.length * 0.05);
        data.isUnderExposed = data.luminance[0] > (pixels.length * 0.05);
        
        return data;
    }
    
    public static void drawWaveformMonitor(Canvas canvas, HistogramData data) {
        // Professional waveform display like DaVinci Resolve
        drawWaveform(canvas, data.luminance, Color.WHITE);
        drawVectorScope(canvas, data.redChannel, data.greenChannel, data.blueChannel);
    }
}
