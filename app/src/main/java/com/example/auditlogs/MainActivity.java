package com.example.auditlogs;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.auditlogs.BuildConfig;


import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;
    private DatabaseHelper databaseHelper;

    private String lastPrompt = null;
    private String lastResponse = null;
    private String promptSentTime = null;
    private String responseReceivedTime = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        databaseHelper = new DatabaseHelper(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText promptInput = findViewById(R.id.promptEt);
        Button sendPromptBtn = findViewById(R.id.sendBtn);
        Button cancelBtn = findViewById(R.id.cancelPromptBtn);
        Button auditBtn = findViewById(R.id.auditBtn);
        TextView responseTextView = findViewById(R.id.resultTv);

        cancelBtn.setOnClickListener(v -> {
            promptInput.setText("");
            responseTextView.setText("");
        });

        sendPromptBtn.setOnClickListener(v -> {
            String prompt = promptInput.getText().toString();
            if (!prompt.isEmpty()) {
                progressDialog = ProgressDialog.show(this,
                        "Loading",
                        "Please wait...",
                        true);

                promptSentTime = String.valueOf(new Date().getTime());


                new OpenAIAsyncTask(response -> {
                    if(progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    responseTextView.setText(response);

                    // DB Values Setup
                    responseReceivedTime = String.valueOf(new Date().getTime());
                    lastPrompt = prompt;
                    lastResponse = response;
                }).execute(prompt);

            } else {
                Toast.makeText(this, "Enter A Prompt", Toast.LENGTH_SHORT).show();
            }

        });

        auditBtn.setOnClickListener(v -> {
            if (lastPrompt != null && lastResponse != null && promptSentTime != null && responseReceivedTime != null) {
                databaseHelper.insertAuditPrompt(promptSentTime, lastPrompt);
                databaseHelper.insertResponse(responseReceivedTime, lastResponse);
                Toast.makeText(this, "Audit information saved.", Toast.LENGTH_LONG).show();

                lastPrompt = null;
                lastResponse = null;
                promptSentTime = null;
                responseReceivedTime = null;
            } else {
                Toast.makeText(this, "No prompt/response to save.", Toast.LENGTH_SHORT).show();
            }
        });

        auditBtn.setOnLongClickListener(v -> {
            showAuditDataDialog();
            return true;
        });
    }

    private void showAuditDataDialog() {
        List<String> prompts = databaseHelper.getAllAuditPrompts();
        List<String> responses = databaseHelper.getAllResponses();

        StringBuilder dataToShow = new StringBuilder("Prompts:\n");
        for (String prompt : prompts) {
            dataToShow.append(prompt).append("\n");
        }
        dataToShow.append("\nResponses:\n");
        for (String response : responses) {
            dataToShow.append(response).append("\n");
        }

        // Display the data in a dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        System.out.println("============AUDITLOG============");
        System.out.println(dataToShow);
        System.out.println("========================");
        builder.setTitle("Audit Data")
                .setMessage(dataToShow.toString())
                .setPositiveButton("OK", null);
        builder.show();
    }

    private static class OpenAIAsyncTask extends AsyncTask<String, Void, String> {

        public interface ResponseListener {
            void onResponseReceived(String response);
        }

        private final ResponseListener listener;

        public OpenAIAsyncTask(ResponseListener listener) {
            this.listener = listener;
        }

        private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

        @Nullable
        @Override
        protected String doInBackground(@NonNull String... params) {
            String prompt = params[0];
            try {
                URL url = new URL(OPENAI_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");

                String API_KEY = "YOUR_API_KEY";
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("model", "gpt-3.5-turbo");
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "You are a helpful assistant.");

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);

                jsonParam.put("messages", new JSONArray(Arrays.asList(systemMessage, userMessage)));

                OutputStream os = connection.getOutputStream();
                os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();
                return response.toString();

            } catch (Exception e) {
                Log.e("OpenAIAsyncTask", "API Request Error", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            try {

                JSONObject jsonResponse = new JSONObject(result);

                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String content = message.getString("content");

                    if (listener != null) {
                        listener.onResponseReceived(content.trim());
                    }
                }
            } catch (Exception e) {
                Log.e("OpenAIAsyncTask", "Parsing Error from API response", e);
                if (listener != null) {
                    listener.onResponseReceived("Error parsing response: " + e.getMessage());
                }
            }
        }
    }
}