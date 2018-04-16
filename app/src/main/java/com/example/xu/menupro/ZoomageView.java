package com.example.xu.menupro;

/**
 * Created by xu on 24/02/18.
 */

/**
 * Copyright 2016 Jeffrey Sibbold
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.view.ScaleGestureDetectorCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;


import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * ZoomageView is a pinch-to-zoom extension of {@link ImageView}, providing a smooth
 * user experience and a very natural feel when zooming and translating. It also supports
 * automatic resetting, and allows for exterior bounds restriction to keep the image within
 * visible window.
 */
public class ZoomageView extends AppCompatImageView implements OnScaleGestureListener {

    private final float MIN_SCALE = 0.6f;
    private final float MAX_SCALE = 8f;
    private final int RESET_DURATION = 200;

    private ScaleType startScaleType;

    // These matrices will be used to move and zoom image
    private Matrix matrix = new Matrix();
    private Matrix startMatrix = new Matrix();

    private float[] matrixValues = new float[9];
    private float[] startValues = null;

    private float minScale = MIN_SCALE;
    private float maxScale = MAX_SCALE;

    //the adjusted scale bounds that account for an image's starting scale values
    private float calculatedMinScale = MIN_SCALE;
    private float calculatedMaxScale = MAX_SCALE;

    private final RectF bounds = new RectF();

    private boolean translatable = true;
    private boolean zoomable = true;
    private boolean restrictBounds = false;
    private boolean animateOnReset = true;
    private boolean autoCenter = false;

    private PointF last = new PointF(0, 0);
    private float startScale = 1f;
    private float scaleBy = 1f;
    private int previousPointerCount = 1;

    private ScaleGestureDetector scaleDetector;

    //

    private ArrayList<String> wordList = new ArrayList<String>();
    private ArrayList<String> blockList = new ArrayList<String>();

    private Paint paintWordBox = new Paint();
    private Paint paintBlockBox = new Paint();
    @LanguageOptions private int menuLanguage;
    @LanguageOptions private int targetLanguage;

    private Context mContext;

    private long touchStartTime;

    private int pointerCount = 1;

    private float actionDownX;

    private float actionDownY;
    // Variables for buliding new Dialog.
    private View inflate;
    private TextView translate;
    private TextView showText;
    private Dialog dialog;
    private static final String API_KEY = "AIzaSyAIxS8xLbqOZuukPiUP2g55IWsc56y9yoU";
    //

    private Detector d;
    private float nms_thres=0.2f;
    private float[] afterNMS;


    public ZoomageView(Context context) {
        this(context, null);
    }

    public ZoomageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mContext = context;
        setPaintBlockBox();
        setPaintWordBox();

