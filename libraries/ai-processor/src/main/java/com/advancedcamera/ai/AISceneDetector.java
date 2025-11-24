// 📁 libraries/ai-processor/src/main/java/com/advancedcamera/ai/AISceneDetector.java

package com.advancedcamera.ai;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.classifier.*;

public class AISceneDetector {
    
    public enum SceneType {
        PORTRAIT, LANDSCAPE, NIGHT, FOOD, TEXT, 
        SUNSET, PET, FLOWER, DOCUMENT, MACRO
    }
    
    public static SceneType detectScene(Bitmap image) {
        try {
            ImageClassifier classifier = ImageClassifier.createFromFileAndOptions(
                context, "scene_model.tflite",
                ImageClassifier.ImageClassifierOptions.builder()
                    .setMaxResults(1)
                    .build()
            );
            
            List<Classifications> results = classifier.classify(TensorImage.fromBitmap(image));
            return mapToSceneType(results.get(0).getCategories().get(0).getLabel());
        } catch (Exception e) {
            return SceneType.LANDSCAPE; // default
        }
    }
    
    public static void applyOptimalSettings(SceneType scene, CaptureRequest.Builder builder) {
        switch (scene) {
            case PORTRAIT:
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, 
                           CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT);
                builder.set(CaptureRequest.CONTROL_AE_MODE, 
                           CameraMetadata.CONTROL_AE_MODE_ON);
                break;
            case NIGHT:
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, 
                           CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 1600);
                break;
            case FOOD:
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, 
                           CameraMetadata.CONTROL_SCENE_MODE_FOOD);
                enhanceColorsForFood(builder);
                break;
        }
    }
}
