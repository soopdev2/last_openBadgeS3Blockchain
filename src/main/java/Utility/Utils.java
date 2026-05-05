/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import java.io.InputStream;
import java.util.logging.Logger;

import com.itextpdf.barcodes.BarcodeQRCode;
import static com.itextpdf.kernel.colors.ColorConstants.BLACK;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import static java.lang.Math.toRadians;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

/**
 *
 * @author Salvatore
 */
public class Utils {

    public static final ResourceBundle config = ResourceBundle.getBundle("conf.config");

    public static String generateSalt(int length) {
        byte[] salt = new byte[length];
        new SecureRandom().nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    public static String calculateSha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String calculateSha256Hex(String text) throws Exception {
        return calculateSha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calcola l'hash SHA-256 dei dati forniti e lo restituisce in formato
     * esadecimale.
     */
    private static String calculateSha256HashHex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Errore nell'hashing SHA-256", e);
        }
    }

    /**
     * Calcola l'hash dell'Assertion in formato JSON.
     */
    public static String calculateAssertionHash(String json) {
        return calculateSha256HashHex(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Esegue l'hashing di un'email secondo lo standard Open Badges
     * (sha256$hash).
     *
     * @param email
     * @param salt
     * @return
     */
    public static String hashRecipientEmail(String email, String salt) {
        try {
            String combined = email.trim().toLowerCase() + salt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "sha256$" + hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'hashing dell'email", e);
        }
    }

    public static LocalDateTime calcolaScadenza(String scadenza) {
        scadenza = scadenza.trim().toLowerCase();

        String valoreNumerico = scadenza.replaceAll("[^0-9]", "");
        String tipo = scadenza.replaceAll("[0-9]", "");

        int valore = Integer.parseInt(valoreNumerico);
        LocalDateTime now = LocalDateTime.now();

        switch (tipo) {
            case "m" -> {
                return now.plusMinutes(valore);
            }
            case "mo" -> {
                return now.plusMonths(valore);
            }
            case "y" -> {
                return now.plusYears(valore);
            }
            default ->
                throw new IllegalArgumentException("Formato scadenza non valido: " + scadenza);
        }
    }

    public static Integer tryParseInt(String param) {
        try {
            return Integer.valueOf(param);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static Long tryParseLong(String param) {
        try {
            return Long.valueOf(param);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String sanitizeInputString(String input) {
        if (input == null) {
            return null;
        }

        // Normalize unicode
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);

        s = s.replaceAll("[\\p{Cntrl}]", "");

        s = s.replaceAll("\\p{C}", "");

        s = s.replaceAll("[<>\"'`{}\\[\\]|\\\\;$]", "");

        s = s.trim().replaceAll("\\s+", " ");

        return s;
    }

    public Map<String, Object> estraiDatiDaFile(String base64File) throws Exception {

        File file = null;

        try {

            String base64Data = base64File;
            String extension = ".tmp";

            if (base64File.contains(",")) {
                String header = base64File.substring(0, base64File.indexOf(","));
                base64Data = base64File.substring(base64File.indexOf(",") + 1);

                if (header.contains("pdf")) {
                    extension = ".pdf";
                } else if (header.contains("png")) {
                    extension = ".png";
                } else if (header.contains("jpeg") || header.contains("jpg")) {
                    extension = ".jpg";
                }
            }
            
            // (1) BASE64 → FILE TEMP
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            file = File.createTempFile("upload_", extension);
            Files.write(file.toPath(), fileBytes);

            String fileName = file.getName().toLowerCase();

            // (2) OCR
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("C:/tesseract");
            tesseract.setLanguage("ita");

            StringBuilder textBuilder = new StringBuilder();

            boolean isPdf = fileName.endsWith(".pdf");

            if (isPdf) {
                try (PDDocument document = PDDocument.load(file)) {
                    PDFRenderer renderer = new PDFRenderer(document);

                    int maxPages = Math.min(document.getNumberOfPages(), 2);

                    for (int i = 0; i < maxPages; i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, 200);
                        textBuilder.append(tesseract.doOCR(image)).append("\n");
                    }
                }
            } else {
                textBuilder.append(tesseract.doOCR(file));
            }

            String cleanText = textBuilder.toString()
                    .replaceAll("\\s+", " ")
                    .trim();

            if (cleanText.length() > 1200) {
                cleanText = cleanText.substring(0, 1200);
            }

            // (3) PROMPT AI
            String escapedText = cleanText
                    .replace("\"", "\\\"")
                    .replace("\n", " ");

            String prompt = """
Rispondi SOLO con JSON valido.

{
  "badgeName": "",
  "badgeDescription": "",
  "user": {
    "nome": "",
    "cognome": "",
    "email": "",
    "azienda": ""
  },
  "criteriaPoints": [
    {
      "titolo": "",
      "valore": ""
    }
  ]
}

TESTO:
%s
""".formatted(escapedText);

            String groqRawResult = GroqUtil.callGroqAPI(prompt);

            String cleaned = cleanGroqJson(groqRawResult);

            if ("INVALID".equals(cleaned)) {
                throw new Exception("Risposta AI non valida");
            }

            JsonObject parsed = JsonParser.parseString(cleaned).getAsJsonObject();

            // BADGE
            Map<String, Object> badge = new HashMap<>();
            badge.put("nome", parsed.has("badgeName") ? parsed.get("badgeName").getAsString() : "Badge");
            badge.put("descrizione", parsed.has("badgeDescription") ? parsed.get("badgeDescription").getAsString() : "");

            // MITTENTE (fallback minimale)
            Map<String, Object> mittente = new HashMap<>();
            mittente.put("nome", "Sistema automatico");
            mittente.put("url", "");

            // DESTINATARIO
            JsonObject user = parsed.has("user") ? parsed.getAsJsonObject("user") : new JsonObject();

            Map<String, Object> destinatario = new HashMap<>();
            destinatario.put("nome", getSafe(user, "nome"));
            destinatario.put("cognome", getSafe(user, "cognome"));
            destinatario.put("email", getSafe(user, "email"));

            // CRITERI
            List<Map<String, Object>> criteriaList = new ArrayList<>();

            if (parsed.has("criteriaPoints")) {
                JsonArray arr = parsed.getAsJsonArray("criteriaPoints");

                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();

                    Map<String, Object> c = new HashMap<>();
                    c.put("titolo", getSafe(obj, "titolo"));
                    c.put("valore", getSafe(obj, "valore"));

                    criteriaList.add(c);
                }
            }

            Map<String, Object> criteri = new HashMap<>();
            criteri.put("items", criteriaList);

            Map<String, Object> result = new HashMap<>();
            result.put("badge", badge);
            result.put("mittente", mittente);
            result.put("destinatario", destinatario);
            result.put("criteri", criteri);

            return result;

        } finally {
            if (file != null && file.exists()) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    private String getSafe(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "";
    }

    /**
     * Isola il contenuto JSON eliminando eventuale testo descrittivo dell'IA.
     */
    private String cleanGroqJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "INVALID";
        }

        try {
            String cleaned = response.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");

            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonParser.parseString(cleaned);

            return cleaned;

        } catch (Exception e) {
            System.err.println("JSON Groq non valido: " + response);
            return "INVALID";
        }
    }

    public String generaBase64ConQR(String base64Content, String assertionUrl) {
        if (base64Content.contains(",")) {
            base64Content = base64Content.split(",")[1];
        }
        byte[] inputBytes = Base64.getDecoder().decode(base64Content);

        try (ByteArrayInputStream is = new ByteArrayInputStream(inputBytes); ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            PdfReader reader = new PdfReader(is);
            PdfWriter writer = new PdfWriter(os);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);

            BarcodeQRCode barcode = new BarcodeQRCode(assertionUrl);
            printBarcode(barcode, pdfDoc, Logger.getAnonymousLogger());

            pdfDoc.close();

            return Base64.getEncoder().encodeToString(os.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean printBarcode(BarcodeQRCode barcode, PdfDocument pdfDoc, Logger log) {
        try {
            Rectangle rect = barcode.getBarcodeSize();
            PdfFormXObject formXObject = new PdfFormXObject(new Rectangle(rect.getWidth(), rect.getHeight() + 10));
            PdfCanvas pdfCanvas = new PdfCanvas(formXObject, pdfDoc);
            barcode.placeBarcode(pdfCanvas, BLACK);
            Image bCodeImage = new Image(formXObject);
            bCodeImage.setRotationAngle(toRadians(90));
            bCodeImage.setFixedPosition(25, 5); // POSIZIONE 
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                new Canvas(pdfDoc.getPage(i), pdfDoc.getDefaultPageSize()).add(bCodeImage);
            }
            return true;
        } catch (Exception ex) {
            log.severe(printException(ex));
            return false;
        }
    }

    public static String printException(Exception ec1) {
        try {
            return ec1.getStackTrace()[0].getMethodName() + " - " + getStackTrace(ec1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ec1.getMessage();
    }

    public int parseNumero(Object value) {
        if (value == null) {
            return 0;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Valore numerico non valido nella scadenza");
        }
    }
}
