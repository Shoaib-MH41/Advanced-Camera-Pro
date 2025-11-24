// 📁 libraries/ui-components/src/main/java/com/advancedcamera/ui/CameraUIController.java

package com.advancedcamera.ui;

import android.animation.*;
import android.view.*;
import android.widget.*;

public class CameraUIController {
    
    public static void setupProControls(View rootView) {
        // Circular Capture Button with ripple effect
        setupCaptureButton(rootView);
        
        // Gesture controls for zoom and focus
        setupGestureControls(rootView);
        
        // Animated mode switcher
        setupModeSwitcher(rootView);
        
        // Professional histogram display
        setupLiveHistogram(rootView);
    }
    
    private static void setupCaptureButton(View rootView) {
        ImageButton captureBtn = rootView.findViewById(R.id.btn_capture);
        
        // Press animation
        captureBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateButtonPress(v, true);
                    break;
                case MotionEvent.ACTION_UP:
                    animateButtonPress(v, false);
                    break;
            }
            return false;
        });
    }
    
    private static void animateButtonPress(View view, boolean pressed) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", pressed ? 0.9f : 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", pressed ? 0.9f : 1.0f);
        
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(150);
        set.start();
    }
}
