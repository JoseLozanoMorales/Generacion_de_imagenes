package com.example.generacindeimagenes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.text.Text;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnSuccessListener<Text>, OnFailureListener {

    private TextView txtResults;
    private ImageView mImageView;
    private Button btnContinuar;

    private String nacionalidadDetectada = "";
    private String currentPhotoPath;

    private static final String API_KEY = com.example.generacindeimagenes.BuildConfig.OPENAI_API_KEY;
    private Bitmap mSelectedImage;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

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

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        mImageView = findViewById(R.id.image_view);
        btnContinuar = findViewById(R.id.button2);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            Uri imageUri = result.getData().getData();
                            mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                            mSelectedImage = rotateImageIfRequired(mSelectedImage, imageUri);
                            mSelectedImage = resizeBitmap(mSelectedImage, 1024);
                            mImageView.setImageBitmap(mSelectedImage);
                            // Desactivar continuar si se cambia la imagen hasta detectar nueva nacionalidad
                            btnContinuar.setEnabled(false);
                            nacionalidadDetectada = "";
                        } catch (IOException e) {
                            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
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
                            mSelectedImage = rotateImageIfRequired(mSelectedImage, Uri.fromFile(new File(currentPhotoPath)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mSelectedImage = resizeBitmap(mSelectedImage, 1024);
                        mImageView.setImageBitmap(mSelectedImage);
                        // Desactivar continuar si se toma nueva foto hasta detectar nacionalidad
                        btnContinuar.setEnabled(false);
                        nacionalidadDetectada = "";
                    }
                });
    }

    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {
        InputStream input = getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (android.os.Build.VERSION.SDK_INT > 23) {
            ei = new ExifInterface(input);
        } else {
            ei = new ExifInterface(selectedImage.getPath());
        }

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private Bitmap resizeBitmap(Bitmap source, int maxLength) {
        try {
            if (source.getWidth() <= maxLength && source.getHeight() <= maxLength) return source;

            int targetWidth, targetHeight;
            double aspectRatio = (double) source.getHeight() / (double) source.getWidth();

            if (source.getWidth() > source.getHeight()) {
                targetWidth = maxLength;
                targetHeight = (int) (targetWidth * aspectRatio);
            } else {
                targetHeight = maxLength;
                targetWidth = (int) (targetHeight / aspectRatio);
            }

            return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        } catch (Exception e) {
            return source;
        }
    }

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

    public void abrirGaleria(View view) {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(i);
    }

    public void abrirCamara(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creando el archivo de imagen", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String bitmapToBase64(Bitmap bitmap){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    public void detectarNacionalidad(View view) {
        if (mSelectedImage == null) {
            Toast.makeText(this, "Primero selecciona una imagen", Toast.LENGTH_SHORT).show();
            return;
        }

        String base64 = bitmapToBase64(mSelectedImage);

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-4o-mini");

                JSONArray messages = new JSONArray();

                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are an expert in Ecuadorian facial morphology. " +
                        "Your task is to identify the specific indigenous group from a list that best matches the person's facial features. " +
                        "Be highly selective and precise. Avoid bias towards the last items of the list.");
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

                String listaNacionalidades = "Awá, Chachi, Épera, Tsáchila, Otavalo, Kayambi, Kitu Kara, Panzaleo, Chibuleo, Salasaka, Waranka, Puruhá, Kañari, Saraguro, Achuar, Andoa, Cofán, Siona, Secoya, Shuar, Shiwiar, Waorani, Zápara, Kichwa Amazónico";

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", "AVAILABLE NATIONALITIES:\n" + listaNacionalidades + "\n\n" +
                        "TASK: Analyze ONLY the person's facial features (bone structure, eyes, etc.) in the image. " +
                        "Based ONLY on the face, output the name of the group from the list that has the closest resemblance.");

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

                RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());
                Request request = new Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .post(body).build();

                Response response = client.newCall(request).execute();
                String result = response.body().string();

                JSONObject jsonObject = new JSONObject(result);
                if (jsonObject.has("choices")) {
                    nacionalidadDetectada = jsonObject.getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content").trim();

                    for (String n : listaNacionalidades.split(", ")) {
                        if (nacionalidadDetectada.contains(n)) {
                            nacionalidadDetectada = n;
                            break;
                        }
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Nacionalidad: " + nacionalidadDetectada, Toast.LENGTH_LONG).show();
                        // ACTIVAR EL BOTÓN CONTINUAR AQUÍ
                        btnContinuar.setEnabled(true);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void generarImagenConRostro(View view) {

        if(mSelectedImage == null){
            Toast.makeText(this,"Primero selecciona una imagen",Toast.LENGTH_SHORT).show();
            return;
        }

        if(nacionalidadDetectada.isEmpty()){
            Toast.makeText(this,"Primero detecta la nacionalidad",Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {

            try {

                OkHttpClient client = new OkHttpClient();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                mSelectedImage.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();

                String prompt = "Transform the person in the image into a member of the "
                        + nacionalidadDetectada +
                        " indigenous culture of Ecuador. Keep the same face and identity. "
                        + "Traditional clothing, cultural accessories, realistic photo.";

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", "dall-e-2")
                        .addFormDataPart("prompt", prompt)
                        .addFormDataPart("n", "1")
                        .addFormDataPart("size", "1024x1024")
                        .addFormDataPart("image", "user_image.png",
                                RequestBody.create(MediaType.parse("image/png"), imageBytes))
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.openai.com/v1/images/edits")
                        .addHeader("Authorization", "Bearer " + API_KEY)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                String result = response.body().string();

                Intent intent = new Intent(MainActivity.this, MainActivity2.class);
                intent.putExtra("imagen", result);
                startActivity(intent);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error en la generación", Toast.LENGTH_SHORT).show());
            }

        }).start();
    }
}