// 📁 libraries/astrophotography/src/main/java/com/advancedcamera/astro/AstroMode.java

package com.advancedcamera.astro;

public class AstroMode {
    
    public static Bitmap captureStarTrail(List<Bitmap> frames, int durationMinutes) {
        // Star alignment and stacking
        Bitmap alignedStars = alignStars(frames);
        
        // Noise reduction for dark skies
        Bitmap denoised = astroNoiseReduction(alignedStars);
        
        // Star color enhancement
        Bitmap enhanced = enhanceStarColors(denoised);
        
        // Milky Way detection and enhancement
        return enhanceMilkyWay(enhanced);
    }
    
    public static void setupAstroCapture(CaptureRequest.Builder builder) {
        // 30-second exposure for stars
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 30000000000L);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, 3200);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f); // Infinity focus
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        
        // Enable noise reduction
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, 
                   CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
    }
}
