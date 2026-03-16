package com.example.generacindeimagenes;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity2 extends AppCompatActivity {
    ImageView imageView;
    Button btnDescargar;
    Button btnVolver;

    private final ActivityResultLauncher<Intent> saveLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveBitmapToUri(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageView = findViewById(R.id.image_view2);
        btnDescargar = findViewById(R.id.btdescargar);
        btnVolver = findViewById(R.id.btnVolver);

        if (ImageHolder.generatedImage != null) {
            imageView.setImageBitmap(ImageHolder.generatedImage);
        }

        btnDescargar.setOnClickListener(v -> abrirSelectorDeArchivos());
        
        btnVolver.setOnClickListener(v -> {
            // Regresa a MainActivity limpiando el stack para evitar comportamiento extraño
            Intent intent = new Intent(MainActivity2.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void abrirSelectorDeArchivos() {
        if (ImageHolder.generatedImage == null) {
            Toast.makeText(this, "No hay imagen para guardar", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "Yo_Ancestral_" + System.currentTimeMillis() + ".png");
        saveLauncher.launch(intent);
    }

    private void saveBitmapToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                ImageHolder.generatedImage.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                Toast.makeText(this, "¡Recuerdo guardado con éxito!", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
