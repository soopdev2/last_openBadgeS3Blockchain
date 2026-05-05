/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

/**
 *
 * @author Aldo
 */
import static Utility.Utils.config;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GroqUtil {

    public static final String API_KEY = config.getString("groq_key");
//    private static final String API_KEY
//            = ConfigLoader.get("GROQ_API_KEY");

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static String callGroqAPI(String prompt) throws IOException {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", "llama-3.1-8b-instant");
        payload.addProperty("temperature", 0);
        payload.addProperty("max_tokens", 1024);

        JsonArray messages = new JsonArray();

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        messages.add(message);
        payload.add("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input);
        }

        int status = conn.getResponseCode();

        InputStream stream = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        if (status < 200 || status >= 300) {
            throw new IOException("Errore HTTP " + status + ": " + response);
        }

        return extractContentFromJson(response.toString());
    }

    public static String extractContentFromJson(String jsonResponse) {
        JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

        JsonArray choices = root.getAsJsonArray("choices");

        if (choices == null || choices.size() == 0) {
            return "{}";
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();

        JsonObject message = firstChoice.getAsJsonObject("message");

        if (message == null || !message.has("content")) {
            return "{}";
        }

        return message.get("content").getAsString().trim();
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\") // escape backslash
                .replace("\"", "\\\"") // escape quote doppia
                .replace("\n", "\\n") // escape newline
                .replace("\r", "\\r");    // escape carriage return, se presenti
    }

//    private static String extractContentFromJson(String json) {
//        int index = json.indexOf("\"content\":\"");
//        if (index == -1) {
//            return "Nessuna risposta trovata.";
//        }
//
//        String partial = json.substring(index + 11);
//        return partial.split("\"")[0].replace("\\n", "\n").replace("\\\"", "\"");
//    }
    public static String callGroqForSQL(String userRequest) throws IOException {
        String prompt = """
    Sei un assistente SQL. Ricevi una richiesta in italiano e restituisci **solo una query SELECT valida** su tabelle chiamate 'vendite' e 'clienti'.
    Non usare UPDATE, DELETE, INSERT o altri comandi.
    Domanda: %s
    Rispondi solo con la query.
    """.formatted(escapeJson(userRequest));

        return callGroqAPI(prompt);
    }
}
