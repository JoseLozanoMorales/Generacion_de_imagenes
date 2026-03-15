package com.example.generacindeimagenes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.text.Text;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements OnSuccessListener<Text>, OnFailureListener {

    private TextView txtResults;
    private ImageView mImageView;
    private Button btnContinuar;

    private String nacionalidadDetectada = "";
    private String currentPhotoPath;

    private static final String API_KEY = BuildConfig.OPENAI_API_KEY;
    private Bitmap mSelectedImage;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    // Cliente reutilizable con timeouts definidos
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Permiso cámara
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        // ✅ Permiso notificaciones en runtime para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        mImageView   = findViewById(R.id.image_view);
        btnContinuar = findViewById(R.id.button2);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            Uri imageUri = result.getData().getData();
                            mSelectedImage = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), imageUri);
                            mSelectedImage = rotateImageIfRequired(mSelectedImage, imageUri);
                            mSelectedImage = resizeBitmap(mSelectedImage, 1024);
                            mImageView.setImageBitmap(mSelectedImage);
                            btnContinuar.setEnabled(false);
                            nacionalidadDetectada = "";
                        } catch (IOException e) {
                            Toast.makeText(this,
                                    "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        mSelectedImage = BitmapFactory.decodeFile(currentPhotoPath);
                        try {
                            mSelectedImage = rotateImageIfRequired(
                                    mSelectedImage,
                                    Uri.fromFile(new File(currentPhotoPath)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mSelectedImage = resizeBitmap(mSelectedImage, 1024);
                        mImageView.setImageBitmap(mSelectedImage);
                        btnContinuar.setEnabled(false);
                        nacionalidadDetectada = "";
                    }
                });
    }

    // ─── Utilidades de imagen ─────────────────────────────────────────────────

    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {
        InputStream input = getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23) {
            ei = new ExifInterface(input);
        } else {
            ei = new ExifInterface(selectedImage.getPath());
        }
        int orientation = ei.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180: return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270: return rotateImage(img, 270);
            default: return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotated = Bitmap.createBitmap(
                img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotated;
    }

    private Bitmap resizeBitmap(Bitmap source, int maxLength) {
        try {
            if (source.getWidth() <= maxLength && source.getHeight() <= maxLength)
                return source;
            double aspectRatio = (double) source.getHeight() / (double) source.getWidth();
            int targetWidth, targetHeight;
            if (source.getWidth() > source.getHeight()) {
                targetWidth  = maxLength;
                targetHeight = (int) (targetWidth * aspectRatio);
            } else {
                targetHeight = maxLength;
                targetWidth  = (int) (targetHeight / aspectRatio);
            }
            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        } catch (Exception e) {
            return source;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Guarda el bitmap en un archivo temporal PNG y devuelve su path.
     * El Worker necesita un path de archivo, no un Bitmap en memoria.
     */
    private String saveBitmapToTempFile(Bitmap bitmap) throws IOException {
        File tempFile = File.createTempFile(
                "worker_input_",
                ".png",
                getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        );
        FileOutputStream fos = new FileOutputStream(tempFile);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
        return tempFile.getAbsolutePath();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // ─── Acciones de UI ───────────────────────────────────────────────────────

    public void abrirGaleria(View view) {
        Intent i = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(i);
    }

    public void abrirCamara(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this,
                        "Error creando el archivo de imagen", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    // ─── Detección de nacionalidad ────────────────────────────────────────────

    public void detectarNacionalidad(View view) {
        if (mSelectedImage == null) {
            Toast.makeText(this, "Primero selecciona una imagen", Toast.LENGTH_SHORT).show();
            return;
        }

        String base64 = bitmapToBase64(mSelectedImage);

        new Thread(() -> {
            try {
                String listaNacionalidades =
                        "Awá, Chachi, Épera, Tsáchila, Otavalo, Kayambi, Kitu Kara, Panzaleo, "
                                + "Chibuleo, Salasaka, Waranka, Puruhá, Kañari, Saraguro, Achuar, Andoa, "
                                + "Cofán, Siona, Secoya, Shuar, Shiwiar, Waorani, Zápara, Kichwa Amazónico";

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-4o-mini");

                JSONArray messages = new JSONArray();

                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content",
                        "Eres un clasificador. Tu tarea es seleccionar OBLIGATORIAMENTE una "
                                + "nacionalidad de la lista proporcionada. Analiza los rasgos faciales "
                                + "de la persona en la imagen (estructura ósea, ojos, pómulos, forma del "
                                + "rostro). Debes escoger SIEMPRE la nacionalidad más parecida de la "
                                + "lista. Nunca respondas que no puedes identificar. Nunca des "
                                + "explicaciones. Responde SOLO con el nombre exacto de una nacionalidad "
                                + "de la lista.");
                messages.put(systemMessage);

                JSONObject userEx1 = new JSONObject();
                userEx1.put("role", "user");
                userEx1.put("content", "Identify the group from the list based on the face.");
                messages.put(userEx1);

                JSONObject assistantEx1 = new JSONObject();
                assistantEx1.put("role", "assistant");
                assistantEx1.put("content", "Otavalo");
                messages.put(assistantEx1);

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                JSONArray content = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text",
                        "LISTA DE NACIONALIDADES:\n" + listaNacionalidades + "\n\n"
                                + "INSTRUCCIONES:\n"
                                + "- Analiza SOLO los rasgos faciales de la persona.\n"
                                + "- Debes seleccionar obligatoriamente UNA nacionalidad de la lista.\n"
                                + "- Si no estás seguro, elige la que MÁS se parezca.\n"
                                + "- NO digas que no puedes identificar.\n"
                                + "- Responde SOLO con el nombre exacto de la nacionalidad.");

                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", "data:image/jpeg;base64," + base64);
                imagePart.put("image_url", imageUrl);

                content.put(textPart);
                content.put(imagePart);
                userMessage.put("content", content);
                messages.put(userMessage);

                jsonBody.put("messages", messages);
                jsonBody.put("max_tokens", 50);
                jsonBody.put("temperature", 0.3);

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), jsonBody.toString());
                Request request = new Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .post(body)
                        .build();

                Response response = httpClient.newCall(request).execute();
                String result = response.body().string();

                JSONObject jsonObject = new JSONObject(result);
                if (jsonObject.has("choices")) {
                    String rawNacionalidad = jsonObject
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    for (String n : listaNacionalidades.split(", ")) {
                        if (rawNacionalidad.contains(n)) {
                            rawNacionalidad = n;
                            break;
                        }
                    }

                    // ✅ Asignación de variable dentro de runOnUiThread
                    final String nacionalidadFinal = rawNacionalidad;
                    runOnUiThread(() -> {
                        nacionalidadDetectada = nacionalidadFinal;
                        Toast.makeText(this,
                                "Nacionalidad: " + nacionalidadDetectada,
                                Toast.LENGTH_LONG).show();
                        btnContinuar.setEnabled(true);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Error al detectar nacionalidad",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ─── Generación de imagen ─────────────────────────────────────────────────

    public void generarImagenConRostro(View view) {
        if (mSelectedImage == null) {
            Toast.makeText(this, "Primero selecciona una imagen", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nacionalidadDetectada.isEmpty()) {
            Toast.makeText(this, "Primero detecta la nacionalidad", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Toast requerido en el hilo principal
        Toast.makeText(this, "Generando imagen...", Toast.LENGTH_SHORT).show();

        // ✅ Desactivar botón para evitar doble envío
        btnContinuar.setEnabled(false);

        new Thread(() -> {
            try {
                // ✅ Guardar bitmap en archivo temporal para pasarlo al Worker
                String imagePath = saveBitmapToTempFile(mSelectedImage);

                // ✅ Construir datos de entrada para el Worker
                Data inputData = new Data.Builder()
                        .putString("nacionalidad", nacionalidadDetectada)
                        .putString("imagen_path", imagePath)
                        .build();

                // ✅ Encolar el Worker en el hilo principal
                runOnUiThread(() -> {
                    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                            ImageGenerationWorker.class)
                            .setInputData(inputData)
                            .build();
                    WorkManager.getInstance(this).enqueue(workRequest);
                });

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Error preparando la imagen", Toast.LENGTH_SHORT).show();
                    btnContinuar.setEnabled(true);
                });
            }
        }).start();
    }

    // ─── ML Kit ───────────────────────────────────────────────────────────────

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        StringBuilder resultados = new StringBuilder();
        if (blocks.isEmpty()) {
            resultados.append("No se encontró texto");
        } else {
            for (Text.TextBlock block : blocks) {
                for (Text.Line line : block.getLines()) {
                    for (Text.Element element : line.getElements()) {
                        resultados.append(element.getText()).append(" ");
                    }
                }
                resultados.append("\n");
            }
        }
        if (txtResults != null) txtResults.setText(resultados.toString());
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        if (txtResults != null) txtResults.setText("Error al procesar la imagen");
    }
}