        verifyScaleRange();
        scaleDetector = new ScaleGestureDetector(context, this);
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false);
        startScaleType = getScaleType();
    }

    static {
        System.loadLibrary("tensorflow_inference");
        System.loadLibrary("nms");
    }

    public native float[] nms(float[] rectsCoord, int length, float nms_thres);

    /**
     * Combine the rectangles we have with the image.
     */
    public void combineRect(Bitmap bitmap, float[] ratio) {
        int numOfFinalRects = afterNMS.length / 9;

        //Create a new image bitmap and attach a brand new canvas to it
        Bitmap rectBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
        Canvas rectCanvas = new Canvas(rectBitmap);
        //Draw the image bitmap into the cavas
        rectCanvas.drawBitmap(bitmap, 0, 0, null);
        //Draw everything else you want into the canvas, in this example a rectangle with rounded edges
        setPaintBlockBox();

        for (int i = 0; i < numOfFinalRects; i++) {
            int[][] pointsRect = new int[4][2];
            for (int j = 0; j < 4; j++) {
                pointsRect[j] = new int[]{(int)((afterNMS[i * 9 + j * 2]+1)/ratio[1]), (int) ((afterNMS[i * 9 + j * 2 + 1]+1)/ratio[0])};
            }
            for (int j = 0; j < 3; j++){
                rectCanvas.drawLine(pointsRect[j][0], pointsRect[j][1], pointsRect[j+1][0], pointsRect[j+1][1], paintBlockBox);
            }
            rectCanvas.drawLine(pointsRect[3][0], pointsRect[3][1], pointsRect[0][0], pointsRect[0][1], paintBlockBox);

        }
        setImageDrawable(new BitmapDrawable(getResources(), rectBitmap));

    }

    private void verifyScaleRange() {
        if (minScale >= maxScale) {
            throw new IllegalStateException("minScale must be less than maxScale");
        }

        if (minScale < 0) {
            throw new IllegalStateException("minScale must be greater than 0");
        }

        if (maxScale < 0) {
            throw new IllegalStateException("maxScale must be greater than 0");
        }
    }

    /**
     * Set the minimum and maximum allowed scale for zooming. {@code minScale} cannot
     * be greater than {@code maxScale} and neither can be 0 or less. This will result
     * in an {@link IllegalStateException}.
     * @param minScale minimum allowed scale
     * @param maxScale maximum allowed scale
     */
    public void setScaleRange(final float minScale, final float maxScale) {
        this.minScale = minScale;
        this.maxScale = maxScale;

        startValues = null;

        verifyScaleRange();
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        super.setScaleType(scaleType);
        startScaleType = scaleType;
        startValues = null;
    }

    /**
     * Set enabled state of the view. Note that this will reset the image's
     * {@link ScaleType} to its pre-zoom state.
     * @param enabled enabled state
     */
    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);

        if (!enabled) {
            setScaleType(startScaleType);
        }
    }

    /**
     * Update the bounds of the displayed image based on the current matrix.
     *
     * @param values the image's current matrix values.
     */
    private void updateBounds(final float[] values) {
        if (getDrawable() != null) {
            bounds.set(values[Matrix.MTRANS_X],
                    values[Matrix.MTRANS_Y],
                    getDrawable().getIntrinsicWidth() * values[Matrix.MSCALE_X] + values[Matrix.MTRANS_X],
                    getDrawable().getIntrinsicHeight() * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]);
        }
    }

    /**
     * Get the width of the displayed image.
     *
     * @return the current width of the image as displayed (not the width of the {@link ImageView} itself.
     */
    private float getCurrentDisplayedWidth() {
        if (getDrawable() != null)
            return getDrawable().getIntrinsicWidth() * matrixValues[Matrix.MSCALE_X];
        else
            return 0;
    }

    /**
     * Get the height of the displayed image.
     *
     * @return the current height of the image as displayed (not the height of the {@link ImageView} itself.
     */
    private float getCurrentDisplayedHeight() {
        if (getDrawable() != null)
            return getDrawable().getIntrinsicHeight() * matrixValues[Matrix.MSCALE_Y];
        else
            return 0;
    }

    /**
     * Remember our starting values so we can animate our image back to its original position.
     */
    private void setStartValues() {
        startValues = new float[9];
        startMatrix = new Matrix(getImageMatrix());
        startMatrix.getValues(startValues);
        calculatedMinScale = minScale * startValues[Matrix.MSCALE_X];
        calculatedMaxScale = maxScale * startValues[Matrix.MSCALE_X];
    }

    /**
     * Set the color, style of the paint, which will be used to paint the word's rectangles.
     */
    private void setPaintWordBox() {
        paintWordBox.setColor(Color.GRAY);
        paintWordBox.setStyle(Paint.Style.STROKE);
        paintWordBox.setStrokeWidth(5);
    }

    /**
     * Set the color, style of the paint, which will be used to paint the textBlock(para)'s rectangles.
     */
    private void setPaintBlockBox() {
        paintBlockBox.setColor(Color.BLUE);
        paintBlockBox.setStyle(Paint.Style.STROKE);
        paintBlockBox.setStrokeWidth(5);
    }

    public void setMenuLanguage(@LanguageOptions final int language) {
        this.menuLanguage = language;
    }

    public void setTargetLanguage(@LanguageOptions final int language) {
        this.targetLanguage = language;
    }

    /**
     * Process image using google mobile vision API, get the bounding box for each word in the image.
     * Then drow these word box on the original image.
     */
    public void processImage(Bitmap bitmap) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Log.i("bitmap", Integer.toString(bitmap.getHeight()) + Integer.toString(bitmap.getWidth()));
