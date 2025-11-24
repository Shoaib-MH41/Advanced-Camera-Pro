// 📁 libraries/creative/src/main/java/com/advancedcamera/creative/LongExposure.java

package com.advancedcamera.creative;

public class LongExposure {
    
    public static Bitmap createLightTrails(List<Bitmap> frames) {
        // Blend frames for light trail effect
        Bitmap result = frames.get(0).copy(Bitmap.Config.ARGB_8888, true);
        
        for (int i = 1; i < frames.size(); i++) {
            result = lightenBlend(result, frames.get(i));
        }
        
        return enhanceTrails(result);
    }
    
    public static Bitmap createSmoothWater(List<Bitmap> frames, int duration) {
        // Average blending for silky water effect
        return temporalAveraging(frames, duration);
    }
    
    public static Bitmap createStarTrails(List<Bitmap> frames) {
        // Specialized star trail creation
        return starTrailCompositing(frames);
    }
}
