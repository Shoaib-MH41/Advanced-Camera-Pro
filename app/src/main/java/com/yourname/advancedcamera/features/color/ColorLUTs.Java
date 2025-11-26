package com.yourname.advancedcamera.features.color;

import android.graphics.*;
import android.util.Log;

public class ColorLUTs {
    private static final String TAG = "ColorLUTs";

    public Bitmap applyCinematicLUT(Bitmap bitmap) {
        Log.d(TAG, "Applying Cinematic LUT");
        return applyColorFilter(bitmap, createCinematicFilter());
    }

    public Bitmap applyVintageLUT(Bitmap bitmap) {
        Log.d(TAG, "Applying Vintage LUT");
        return applyColorFilter(bitmap, createVintageFilter());
    }

    public Bitmap applyModernLUT(Bitmap bitmap) {
        Log.d(TAG, "Applying Modern LUT");
        return applyColorFilter(bitmap, createModernFilter());
    }

    public Bitmap applyNetflixLUT(Bitmap bitmap) {
        Log.d(TAG, "Applying Netflix Style LUT");
        return applyColorFilter(bitmap, createNetflixFilter());
    }

    private Bitmap applyColorFilter(Bitmap bitmap, ColorFilter filter) {
        try {
            Bitmap result = bitmap.copy(bitmap.getConfig(), true);
            Canvas canvas = new Canvas(result);
            Paint paint = new Paint();
            paint.setColorFilter(filter);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Color filter failed: " + e.getMessage());
            return bitmap;
        }
    }

    private ColorFilter createCinematicFilter() {
        float[] matrix = {
            1.1f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.9f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.2f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        return new ColorMatrixColorFilter(new ColorMatrix(matrix));
    }

    private ColorFilter createVintageFilter() {
        float[] matrix = {
            1.0f, 0.1f, 0.1f, 0.0f, 20.0f,
            0.0f, 0.9f, 0.1f, 0.0f, 10.0f,
            0.1f, 0.1f, 0.8f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        return new ColorMatrixColorFilter(new ColorMatrix(matrix));
    }

    private ColorFilter createModernFilter() {
        float[] matrix = {
            1.2f, 0.0f, 0.0f, 0.0f, -10.0f,
            0.0f, 1.1f, 0.0f, 0.0f, -5.0f,
            0.0f, 0.0f, 1.3f, 0.0f, -15.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        return new ColorMatrixColorFilter(new ColorMatrix(matrix));
    }

    private ColorFilter createNetflixFilter() {
        float[] matrix = {
            1.3f, -0.1f, 0.1f, 0.0f, -20.0f,
            -0.1f, 1.1f, 0.0f, 0.0f, -10.0f,
            0.1f, -0.1f, 1.4f, 0.0f, -25.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        return new ColorMatrixColorFilter(new ColorMatrix(matrix));
    }
}
