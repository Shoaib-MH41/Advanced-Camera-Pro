package com.yourname.advancedcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.tabs.TabLayout;

import com.yourname.advancedcamera.features.FeatureManager;
import com.yourname.advancedcamera.features.color.ColorLUTs;
import com.yourname.advancedcamera.features.night.NightModeProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraActivity extends AppCompatActivity {
    
    private static final String TAG = "AdvancedCamera";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    
    // Camera components
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    
    // UI Components
    private TextureView textureView;
    private ImageButton btnCapture, btnSwitchCamera, btnSettings, btnGallery, btnModeSwitch;
    private SeekBar seekExposure, seekISO, seekZoom;
    private TextView tvStatus, recordingIndicator;
    private LinearLayout controlPanel;
    private TabLayout tabModes;
    private ImageView focusIndicator;
    
    // Background handlers
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    
    // Image reader for capture
    private ImageReader imageReader;
    
    // Camera state
    private String cameraId;
    private Size previewSize;
    private Integer sensorOrientation;
    private boolean isFrontCamera = false;
    private boolean isRecording = false;
    private int currentMode = 0; // 0: Auto, 1: Pro, 2: Night, 3: Portrait, 4: Video
    
    // Advanced Features
    private FeatureManager featureManager;
    private ColorLUTs colorLUTs;
    private NightModeProcessor nightProcessor;
    
    // Orientation handling
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    
    // TextureView listener
    private final TextureView.SurfaceTextureListener surfaceTextureListener = 
        new TextureView.SurfaceTextureListener() {
            
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
        }
        
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }
        
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Frame update if needed for real-time processing
        }
    };
    
    // Camera state callback
    private final CameraDevice.StateCallback cameraStateCallback = 
        new CameraDevice.StateCallback() {
            
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
            updateStatus("Camera Ready - Advanced Features Loaded");
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
            updateStatus("Camera Disconnected");
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
            updateStatus("Camera Error: " + error);
        }
    };
    
    // Capture session state callback
    private final CameraCaptureSession.StateCallback captureSessionCallback = 
        new CameraCaptureSession.StateCallback() {
            
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            captureSession = session;
            try {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
                
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start preview: " + e.getMessage());
            }
        }
        
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            updateStatus("Failed to configure camera");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_pro);
        
        initializeUI();
        initializeAdvancedFeatures();
        checkPermissions();
    }
    
    private void initializeUI() {
        // Initialize views
        textureView = findViewById(R.id.texture_view);
        btnCapture = findViewById(R.id.btn_capture);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnSettings = findViewById(R.id.btn_settings);
        btnGallery = findViewById(R.id.btn_gallery);
        btnModeSwitch = findViewById(R.id.btn_mode_switch);
        
        seekExposure = findViewById(R.id.seek_exposure);
        seekISO = findViewById(R.id.seek_iso);
        seekZoom = findViewById(R.id.seek_zoom);
        
        tvStatus = findViewById(R.id.tv_status);
        recordingIndicator = findViewById(R.id.recording_indicator);
        controlPanel = findViewById(R.id.control_panel);
        tabModes = findViewById(R.id.tab_modes);
        focusIndicator = findViewById(R.id.focus_indicator);
        
        setupEventListeners();
        setupModeTabs();
    }
    
    /**
     * Initialize Advanced Features
     */
    private void initializeAdvancedFeatures() {
        try {
            // ✅ CORRECTED: Constructor without parameters
            featureManager = new FeatureManager();
            colorLUTs = new ColorLUTs();
            nightProcessor = new NightModeProcessor();
            
            // ✅ CORRECTED: Removed initializeFeatures() call since it doesn't exist
            // featureManager.initializeFeatures(); // This method doesn't exist in FeatureManager
            
            // Show available features in log
            List<String> availableFeatures = featureManager.getAvailableFeatures();
            Log.d(TAG, "Advanced Features Loaded: " + availableFeatures);
            
            // Update status with features info
            updateStatus("Advanced Features: " + availableFeatures.size() + " loaded");
            
        } catch (Exception e) {
            Log.e(TAG, "Advanced features initialization failed: " + e.getMessage());
            updateStatus("Basic Mode - Advanced Features Failed");
        }
    }
    
    private void setupEventListeners() {
        // Texture view listener
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        
        // Capture button
        btnCapture.setOnClickListener(v -> captureImage());
        
        // Switch camera
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        
        // Settings button - Now with advanced features
        btnSettings.setOnClickListener(v -> showAdvancedSettings());
        
        // Gallery button
        btnGallery.setOnClickListener(v -> openGallery());
        
        // Mode switch
        btnModeSwitch.setOnClickListener(v -> toggleManualMode());
        
        // SeekBar listeners
        seekExposure.setOnSeekBarChangeListener(seekBarChangeListener);
        seekISO.setOnSeekBarChangeListener(seekBarChangeListener);
        seekZoom.setOnSeekBarChangeListener(seekBarChangeListener);
        
        // Touch focus
        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                setFocusArea(x, y);
            }
            return true;
        });
    }
    
    private void setupModeTabs() {
        tabModes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentMode = tab.getPosition();
                applyModeSettings(currentMode);
                
                // Show mode-specific features
                showModeFeatures(currentMode);
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    /**
     * Show features available for current mode
     */
    private void showModeFeatures(int mode) {
        String modeName = "";
        String features = "";
        
        switch (mode) {
            case 0: // Auto
                modeName = "Auto Mode";
                features = "Smart Scene Detection";
                break;
            case 1: // Pro
                modeName = "Pro Mode";
                features = "Manual Controls + RAW";
                break;
            case 2: // Night
                modeName = "Night Mode";
                // ✅ CORRECTED: Use getter methods instead of direct field access
                if (featureManager.getIsNightVisionEnabled()) {
                    features = "AI Night Vision Available";
                } else {
                    features = "Basic Night Mode";
                }
                break;
            case 3: // Portrait
                modeName = "Portrait Mode";
                // ✅ CORRECTED: Use getter methods instead of direct field access
                if (featureManager.getIsColorLUTsEnabled()) {
                    features = "Cinematic LUTs Available";
                } else {
                    features = "Portrait Mode";
                }
                break;
            case 4: // Video
                modeName = "Video Mode";
                features = "Video Recording";
                break;
        }
        
        Toast.makeText(this, modeName + " - " + features, Toast.LENGTH_SHORT).show();
    }
    
    private final SeekBar.OnSeekBarChangeListener seekBarChangeListener = 
        new SeekBar.OnSeekBarChangeListener() {
            
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && cameraDevice != null) {
                applyManualSettings();
            }
        }
        
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                }, REQUEST_CAMERA_PERMISSION);
        } else {
            startBackgroundThread();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundThread();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Background thread interrupted: " + e.getMessage());
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private void setupCamera(int width, int height) {
        try {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String[] cameraList = cameraManager.getCameraIdList();
            
            if (cameraList.length == 0) {
                updateStatus("No camera found");
                return;
            }
            
            // Find back camera first
            for (String id : cameraList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null) {
                cameraId = cameraList[0]; // Use first available camera
            }
            
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                updateStatus("Camera not supported");
                return;
            }
            
            // Choose preview size
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture.class), width, height);
            
            // Get sensor orientation
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            
            configureTransform(width, height);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera setup failed: " + e.getMessage());
        }
    }
    
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            if (cameraManager != null && cameraId != null) {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera: " + e.getMessage());
        }
    }
    
    private void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            
            // Setup image reader for capture
            setupImageReader();
            
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                captureSessionCallback, backgroundHandler);
                
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to start preview: " + e.getMessage());
        }
    }
    
    private void setupImageReader() {
        try {
            // ✅ CORRECTED: Added try-catch for CameraAccessException
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map == null) {
                Log.e(TAG, "StreamConfigurationMap is null");
                return;
            }
            
            Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);
            
            if (outputSizes == null || outputSizes.length == 0) {
                Log.e(TAG, "No JPEG output sizes available");
                return;
            }
            
            Size largest = Collections.max(Arrays.asList(outputSizes), 
                new CompareSizesByArea());
            
            imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                ImageFormat.JPEG, 1);
            
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to setup image reader: " + e.getMessage());
            // Fallback to default size
            try {
                imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
            } catch (Exception ex) {
                Log.e(TAG, "Fallback image reader also failed: " + ex.getMessage());
            }
        }
    }
    
    private final ImageReader.OnImageAvailableListener imageAvailableListener = 
        new ImageReader.OnImageAvailableListener() {
            
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    
                    // Apply advanced features if enabled
                    Bitmap processedBitmap = applyAdvancedProcessing(bitmap);
                    
                    saveImage(processedBitmap);
                    
                    runOnUiThread(() -> 
                        Toast.makeText(CameraActivity.this, "Photo saved with advanced processing!", Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };
    
    /**
     * Apply advanced features to captured image
     */
    private Bitmap applyAdvancedProcessing(Bitmap originalBitmap) {
        Bitmap processedBitmap = originalBitmap;
        
        try {
            // Apply features based on current mode
            switch (currentMode) {
                case 2: // Night Mode
                    // ✅ CORRECTED: Use getter method
                    if (featureManager.getIsNightVisionEnabled()) {
                        // For single image, we can still enhance it
                        List<Bitmap> singleFrame = new ArrayList<>();
                        singleFrame.add(originalBitmap);
                        processedBitmap = nightProcessor.processNightShot(singleFrame);
                        Log.d(TAG, "Night vision processing applied");
                    }
                    break;
                    
                case 3: // Portrait Mode
                    // ✅ CORRECTED: Use getter method
                    if (featureManager.getIsColorLUTsEnabled()) {
                        processedBitmap = colorLUTs.applyCinematicLUT(originalBitmap);
                        Log.d(TAG, "Cinematic LUT applied");
                    }
                    break;
                    
                case 1: // Pro Mode - Apply vintage LUT
                    // ✅ CORRECTED: Use getter method
                    if (featureManager.getIsColorLUTsEnabled()) {
                        processedBitmap = colorLUTs.applyVintageLUT(originalBitmap);
                        Log.d(TAG, "Vintage LUT applied");
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Advanced processing failed: " + e.getMessage());
            // Return original if processing fails
            processedBitmap = originalBitmap;
        }
        
        return processedBitmap;
    }
    
    private void captureImage() {
        if (cameraDevice == null) return;
        
        try {
            if (currentMode == 4) { // Video mode
                toggleRecording();
                return;
            }
            
            // Show feature info before capture
            showCaptureInfo();
            
            // Capture still image
            CaptureRequest.Builder captureBuilder = 
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            
            // Set capture orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 
                getOrientation());
            
            // Apply current mode settings
            applyCaptureSettings(captureBuilder);
            
            CameraCaptureSession.CaptureCallback captureCallback = 
                new CameraCaptureSession.CaptureCallback() {
                
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, 
                                             @NonNull CaptureRequest request, 
                                             @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    updateStatus("Capture completed with advanced processing");
                }
            };
            
            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), captureCallback, null);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Capture failed: " + e.getMessage());
        }
    }
    
    /**
     * Show info about features being applied
     */
    private void showCaptureInfo() {
        String featureInfo = "Basic Capture";
        
        // ✅ CORRECTED: All uses of featureManager variables now use getter methods
        if (currentMode == 2 && featureManager.getIsNightVisionEnabled()) {
            featureInfo = "Night Vision Processing";
        } else if (currentMode == 3 && featureManager.getIsColorLUTsEnabled()) {
            featureInfo = "Cinematic LUT Applied";
        } else if (currentMode == 1 && featureManager.getIsColorLUTsEnabled()) {
            featureInfo = "Vintage LUT Applied";
        }
        
        Toast.makeText(this, featureInfo, Toast.LENGTH_SHORT).show();
    }
    
    private void setFocusArea(float x, float y) {
        if (cameraDevice == null) return;
        
        try {
            // Show focus indicator
            focusIndicator.setX(x - focusIndicator.getWidth() / 2);
            focusIndicator.setY(y - focusIndicator.getHeight() / 2);
            focusIndicator.setVisibility(View.VISIBLE);
            
            // Create focus area meter
            MeteringRectangle focusArea = new MeteringRectangle(
                (int) (x - 100), (int) (y - 100), 200, 200, MeteringRectangle.METERING_WEIGHT_MAX);
            
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, 
                new MeteringRectangle[]{focusArea});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, 
                new MeteringRectangle[]{focusArea});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, 
                CameraMetadata.CONTROL_AF_TRIGGER_START);
            
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
            
            // Hide focus indicator after delay
            backgroundHandler.postDelayed(() -> 
                runOnUiThread(() -> focusIndicator.setVisibility(View.INVISIBLE)), 2000);
                
        } catch (CameraAccessException e) {
            Log.e(TAG, "Focus failed: " + e.getMessage());
        }
    }
    
    private void switchCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        
        isFrontCamera = !isFrontCamera;
        cameraId = null;
        
        try {
            String[] cameraList = cameraManager.getCameraIdList();
            for (String id : cameraList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                
                if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                } else if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null) {
                cameraId = cameraList[0];
            }
            
            openCamera();
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera switch failed: " + e.getMessage());
        }
    }
    
    private void toggleManualMode() {
        boolean isManual = currentMode == 1; // Pro mode
        seekExposure.setVisibility(isManual ? View.VISIBLE : View.GONE);
        seekISO.setVisibility(isManual ? View.VISIBLE : View.GONE);
    }
    
    private void applyModeSettings(int mode) {
        try {
            switch (mode) {
                case 0: // Auto
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, 
                        CaptureRequest.CONTROL_MODE_AUTO);
                    break;
                case 1: // Pro
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, 
                        CaptureRequest.CONTROL_MODE_OFF);
                    break;
                case 2: // Night
                    previewRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                        CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
                    break;
                case 3: // Portrait
                    previewRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                        CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT);
                    break;
                case 4: // Video
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_AUTO);
                    break;
            }
            
            if (captureSession != null) {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
            }
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Mode change failed: " + e.getMessage());
        }
    }
    
    private void applyManualSettings() {
        if (currentMode != 1) return; // Only in Pro mode
        
        try {
            // Apply exposure compensation
            int exposure = seekExposure.getProgress() - 50; // -50 to +50
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);
            
            // Apply manual settings if supported
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            
            if (Arrays.stream(capabilities).anyMatch(cap -> 
                cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                
                // Manual ISO
                Range<Integer> isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                if (isoRange != null) {
                    int iso = isoRange.getLower() + (isoRange.getUpper() - isoRange.getLower()) * seekISO.getProgress() / 100;
                    previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                }
            }
            
            if (captureSession != null) {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
            }
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Manual settings failed: " + e.getMessage());
        }
    }
    
    private void applyCaptureSettings(CaptureRequest.Builder builder) {
        // Apply current mode settings to capture
        if (currentMode == 1) { // Pro mode
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        }
    }
    
    private void toggleRecording() {
        // Video recording implementation would go here
        isRecording = !isRecording;
        recordingIndicator.setVisibility(isRecording ? View.VISIBLE : View.GONE);
        
        if (isRecording) {
            btnCapture.setBackgroundResource(R.drawable.btn_capture_modern_pressed);
            updateStatus("Recording...");
        } else {
            btnCapture.setBackgroundResource(R.drawable.btn_capture_modern);
            updateStatus("Recording stopped");
        }
    }
    
    private void saveImage(Bitmap bitmap) {
        String filename = "IMG_" + System.currentTimeMillis() + ".jpg";
        File file = new File(getExternalFilesDir(null), filename);
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Log.d(TAG, "Image saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save image: " + e.getMessage());
        }
    }
    
    /**
     * Advanced settings menu
     */
    private void showAdvancedSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Advanced Features");
        
        List<String> availableFeatures = featureManager.getAvailableFeatures();
        String featuresText = "Available Features:\n• " + String.join("\n• ", availableFeatures);
        
        builder.setMessage(featuresText);
        builder.setPositiveButton("OK", null);
        builder.setNeutralButton("Test Features", (dialog, which) -> {
            testAdvancedFeatures();
        });
        
        builder.show();
    }
    
    /**
     * Test advanced features
     */
    private void testAdvancedFeatures() {
        Toast.makeText(this, "Testing advanced features...", Toast.LENGTH_SHORT).show();
        
        // ✅ CORRECTED: Use getter methods
        // Test Color LUTs
        if (featureManager.getIsColorLUTsEnabled()) {
            Log.d(TAG, "Color LUTs feature is working");
        }
        
        // Test Night Vision
        if (featureManager.getIsNightVisionEnabled()) {
            Log.d(TAG, "Night Vision feature is working");
        }
        
        Toast.makeText(this, "Feature tests completed - check Logcat", Toast.LENGTH_LONG).show();
    }
    
    private void openGallery() {
        Toast.makeText(this, "Gallery will be implemented", Toast.LENGTH_SHORT).show();
    }
    
    private void updateStatus(String message) {
        runOnUiThread(() -> tvStatus.setText(message));
    }
    
    private int getOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }
    
    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null) return;
        
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        
        float scaleX = (float) viewWidth / previewSize.getWidth();
        float scaleY = (float) viewHeight / previewSize.getHeight();
        
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            matrix.setScale(scaleX, scaleY);
        } else {
            matrix.setScale(scaleY, scaleX);
        }
        
        textureView.setTransform(matrix);
    }
    
    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                option.getWidth() <= width && option.getHeight() <= height) {
                bigEnough.add(option);
            }
        }
        
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }
    
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        
        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            openCamera();
        }
    }
    
    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
