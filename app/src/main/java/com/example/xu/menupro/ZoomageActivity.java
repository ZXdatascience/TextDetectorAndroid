package com.example.xu.menupro;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;


/**
 * Created by xu on 24/02/18.
 */

public class ZoomageActivity extends AppCompatActivity {

    private ZoomageView demoView;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.zoomage_layout);

        Bitmap bitmap = Global.bitmap;
        int menuId = getIntent().getIntExtra("menuLanguage", 0);
        int targetId = getIntent().getIntExtra("targetLanguage", 0);

        demoView = (ZoomageView)findViewById(R.id.demoView);
        demoView.setMenuLanguage(menuId);
        demoView.setTargetLanguage(targetId);
        demoView.processImage(bitmap);


    }


}