//        if (bitmap.getHeight() > 1000 || bitmap.getWidth() > 1000) {
//            int scaledWidth = bitmap.getWidth();
//            int scaledHeight = bitmap.getHeight();
//            while (scaledHeight > 1000 || scaledWidth > 1000) {
//                scaledHeight /= 2;
//                scaledWidth /= 2;
//            }
//            bitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false);
//        }
        int[] resized = resize(bitmap);
        Bitmap resizedIamge = resizeImage(bitmap, resized);
        float[] ratio = getRatio(bitmap, resized);
        d = Detector.create(mContext.getAssets(), resizedIamge.getWidth(),resizedIamge.getHeight());
        float[] restoredRects1D = d.detectIamge(resizedIamge, ratio);
        // TIll now, we have the raw Rects that restored from the image.
        // The next step, is to use the native c++ method to implement the NMS.


        afterNMS = nms(restoredRects1D, restoredRects1D.length/9, nms_thres);

        combineRect(bitmap, ratio);
    }

    /**
     * Show the dialog for translating the text in the block.
     * @param x the x point when the finger leaves the screen
     * @param y the y point when the finger leaves the screen
     */
    public void showDialog(float x, float y){
        dialog = new Dialog(mContext, R.style.ActionSheetDialogStyle);
        //填充对话框的布局
        inflate = LayoutInflater.from(mContext).inflate(R.layout.translate_dialog, null);
        //初始化控件
        translate = (TextView) inflate.findViewById(R.id.translate);
        showText = (TextView) inflate.findViewById(R.id.showText);


//        setTanslateListener();

        // Show the text in the block box.
//        for (int i = 0; i < blockRectList.size(); i++) {
//            if (blockRectList.get(i).contains(x, y)) {
//                showText.setText(blockList.get(i));
//            }
//        }
        //将布局设置给Dialog
        dialog.setContentView(inflate);
        //获取当前Activity所在的窗体
        Window dialogWindow = dialog.getWindow();
        //设置Dialog从窗体底部弹出
        dialogWindow.setGravity( Gravity.BOTTOM);
        //获得窗体的属性
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.y = 20;//设置Dialog距离底部的距离
//       将属性设置给窗体
        dialogWindow.setAttributes(lp);
        dialog.show();//显示对话框
    }

    /**
     *  Set the listener for the translate button. One the button is clicked, the app make a
        request and show the translated text in the textView.
     */
