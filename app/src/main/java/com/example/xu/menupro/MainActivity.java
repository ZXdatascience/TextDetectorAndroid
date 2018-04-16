package com.example.xu.menupro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;

/**
 * Created by xu on 23/02/18.
 */

public class MainActivity extends AppCompatActivity implements ImageInputHelper.ImageActionListener, View.OnClickListener {

    private ImageInputHelper imageInputHelper;
    private ZoomageView demoView;
    @LanguageOptions private int menuLanguage;
    @LanguageOptions private int targetLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageInputHelper = new ImageInputHelper(this);
        imageInputHelper.setImageActionListener(this);


        findViewById(R.id.take_picture_with_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageInputHelper.takePhotoWithCamera();
            }
        });
        findViewById(R.id.select_photo_from_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageInputHelper.selectImageFromGallery();
            }
        });


        findViewById(R.id.menuLanguage).setOnClickListener(this);
        findViewById(R.id.targetLanuage).setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        imageInputHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onImageSelectedFromGallery(Uri uri, File imageFile) {
        // cropping the selected image. crop intent will have aspect ratio 16/9 and result image
        // will have size 800x450
        this.onImageProcessing(uri);

    }

    @Override
    public void onImageTakenFromCamera(Uri uri, File imageFile) {
        // cropping the taken photo. crop intent will have aspect ratio 16/9 and result image
        // will have size 800x450
        this.onImageProcessing(uri);

    }

    @Override
    public void onImageCropped(Uri uri, File imageFile) {
        try {
            // getting bitmap from uri
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            // showing bitmap in image view
            ((ImageView) findViewById(R.id.image)).setImageBitmap(bitmap);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onImageProcessing(Uri uri) {
        try {
            // getting bitmap from uri
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            Global.bitmap = bitmap;
            // showing bitmap in image view
            Intent processIntent = new Intent(this, ZoomageActivity.class);
            processIntent.putExtra("menuLanguage", menuLanguage);
            processIntent.putExtra("targetLanguage", targetLanguage);
            startActivity(processIntent);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.menuLanguage) {
            showMenuLanguageOptions();
        }
        else {
            showTargetLanuageOptions();
        }
    }

    private void showMenuLanguageOptions() {
        CharSequence[] options = new CharSequence[]{"English", "French", "Chinese", "Japanese"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                menuLanguage = which;
            }
        });

        builder.create().show();
    }

    private void showTargetLanuageOptions() {
        CharSequence[] options = new CharSequence[]{"English", "French", "Chinese", "Japanese"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                targetLanguage = which;
            }
        });

        builder.create().show();
    }
}