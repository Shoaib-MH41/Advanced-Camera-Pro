// 📁 libraries/gestures/src/main/java/com/advancedcamera/gestures/CameraGestures.java

package com.advancedcamera.gestures;

public class CameraGestures {
    
    public static class GestureController implements View.OnTouchListener {
        private float initialFingerSpacing = 0;
        
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    initialFingerSpacing = getFingerSpacing(event);
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2) {
                        // Pinch to zoom
                        float newFingerSpacing = getFingerSpacing(event);
                        if (newFingerSpacing > initialFingerSpacing) {
                            zoomIn(); // Smooth zoom in
                        } else {
                            zoomOut(); // Smooth zoom out
                        }
                        initialFingerSpacing = newFingerSpacing;
                    } else {
                        // Single finger - focus and exposure
                        setFocusAndExposure(event.getX(), event.getY());
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                    resetFocusIndicator();
                    break;
            }
            return true;
        }
        
        private float getFingerSpacing(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(x * x + y * y);
        }
    }
}
