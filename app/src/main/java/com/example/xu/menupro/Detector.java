package com.example.xu.menupro;

/**
 * Created by xu on 02/03/18.
 */

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Created by xu on 02/03/18.
 */

public class Detector {

    private TensorFlowInferenceInterface inferenceInterface;
    private static final String MODEL_FILE = "file:///android_asset/optimized_expanded_convs.pb";
    private static final String INPUT_NODE = "input_images";
    private static final String OUTPUT_NODE_GEOMETRY = "feature_fusion/concat_3";
    private static final String OUTPUT_NODE_SCORE = "feature_fusion/Conv_1/Sigmoid";
    private static final String[] OUTPUT_NAMES = {OUTPUT_NODE_SCORE, OUTPUT_NODE_GEOMETRY};
    AssetManager assetManager;

    private float[] floatValues;

    private float[] geometry;
    private float[] score;
    private ArrayList<Float> filteredScores = new ArrayList<Float>();
    private FloatBuffer floatBufferGeo;
    private FloatBuffer floatBufferScore;
    private int inputWidth;
    private int inputHeight;

    private double score_map_thresh=0.8;
    private double box_thresh=0.1;

    private boolean runStats = false;


    private Detector() {
    }

    public static Detector create(AssetManager assetManager, int inputWidth, int inputHeight) {
        Detector d = new Detector();
        d.assetManager = assetManager;
        d.inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);
        d.floatValues = new float[inputWidth * inputHeight * 3];
        d.floatBufferGeo = FloatBuffer.allocate(1 * inputHeight * inputWidth /16 * 5);
        d.floatBufferScore = FloatBuffer.allocate(1 * inputHeight * inputWidth /16);
        d.inputWidth = inputWidth;
        d.inputHeight = inputHeight;
        return d;
    }

    public float[] detectIamge(Bitmap bitmap, float[] ratio) {
        int[] intValues = new int[inputHeight * inputWidth];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for(int i=0; i < intValues.length; i++)
        {
            int val = intValues[i];
            float R = (float) ((val >> 16) & 0xff);;
            float G = (float) ((val >>  8) & 0xff);
            float B = (float) ((val      ) & 0xff);
            floatValues[i * 3 + 0] = R;
            floatValues[i * 3 + 1] = G;
            floatValues[i * 3 + 2] = B;
            // Here we convert our bitmap into a 1d array.
            // It is important to understand the 3d to 1d flatten process and consider about the
            // order of the elements in the new 1d aray,

        }


        // feed the input image to the neural network.
        inferenceInterface.feed(INPUT_NODE, floatValues, new long[] {1, bitmap.getHeight(), bitmap.getWidth(), 3});
        // The input is of the shape[1, height, width, 3]
        inferenceInterface.run(OUTPUT_NAMES, runStats);
        inferenceInterface.fetch(OUTPUT_NODE_GEOMETRY, floatBufferGeo);
        inferenceInterface.fetch(OUTPUT_NODE_SCORE, floatBufferScore);
        geometry = floatBufferGeo.array();
        /*
        geometry should be of shape [1, width/4, height/4, 5] and is fetched in a 1d array.
        It is tricky here because the model takes input of size [1, height, width, 3].
        However, the output of the model, geometry is of size [1, width/4, height/4, 5], which
        means the output shrinked and also exchanged the x, y axises that represent the image.
        */

        score = floatBufferScore.array();

        ArrayList<Integer[]> filterOrigin = filterFromScoreMapOriginal();
        ArrayList<Float[]> filterGeometry = filterFromScoreMapGeometry();
        float[][] restoredRects = restoreRects(filterOrigin, filterGeometry);


        Log.i("output score", Integer.toString(score.length));
        Log.i("output geometry", Integer.toString(geometry.length));
        float[] inputForNMS = new float[restoredRects.length * (restoredRects[0].length+1)];
        for(int i = 0; i < restoredRects.length; i++) {
            float[] row = restoredRects[i];
            for(int j = 0; j < row.length; j++) {
                float number = restoredRects[i][j];
                inputForNMS[i*(row.length+1)+j] = number;
            }
            inputForNMS[i*(row.length+1)+8] = filteredScores.get(i);

        }

        return inputForNMS;
    }

    private  ArrayList<Integer[]> filterFromScoreMapOriginal() {
        ArrayList<Integer[]> filteredIndex = new ArrayList<Integer[]>();
        // filteredIndex is the original x,y coordinate of pixels after filtering.
        for (int i = 0; i < score.length; i++) {
            if (score[i] > score_map_thresh) {
                filteredScores.add(score[i]);
                filteredIndex.add(new Integer[] {(i % (inputWidth/4)) * 4, (i / (inputWidth/4)) * 4});
                // Here I first get the 2d index of score array, then multiply it by 4.
                // The multiplication is to retrieve the real x,y value of origin pixels.
            }
        }
        return filteredIndex;

    }

    private ArrayList<Float[]> filterFromScoreMapGeometry() {
        ArrayList<Float[]> filteredGeo = new ArrayList<Float[]>();
        // filteredGeo is the corresponding geometry of each pixel after filtering. Each contain
        // five channels representing 4 distances to the boundary and 1 rotation angel.
        Log.i("length of Score, Geo", score.length + " " + geometry.length);
        for (int i = 0; i < score.length; i++) {
            if (score[i] > score_map_thresh) {
                int size = inputHeight * inputWidth /16;
                filteredGeo.add(new Float[]{geometry[i * 5], geometry[i * 5 + 1], geometry[i * 5 + 2],
                        geometry[i * 5 + 3], geometry[i * 5 + 4]});
            }
            // add the geometry values that represent the distance from the origin to the rectangles'
            // boundaries and the rotation angle.
        }
        return filteredGeo;
    }

    /**
     * This restore a SINGLE rectangle with 8 floats in the array as the result.
     * It is in the order of Top-Left, Top-Right, Bottom-Right, Bottom-Left.
     * Each point with x before y. [x1, y1. x2. y2, x3, y3, x4, y4]
     * @return
     */
    private float[] restoreRectangle(Integer[] origin, Float[] pixelGeo) {
        Matrix matrix = new Matrix();
        float[] rect = new float[]{-pixelGeo[3], -pixelGeo[0], pixelGeo[1], -pixelGeo[0]
                , pixelGeo[1], pixelGeo[2], -pixelGeo[3], pixelGeo[2]};
        matrix.postRotate(- pixelGeo[4] * 180 / (float) Math.PI, 0, 0);
        matrix.postTranslate(origin[0], origin[1]);
        matrix.mapPoints(rect);

        return rect;

    }

    /**
     * This returns a N*8 2D float array, with the first dimension the number of rect, second the four points'
     * coordinates.
     * @param origins
     * @param geometrys
     * @return
     */
    private float[][] restoreRects(ArrayList<Integer[]> origins, ArrayList<Float[]> geometrys) {
        float[][] restoredRects = new float[origins.size()][8];
        Log.i("the size of origins", Integer.toString(origins.size()));
        for (int i = 0; i < origins.size(); i++) {
            restoredRects[i] = restoreRectangle(origins.get(i), geometrys.get(i));
        }
        return restoredRects;
    }

}