//    private void setTanslateListener() {
//        final Handler textViewHandler = new Handler();
//        translate.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                new AsyncTask<Void, Void, Void>() {
//                    @Override
//                    protected Void doInBackground(Void... params) {
//                        String beforeTranslation = showText.getText().toString();
//                        TranslateOptions options = TranslateOptions.newBuilder()
//                                .setApiKey(API_KEY)
//                                .build();
//                        Translate translate = options.getService();
//                        final Translation translation =
//                                translate.translate(beforeTranslation,
//                                        Translate.TranslateOption.targetLanguage("zh-CN"));
//                        textViewHandler.post(new Runnable() {
//                            @Override
//                            public void run() {
//                                if (showText != null) {
//                                    showText.setText(translation.getTranslatedText());
//                                }
//                            }
//                        });
//                        return null;
//                    }
//                }.execute();
//            }
//        });
//    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (isEnabled() && (zoomable || translatable)) {
            if (getScaleType() != ScaleType.MATRIX) {
                super.setScaleType(ScaleType.MATRIX);
            }

            if (startValues == null) {
                setStartValues();
            }

            //get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            matrix.set(getImageMatrix());
            matrix.getValues(matrixValues);
            updateBounds(matrixValues);

            scaleDetector.onTouchEvent(event);

            /* if the event is a down touch, or if the number of touch points changed,
            * we should reset our start point, as event origins have likely shifted to a
            * different part of the screen*/
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||
                    event.getPointerCount() != previousPointerCount) {
                last.set(scaleDetector.getFocusX(), scaleDetector.getFocusY());
                touchStartTime = Calendar.getInstance().getTimeInMillis();;
                actionDownX = event.getX();
                actionDownY = event.getY();
            }
            else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {

                final float focusx = scaleDetector.getFocusX();
                final float focusy = scaleDetector.getFocusY();
                if (event.getPointerCount() > 1) {
                    pointerCount = event.getPointerCount();
                }


                if (translatable) {
                    //calculate the distance for translation
                    float xdistance = getXDistance(focusx, last.x);
                    float ydistance = getYDistance(focusy, last.y);
                    matrix.postTranslate(xdistance, ydistance);
                }

                if (zoomable) {
                    matrix.postScale(scaleBy, scaleBy, focusx, focusy);
                }

                setImageMatrix(matrix);

                last.set(focusx, focusy);
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                scaleBy = 1f;
                // when the finger is up, check the time of pressing, the distance the finger moved.
                long endTime = Calendar.getInstance().getTimeInMillis();;
                final float focusx = scaleDetector.getFocusX();
                final float focusy = scaleDetector.getFocusY();
                float xdistance = getXDistance(focusx, actionDownX);
                float ydistance = getYDistance(focusy, actionDownY);
                double distance = Math.sqrt(xdistance * xdistance + ydistance * ydistance);

                float[] values = new float[9];
                matrix.getValues(values);
                float relativeX = (event.getX() - values[2]) / values[0];
                float relativeY = (event.getY() - values[5]) / values[4];
                boolean touchOnBox = false;
//                for (int i = 0; i < blockRectList.size(); i++) {
//                    if (blockRectList.get(i).contains(relativeX, relativeY)) {
//                        touchOnBox = true;
//                        break;
//                    }
//                }

                if ((endTime - touchStartTime)/1000 < 0.5 && distance < 15 && pointerCount == 1
                        && touchOnBox == true) {
                    showDialog(relativeX, relativeY);
                }

                resetImage();
                pointerCount = 1;
            }

            //this tracks whether they have changed the number of fingers down
            previousPointerCount = event.getPointerCount();

            return true;
        }

        return super.onTouchEvent(event);
    }

    /**
     * Reset the image based on UNDER mode.
     */
    private void resetImage() {
        if (matrixValues[Matrix.MSCALE_X] <= startValues[Matrix.MSCALE_X]) {
            reset();
        } else {
            center();
        }

    }

    /**
     * This helps to keep the image on-screen by animating the translation to the nearest
     * edge, both vertically and horizontally.
     */
    private void center() {
        if (autoCenter) {
            animateTranslationX();
            animateTranslationY();
        }
    }

    /**
     * Reset image back to its original size. Will snap back to original size
     */
    public void reset() {
        reset(animateOnReset);
    }

    /**
     * Reset image back to its starting size. If {@code animate} is false, image
     * will snap back to its original size.
     * @param animate animate the image back to its starting size
     */
    public void reset(final boolean animate) {
        if (animate) {
            animateToStartMatrix();
        }
        else {
            setImageMatrix(startMatrix);
        }
    }

    /**
     * Animate the matrix back to its original position after the user stopped interacting with it.
     */
    private void animateToStartMatrix() {

        final Matrix beginMatrix = new Matrix(getImageMatrix());
        beginMatrix.getValues(matrixValues);

        //difference in current and original values
        final float xsdiff = startValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X];
        final float ysdiff = startValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y];
        final float xtdiff = startValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X];
        final float ytdiff = startValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y];

        ValueAnimator anim = ValueAnimator.ofFloat(0, 1f);
        anim.addUpdateListener(new AnimatorUpdateListener() {

            final Matrix activeMatrix = new Matrix(getImageMatrix());
            final float[] values = new float[9];

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float val = (Float) animation.getAnimatedValue();
                activeMatrix.set(beginMatrix);
                activeMatrix.getValues(values);
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xtdiff * val;
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + ytdiff * val;
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xsdiff * val;
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + ysdiff * val;
                activeMatrix.setValues(values);
                setImageMatrix(activeMatrix);
            }
        });
        anim.setDuration(RESET_DURATION);
        anim.start();
    }

    private void animateTranslationX() {
        if (getCurrentDisplayedWidth() > getWidth()) {
            //the left edge is too far to the interior
            if (bounds.left > 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0);
            }
            //the right edge is too far to the interior
            else if (bounds.right < getWidth()) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + getWidth() - bounds.right);
            }
        } else {
            //left edge needs to be pulled in, and should be considered before the right edge
            if (bounds.left < 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0);
            }
            //right edge needs to be pulled in
            else if (bounds.right > getWidth()) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + getWidth() - bounds.right);
            }
        }
    }

    private void animateTranslationY() {
        if (getCurrentDisplayedHeight() > getHeight()) {
            //the top edge is too far to the interior
            if (bounds.top > 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0);
            }
            //the bottom edge is too far to the interior
            else if (bounds.bottom < getHeight()) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + getHeight() - bounds.bottom);
            }
        } else {
            //top needs to be pulled in, and needs to be considered before the bottom edge
            if (bounds.top < 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0);
            }
            //bottom edge needs to be pulled in
            else if (bounds.bottom > getHeight()) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + getHeight() - bounds.bottom);
            }
        }
    }

    private void animateMatrixIndex(final int index, final float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(matrixValues[index], to);
        animator.addUpdateListener(new AnimatorUpdateListener() {

            final float[] values = new float[9];
            Matrix current = new Matrix();

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                current.set(getImageMatrix());
                current.getValues(values);
                values[index] = (Float) animation.getAnimatedValue();
                current.setValues(values);
                setImageMatrix(current);
            }
        });
        animator.setDuration(RESET_DURATION);
        animator.start();
    }

    /**
     * Get the x distance to translate the current image.
     *
     * @param toX   the current x location of touch focus
     * @param fromX the last x location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private float getXDistance(final float toX, final float fromX) {
        float xdistance = toX - fromX;

        if (restrictBounds) {
            xdistance = getRestrictedXDistance(xdistance);
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.right + xdistance < 0) {
            xdistance = -bounds.right;
        }
        else if (bounds.left + xdistance > getWidth()) {
            xdistance = getWidth() - bounds.left;
        }

        return xdistance;
    }

    /**
     * Get the horizontal distance to translate the current image, but restrict
     * it to the outer bounds of the {@link ImageView}. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     * @param xdistance the current desired horizontal distance to translate
     * @return the actual horizontal distance to translate with bounds restrictions
     */
    private float getRestrictedXDistance(final float xdistance) {
        float restrictedXDistance = xdistance;

        if (getCurrentDisplayedWidth() >= getWidth()) {
            if (bounds.left <= 0 && bounds.left + xdistance > 0 && !scaleDetector.isInProgress()) {
                restrictedXDistance = -bounds.left;
            } else if (bounds.right >= getWidth() && bounds.right + xdistance < getWidth() && !scaleDetector.isInProgress()) {
                restrictedXDistance = getWidth() - bounds.right;
            }
        } else if (!scaleDetector.isInProgress()) {
            if (bounds.left >= 0 && bounds.left + xdistance < 0) {
                restrictedXDistance = -bounds.left;
            } else if (bounds.right <= getWidth() && bounds.right + xdistance > getWidth()) {
                restrictedXDistance = getWidth() - bounds.right;
            }
        }

        return restrictedXDistance;
    }

    /**
     * Get the y distance to translate the current image.
     *
     * @param toY   the current y location of touch focus
     * @param fromY the last y location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private float getYDistance(final float toY, final float fromY) {
        float ydistance = toY - fromY;

        if (restrictBounds) {
            ydistance = getRestrictedYDistance(ydistance);
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.bottom + ydistance < 0) {
            ydistance = -bounds.bottom;
        }
        else if (bounds.top + ydistance > getHeight()) {
            ydistance = getHeight() - bounds.top;
        }

        return ydistance;
    }

    /**
     * Get the vertical distance to translate the current image, but restrict
     * it to the outer bounds of the {@link ImageView}. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     * @param ydistance the current desired vertical distance to translate
     * @return the actual vertical distance to translate with bounds restrictions
     */
    private float getRestrictedYDistance(final float ydistance) {
        float restrictedYDistance = ydistance;

        if (getCurrentDisplayedHeight() >= getHeight()) {
            if (bounds.top <= 0 && bounds.top + ydistance > 0 && !scaleDetector.isInProgress()) {
                restrictedYDistance = -bounds.top;
            } else if (bounds.bottom >= getHeight() && bounds.bottom + ydistance < getHeight() && !scaleDetector.isInProgress()) {
                restrictedYDistance = getHeight() - bounds.bottom;
            }
        } else if (!scaleDetector.isInProgress()) {
            if (bounds.top >= 0 && bounds.top + ydistance < 0) {
                restrictedYDistance = -bounds.top;
            } else if (bounds.bottom <= getHeight() && bounds.bottom + ydistance > getHeight()) {
                restrictedYDistance = getHeight() - bounds.bottom;
            }
        }

        return restrictedYDistance;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        //calculate value we should scale by, ultimately the scale will be startScale*scaleFactor
        scaleBy = (startScale * detector.getScaleFactor()) / matrixValues[Matrix.MSCALE_X];

        //what the scaling should end up at after the transformation
        final float projectedScale = scaleBy * matrixValues[Matrix.MSCALE_X];

        //clamp to the min/max if it's going over
        if (projectedScale < calculatedMinScale) {
            scaleBy = calculatedMinScale / matrixValues[Matrix.MSCALE_X];
        } else if (projectedScale > calculatedMaxScale) {
            scaleBy = calculatedMaxScale / matrixValues[Matrix.MSCALE_X];
        }

        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        startScale = matrixValues[Matrix.MSCALE_X];
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        scaleBy = 1f;
    }

    private int[] resize(Bitmap bitmap) {

        int resizeH = bitmap.getHeight();
        int h = bitmap.getHeight();
        int resizeW = bitmap.getWidth();
        int w = bitmap.getWidth();
        if (h % 32 != 0) {
            resizeH = (h / 32 - 1) * 32;
        }
        if (w % 32 != 0) {
            resizeW = (w / 32 - 1) * 32;
        }
        int[] resize = {resizeH, resizeW};
        return resize;
    }

    private float[] getRatio(Bitmap bitmap, int[] resize) {
        int resizeH = resize[0];
        int resizeW = resize[1];
        float ratioH = ((float)resizeH) / ((float)bitmap.getHeight());
        float ratioW = ((float)resizeW) / ((float)bitmap.getWidth());
        float[] ratio = {ratioH, ratioW};
        return  ratio;
    }

    private Bitmap resizeImage(Bitmap bitmap, int[] resize) {

        int resizeH = resize[0];
        int resizeW = resize[1];
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, resizeW, resizeH, false);
        Log.i("resized bitmap", Integer.toString(resizedBitmap.getHeight()) + Integer.toString(resizedBitmap.getWidth()));
        return resizedBitmap;

    }
}
