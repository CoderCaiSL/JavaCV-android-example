package org.bytedeco.javacv_android_example;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.bytedeco.javacv_android_example.record.RecordActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraXBasic";
    private static final String[] REQUIRED_PERMISSIONS = new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btnRecord).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, RecordActivity.class));
            }
        });
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        findViewById(R.id.btnPDF).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float A4Width = 794f;
                float A4Height = 1123f;
                PdfDocument document = new PdfDocument();//1, 建立PdfDocument
                View root = findViewById(R.id.rootView);
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        (int) A4Height,(int)  A4Width, 1).create();//2
                PdfDocument.Page page = document.startPage(pageInfo);
                root.draw(page.getCanvas());//3
                document.finishPage(page);//4
                try {
                    String path = getCacheDir() + File.separator + "table.pdf";
                    File e = new File(path);
                    if (e.exists()) {
                        e.delete();
                    }
                    document.writeTo(new FileOutputStream(e));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private boolean allPermissionsGranted() {
        for(String permission: REQUIRED_PERMISSIONS) {
            Boolean granted = ContextCompat.checkSelfPermission(
                    getBaseContext(), permission) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                return false;
            }
        }
        return true;
    }
}
