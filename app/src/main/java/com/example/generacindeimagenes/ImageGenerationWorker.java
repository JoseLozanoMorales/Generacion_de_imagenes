package com.example.generacindeimagenes;

import android.app.PendingIntent;
import android.content.Intent;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageGenerationWorker extends Worker {

    public ImageGenerationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        try {

            String apiKey = BuildConfig.OPENAI_API_KEY;

            String nacionalidad = getInputData().getString("nacionalidad");

            String path = getInputData().getString("imagen_path");

            Bitmap bitmap = BitmapFactory.decodeFile(path);

            if (bitmap == null) {
                android.util.Log.e("WORKER", "No se pudo cargar la imagen desde: " + path);
                return Result.failure();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] imageBytes = baos.toByteArray();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            String prompt =
                    "Usa la imagen proporcionada como referencia EXACTA del rostro. "
                            + "Debe ser claramente la misma persona, misma identidad. "
                            + "Mantén exactamente las mismas facciones faciales: ojos, nariz, boca y forma del rostro. "
                            + "Genera una imagen de CUERPO COMPLETO. "
                            + "La persona debe aparecer como miembro de la nacionalidad ecuatoriana "
                            + nacionalidad + ". "
                            + "Vestimenta tradicional auténtica, accesorios culturales reales, pintura facial tradicional en caso de que aplique. "
                            + "Ambientado en un entorno natural relacionado con su cultura (selva amazónica, montaña andina o comunidad indígena). "
                            + "Estilo fotorrealista, iluminación natural, alta calidad";

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "gpt-image-1.5")
                    .addFormDataPart("prompt", prompt)
                    .addFormDataPart("size", "1024x1024")
                    .addFormDataPart("quality", "high")
                    .addFormDataPart(
                            "image",
                            "user.png",
                            RequestBody.create(imageBytes, MediaType.parse("image/png"))

                    )
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/images/edits")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();
            String result = response.body().string();
            if (!response.isSuccessful()) {
                android.util.Log.e("OPENAI_ERROR", result);
                mostrarNotificacionError();
                return Result.failure();
            }
            android.util.Log.d("OPENAI_RESPONSE", result);


            JSONObject jsonObject = new JSONObject(result);

            if (jsonObject.has("data")) {

                String base64Image = jsonObject
                        .getJSONArray("data")
                        .getJSONObject(0)
                        .getString("b64_json");

                byte[] decoded = Base64.decode(base64Image, Base64.DEFAULT);
                Bitmap generatedBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                ImageHolder.generatedImage = generatedBitmap;
                mostrarNotificacion();

                return Result.success();
            }

        } catch (Exception e) {
            e.printStackTrace();

            android.util.Log.e("OPENAI_ERROR", e.toString());

            mostrarNotificacionError();

            return Result.failure();
        }

        return Result.failure();
    }

    private void mostrarNotificacion() {

        Context context = getApplicationContext();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "imagen_generada";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "Imagen generada",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setContentTitle("Imagen generada")
                        .setContentText("Toca para ver tu imagen")
                        .setSmallIcon(android.R.drawable.ic_menu_gallery)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        manager.notify(1, builder.build());
    }
    private void mostrarNotificacionError() {

        Context context = getApplicationContext();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "imagen_error";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "Error de generación",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setContentTitle("Error al generar imagen")
                        .setContentText("Hubo un problema al generar la imagen. Inténtalo nuevamente.")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        manager.notify(2, builder.build());
    }
}
