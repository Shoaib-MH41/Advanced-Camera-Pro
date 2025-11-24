// 📁 libraries/camera-core/src/main/java/com/advancedcamera/core/AdvancedCameraController.java

package com.advancedcamera.core;

import android.hardware.camera2.*;
import android.util.Range;
import android.util.Size;

public class AdvancedCameraController {
    
    public static class CameraFeatures {
        public boolean supportsRAW = false;
        public boolean supportsManualExposure = false;
        public boolean supportsManualFocus = false;
        public Range<Long> exposureRange;
        public Range<Integer> isoRange;
        public Size[] outputSizes;
    }
    
    public static CameraFeatures detectFeatures(CameraCharacteristics characteristics) {
        CameraFeatures features = new CameraFeatures();
        
        // Detect RAW support
        int[] capabilities = characteristics.get(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        );
        features.supportsRAW = Arrays.stream(capabilities)
            .anyMatch(cap -> cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW);
        
        // Detect manual controls
        features.supportsManualExposure = Arrays.stream(capabilities)
            .anyMatch(cap -> cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        
        // Get exposure ranges
        features.exposureRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        );
        features.isoRange = characteristics.get(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        );
        
        return features;
    }
    
    public static CaptureRequest.Builder setupProMode(
            CameraDevice device, 
            CameraFeatures features,
            Surface previewSurface) throws CameraAccessException {
        
        CaptureRequest.Builder builder = device.createCaptureRequest(
            CameraDevice.TEMPLATE_MANUAL
        );
        
        builder.addTarget(previewSurface);
        
        // Enable manual controls if supported
        if (features.supportsManualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        }
        
        return builder;
    }
}
