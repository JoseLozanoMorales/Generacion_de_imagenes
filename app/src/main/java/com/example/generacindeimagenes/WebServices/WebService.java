package com.example.generacindeimagenes.WebServices;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebService extends AsyncTask<String, Long, String> {

    private Map<String, String> datos;
    private String url;
    private Context actividad;
    private Asynchtask callback=null;
    ProgressDialog progDailog;

    public  WebService(String urlWebService,Map<String, String> data, Context activity, Asynchtask callback) {
        this.url=urlWebService;
        this.datos=data;
        this.actividad=activity;
        this.callback=callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progDailog = new ProgressDialog(actividad);
        progDailog.setMessage("Procesando...");
        progDailog.setIndeterminate(false);
        progDailog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progDailog.setCancelable(true);
        progDailog.show();
    }

    @Override
    protected String doInBackground(String... params) {
        String result = "";
        try {
            URL url = new URL(this.url);

            // ----- INICIO DE LA CORRECCIÓN -----
            // Usaremos una conexión genérica HttpURLConnection que funciona para http y https
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Lógica condicional MEJORADA
            // Solo si la conexión es HTTPS y la URL NO es de una API pública conocida,
            // aplicamos la configuración permisiva.
            if (conn instanceof HttpsURLConnection && !this.url.contains("api.countrylayer.com") && !this.url.contains("geognos.com")) {
                Log.d("WebService", "URL no estándar HTTPS detectada. Aplicando configuración SSL permisiva.");
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;

                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        }
                };

                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
            // ----- FIN DE LA CORRECCIÓN -----

            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod(params[0]);

            if (params.length > 2) {
                conn.setRequestProperty("Authorization", params[1] + params[2]);
            }

            conn.setDoInput(true);
            int responseCode = conn.getResponseCode();

            InputStream inputStream = (responseCode == HttpURLConnection.HTTP_OK) ?
                    conn.getInputStream() : conn.getErrorStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            result = sb.toString();
            reader.close();
            inputStream.close();
            conn.disconnect();

        } catch (Exception e) {
            Log.e("WebService", "Error en doInBackground: " + e.getMessage(), e);
            result = "ERROR: " + e.getMessage();
        }
        return result;
    }


    @Override
    protected void onPostExecute(String response) {
        super.onPostExecute(response);
        if (progDailog != null && progDailog.isShowing()) {
            progDailog.dismiss();
        }

        if (callback != null) {
            try {
                callback.processFinish(response);
            } catch (JSONException e) {
                Log.e("WebService", "Error al procesar JSON en el callback", e);
            }
        }
    }

    public Map<String, String> getDatos() { return datos; }
    public void setDatos(Map<String, String> datos) { this.datos = datos; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Context getActividad() { return actividad; }
    public void setActividad(Context actividad) { this.actividad = actividad; }
    //La variable xml no se está usando, pero la mantenemos por retrocompatibilidad
    private String xml;
    public String getXml() { return xml; }
    public void setXml(String xml) { this.xml = xml; }
    public ProgressDialog getProgDailog() { return progDailog; }
    public void setProgDailog(ProgressDialog progDailog) { this.progDailog = progDailog; }
}
