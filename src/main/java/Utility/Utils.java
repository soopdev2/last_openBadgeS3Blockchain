/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
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
import static java.lang.Math.toRadians;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

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

        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);

        s = s.replaceAll("[\\p{Cntrl}]", "");

        s = s.replaceAll("\\p{C}", "");

        s = s.replaceAll("[<>\"'`{}\\[\\]|\\\\;$]", "");

        s = s.trim().replaceAll("\\s+", " ");

        return s;
    }

    public Map<String, Object> estraiDatiDaFile(String base64File, String estensione) throws Exception {
        File file = null;

        try {
            String base64Data = base64File;
            String extension = ".tmp";

            if (base64File.contains(",")) {
                String[] parts = base64File.split(",");
                String header = parts[0].toLowerCase();
                base64Data = parts[1];

                if (header.contains("pdf")) {
                    extension = ".pdf";
                } else if (header.contains("png")) {
                    extension = ".png";
                } else if (header.contains("jp")) {
                    extension = ".jpg";
                }
            }

            if (extension.equals(".tmp") && estensione != null && !estensione.isBlank()) {
                extension = estensione.startsWith(".") ? estensione : "." + estensione;
            }

            byte[] fileBytes = Base64.getDecoder().decode(base64Data);
            file = File.createTempFile("upload_", extension);
            Files.write(file.toPath(), fileBytes);

            StringBuilder textBuilder = new StringBuilder();

            ITesseract tesseract = new Tesseract();
            String tesseract_path = config.getString("tesseract_path");
            tesseract.setDatapath(tesseract_path);
            tesseract.setLanguage("ita");

            if (extension.equalsIgnoreCase(".pdf")) {
                try (PDDocument document = Loader.loadPDF(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String pdfText = stripper.getText(document);

                    if (pdfText != null && pdfText.trim().length() > 50) {
                        textBuilder.append(pdfText);
                    } else {
                        PDFRenderer renderer = new PDFRenderer(document);
                        int pages = Math.min(document.getNumberOfPages(), 2);

                        for (int i = 0; i < pages; i++) {
                            textBuilder.append(
                                    tesseract.doOCR(renderer.renderImageWithDPI(i, 200))
                            ).append("\n");
                        }
                    }
                }
            } else {
                textBuilder.append(tesseract.doOCR(file));
            }

            String cleanText = textBuilder.toString()
                    .replaceAll("\\s+", " ")
                    .replace("Ã\u00A0", "à")
                    .trim();

            if (cleanText.length() > 2500) {
                cleanText = cleanText.substring(0, 2500);
            }

            String escapedText = cleanText
                    .replace("\"", "\\\"")
                    .replace("\n", " ");

            String prompt = """
    Rispondi SOLO con JSON valido. Estrai i dati con precisione chirurgica.
                            
    ### REGOLE PER IL BADGE:
    1. badge_name: NON scrivere "Badge". Genera un titolo professionale basato sul contenuto (es. "Certificazione Sviluppatore Java", "Esperto Sicurezza sul Lavoro").
    2. badge_description: Genera una descrizione dettagliata di cosa rappresenta questo documento e quali competenze valida.
    
    ### REGOLE PER USER:
    1. Estrai Nome, Cognome, Data Nascita, CF, Ruolo, Email (1 sola), Azienda, Telefono.
    2. Luogo Nascita e Indirizzo vanno in 'altri_dati' come chiavi singole.
    
    ### REGOLE PER CRITERI (COMPETENZE):
   CRITERI: Solo requisiti tecnici/competenze. 'valore' mai vuoto.
   TROVA E RIPORTA I VALORI CHE DESCRIVONO LA COMPETENZA.
   SE SPECIFICATO INSERISCI ANCHE LA VALUTAZIONE O SEZIONE.
                            

    ### SCHEMA JSON:
    {
    "badge_name": "",
     "badge_description": "",
      "user": { 
        "nome": "", "cognome": "", "data_nascita": "", "codice_fiscale": "", 
        "ruolo": "", "email": "", "telefono": "", "azienda": "",
        "altri_dati": { "luogo_nascita": "", "indirizzo": "" }
      },
      "criteriaPoints": [{ "titolo": "", "valore": "" }]
    }
    
    TESTO DA ANALIZZARE: %s
    """.formatted(escapedText);

            String cleaned = "INVALID";

            for (int i = 0; i < 2; i++) {
                String raw = GroqUtil.callGroqAPI(prompt);
                cleaned = cleanGroqJson(raw);

                if (!"INVALID".equals(cleaned)) {
                    break;
                }
            }

            if ("INVALID".equals(cleaned)) {
                return fallbackResponse(cleanText);
            }

            JsonObject parsed = JsonParser.parseString(cleaned).getAsJsonObject();
            JsonObject user = parsed.has("user") ? parsed.getAsJsonObject("user") : new JsonObject();

            Map<String, Object> destinatario = new LinkedHashMap<>();
            destinatario.put("nome", getSafe(user, "nome"));
            destinatario.put("cognome", getSafe(user, "cognome"));
            destinatario.put("email",
                    getSafe(user, "email").isEmpty() ? extractEmail(cleanText) : getSafe(user, "email")
            );
            destinatario.put("telefono", getSafe(user, "telefono"));
            destinatario.put("dataNascita", getSafe(user, "data_nascita"));
            destinatario.put("codiceFiscale", getSafe(user, "codice_fiscale"));
            destinatario.put("ruolo", getSafe(user, "ruolo"));
            destinatario.put("azienda", getSafe(user, "azienda"));

            if (user.has("altri_dati") && user.get("altri_dati").isJsonObject()) {
                JsonObject extra = user.getAsJsonObject("altri_dati");

                for (Map.Entry<String, JsonElement> entry : extra.entrySet()) {
                    String key = entry.getKey().replace("_", " ");
                    destinatario.put(key, entry.getValue().getAsString());
                }
            }

            List<Map<String, String>> criteriaList = new ArrayList<>();

            if (parsed.has("criteriaPoints") && parsed.get("criteriaPoints").isJsonArray()) {
                parsed.getAsJsonArray("criteriaPoints").forEach(el -> {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();

                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("titolo", String.valueOf(getSafe(obj, "titolo")));
                        item.put("valore", String.valueOf(getSafe(obj, "valore")));

                        criteriaList.add(item);
                    }
                });
            }

            String extractedName = getSafe(parsed, "badge_name");
            String extractedDesc = getSafe(parsed, "badge_description");

            if (extractedName == null || extractedName.isBlank()) {
                extractedName = "Attestato Formazione";
            }

            if (extractedDesc == null || extractedDesc.isBlank()) {
                extractedDesc = "Documento che certifica competenze professionali acquisite e validate.";
            }

            return Map.of(
                    "badge", Map.of(
                            "badge_name", extractedName,
                            "badge_description", extractedDesc,
                            "logo", "https://openbagetest.s3.eu-central-1.amazonaws.com/logo/logoOpenBadge.png"
                    ),
                    "mittente", Map.of("nome", "SmartOOP", "url", "https://smartoop.it/"),
                    "destinatario", destinatario,
                    "criteri", Map.of("items", criteriaList)
            );

        } finally {
            if (file != null && file.exists()) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    private Map<String, Object> fallbackResponse(String text) {
        return Map.of(
                "badge", Map.of("nome", "Documento elaborato", "descrizione", text.substring(0, Math.min(200, text.length()))),
                "mittente", Map.of("nome", "Sistema automatico", "url", ""),
                "destinatario", Map.of("email", extractEmail(text)),
                "criteri", Map.of("items", List.of())
        );
    }

    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return "";
    }

    private String getSafe(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "";
    }

    /**
     * Isola il contenuto JSON eliminando eventuale testo descrittivo dell'IA.
     */
    private String cleanGroqJson(String raw) {
        if (raw == null) {
            return "INVALID";
        }

        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            String json = raw.substring(start, end + 1);

            try {
                JsonParser.parseString(json);
                return json;
            } catch (Exception e) {
                return "INVALID";
            }
        }

        return "INVALID";
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
