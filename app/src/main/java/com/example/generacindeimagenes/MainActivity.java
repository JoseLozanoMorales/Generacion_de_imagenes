package com.example.generacindeimagenes;
import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.generacindeimagenes.BuildConfig;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnSuccessListener<Text>, OnFailureListener {

    private TextView txtResults;
    private ImageView mImageView;

    private String nacionalidadDetectada = "";

    private static final String API_KEY = com.example.generacindeimagenes.BuildConfig.OPENAI_API_KEY;    private Bitmap mSelectedImage;
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

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), result.getData().getData());
                            mImageView.setImageBitmap(mSelectedImage);
                        } catch (IOException e) {
                            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getExtras() != null) {
                        mSelectedImage = (Bitmap) result.getData().getExtras().get("data");
                        mImageView.setImageBitmap(mSelectedImage);
                    }
                });
    }

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        StringBuilder resultados = new StringBuilder();
        if (blocks.isEmpty()) {
            resultados.append(getString(R.string.no_text_found));
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
        txtResults.setText(resultados.toString());
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        txtResults.setText(R.string.error_processing_image);
    }

    public void abrirGaleria(View view) {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(i);
    }

    public void abrirCamara(View view) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private String bitmapToBase64(Bitmap bitmap){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
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

                // 1. Mensaje de Sistema: Foco exclusivo en facciones faciales
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content",
                        "Eres un clasificador. Tu tarea es seleccionar OBLIGATORIAMENTE una nacionalidad "
                                + "de la lista proporcionada. Analiza los rasgos faciales de la persona en la imagen "
                                + "(estructura ósea, ojos, pómulos, forma del rostro). "
                                + "Debes escoger SIEMPRE la nacionalidad más parecida de la lista. "
                                + "Nunca respondas que no puedes identificar. "
                                + "Nunca des explicaciones. "
                                + "Responde SOLO con el nombre exacto de una nacionalidad de la lista.");
                messages.put(systemMessage);

                // 2. Ejemplos de "entrenamiento" para evitar que repita siempre lo mismo
                JSONObject userEx1 = new JSONObject();
                userEx1.put("role", "user");
                userEx1.put("content", "Identify the group from the list based on the face.");
                messages.put(userEx1);
                JSONObject assistantEx1 = new JSONObject();
                assistantEx1.put("role", "assistant");
                assistantEx1.put("content", "Otavalo");
                messages.put(assistantEx1);

                // 3. Mensaje Real
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                JSONArray content = new JSONArray();

                String listaNacionalidades = "Awá, Chachi, Épera, Tsáchila, Otavalo, Kayambi, Kitu Kara, Panzaleo, Chibuleo, Salasaka, Waranka, Puruhá, Kañari, Saraguro, Achuar, Andoa, Cofán, Siona, Secoya, Shuar, Shiwiar, Waorani, Zápara, Kichwa Amazónico";

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                // Estructura invertida con foco solo en la cara
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
                jsonBody.put("temperature", 0.3); // Baja temperatura para más precisión

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

                    // Limpieza de seguridad
                    for (String n : listaNacionalidades.split(", ")) {
                        if (nacionalidadDetectada.contains(n)) {
                            nacionalidadDetectada = n;
                            break;
                        }
                    }

                    runOnUiThread(() -> Toast.makeText(this, "Nacionalidad: " + nacionalidadDetectada, Toast.LENGTH_LONG).show());
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

                // OpenAI Edits requiere formato PNG y la imagen debe ser cuadrada
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                mSelectedImage.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();

                String prompt = "Transform the person in the image into a member of the "
                        + nacionalidadDetectada +
                        " indigenous culture of Ecuador. Keep the same face and identity. "
                        + "Traditional clothing, cultural accessories, realistic photo.";

                // Construcción del MultipartBody
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
