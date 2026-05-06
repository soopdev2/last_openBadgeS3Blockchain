/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Services.OpenBadges;

import Entity.InfoTrack;
import Entity.Transazione;
import Utility.JpaUtil;
import Utility.Utils;
import static Utility.Utils.config;
import static Utility.Utils.generateSalt;
import static Utility.Utils.hashRecipientEmail;
import static Utility.Utils.tryParseInt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.NoOpProcessor;
import org.web3j.utils.Numeric;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 *
 * @author Salvatore
 */
public class OpenBadgeService {

    private static final String ACCESS_KEY = config.getString("access_key");
    private static final String SECRET_KEY = config.getString("secret_key");
    private static final String AWS_REGION = config.getString("aws_region");
    private static final String BUCKET_NAME = config.getString("bucket_name");
    private static final String BASE_URL = "https://" + BUCKET_NAME + ".s3." + AWS_REGION + ".amazonaws.com/";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private S3Client createS3Client() {
        return S3Client.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
                ))
                .build();
    }

    public Map<String, Object> genera(
            Map<String, Object> badge,
            Map<String, Object> mittente,
            Map<String, Object> destinatario,
            Map<String, Object> criteri,
            String base64File,
            String estensione
    ) {

        // (1) Infotrack
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("SERVICE API POST /openbadge/genera - Generazione badge service");

        Map<String, Object> response = new LinkedHashMap<>();

        try {

            Utils utils = new Utils();

            Map<String, Object> parsedData = null;

            boolean needFileFallback
                    = isEmptyMap(badge)
                    || isEmptyMap(mittente)
                    || isEmptyMap(destinatario)
                    || isEmptyMap(criteri);

            if (needFileFallback) {

                if (base64File == null || base64File.isBlank()) {
                    throw new IllegalArgumentException("Dati mancanti e file base64 non fornito");
                }

                parsedData = utils.estraiDatiDaFile(
                        base64File,
                        (estensione != null && !estensione.isBlank()) ? estensione : ".pdf"
                );
            }
            badge = mergeIfMissing(badge, parsedData != null ? parsedData.get("badge") : null);
            mittente = mergeIfMissing(mittente, parsedData != null ? parsedData.get("mittente") : null);
            destinatario = mergeIfMissing(destinatario, parsedData != null ? parsedData.get("destinatario") : null);
            criteri = mergeIfMissing(criteri, parsedData != null ? parsedData.get("criteri") : null);

            // (3) VALIDAZIONE BASE
            if (badge == null || mittente == null || destinatario == null || criteri == null) {
                throw new IllegalArgumentException("Impossibile recuperare tutti i dati necessari");
            }

            // (4) CONFIG CRITERIA LIMIT
            String MAX_CRITERIA_COUNT_STRING = config.getString("MAX_CRITERIA_COUNT");
            final int MAX_CRITERIA_COUNT = tryParseInt(MAX_CRITERIA_COUNT_STRING);

            if (MAX_CRITERIA_COUNT <= 0) {
                throw new IllegalStateException("Configurazione MAX_CRITERIA_COUNT non valida");
            }

            List<Map<String, Object>> criteriaList;
            if (criteri.containsKey("items")) {
                criteriaList = (List<Map<String, Object>>) criteri.get("items");
            } else {
                criteriaList = new ArrayList<>();
                criteriaList.add(criteri);
            }

            if (criteriaList.size() > MAX_CRITERIA_COUNT) {
                throw new IllegalArgumentException("Superato limite massimo criteri");
            }

            // (5) DATI BADGE
            String badgeName = (String) badge.get("badge_name");
            String badgeDescription = (String) badge.get("badge_description");
            String logoUrl = (String) badge.getOrDefault("logo", "https://openbagetest.s3.eu-central-1.amazonaws.com/logo/logoOpenBadge.png");

            if (badgeName == null || badgeName.isBlank() || badgeName.equalsIgnoreCase("badge")) {
                badgeName = null;
            }

            if (badgeDescription == null || badgeDescription.isBlank()
                    || badgeDescription.toLowerCase().contains("descrizione non disponibile")) {
                badgeDescription = null;
            }

            if (badgeName == null) {
                badgeName = "Certificazione Professionale";
            }

            if (badgeDescription == null) {
                badgeDescription = "Certificazione che attesta competenze professionali acquisite e validate nel contesto formativo o lavorativo.";
            }

            // (6) DATI UTENTI
            String mittenteNome = (String) mittente.get("nome");
            String mittenteUrl = (String) mittente.getOrDefault("url", "");
            String emailDestinatario = (String) destinatario.get("email");

            if (emailDestinatario == null || emailDestinatario.isBlank()) {
                throw new IllegalArgumentException("Email destinatario mancante");
            }

            // (8) ISSUER
            String uniqueIdIssuer = UUID.randomUUID().toString().substring(0, 10);

            Map<String, Object> issuer = Map.of(
                    "@context", "https://w3id.org/openbadges/v2",
                    "id", BASE_URL + "issuer-" + uniqueIdIssuer + ".json",
                    "type", "Issuer",
                    "name", mittenteNome,
                    "url", mittenteUrl
            );

            String issuerJson = gson.toJson(issuer);

            // (9) CRITERIA
            String uniqueIdCriteria = UUID.randomUUID().toString().substring(0, 10);
            String criteriaFileName = "criteria-" + uniqueIdCriteria + ".html";
            String criteriaUrl = BASE_URL + criteriaFileName;

            String titlesNarrative = criteriaList.stream()
                    .map(map -> "\n• " + map.getOrDefault("titolo", "Criterio non specificato"))
                    .collect(Collectors.joining());

            String narrativeText = "I criteri soddisfatti sono:" + titlesNarrative
                    + "\n\nPer visualizzare i dettagli completi, visita: " + criteriaUrl;

            String criteriaHtml = createCriteriaHtml(badgeName, criteriaList, destinatario);

            // (10) BADGE CLASS
            String uniqueIdBadge = UUID.randomUUID().toString().substring(0, 10);

            Map<String, Object> badgeJsonMap = new HashMap<>();
            badgeJsonMap.put("@context", "https://w3id.org/openbadges/v2");
            badgeJsonMap.put("id", BASE_URL + "badge-" + uniqueIdBadge + ".json");
            badgeJsonMap.put("type", "BadgeClass");
            badgeJsonMap.put("name", badgeName);
            badgeJsonMap.put("description", badgeDescription);
            badgeJsonMap.put("image", logoUrl);

            badgeJsonMap.put("criteria", Map.of(
                    "id", criteriaUrl,
                    "type", "Criteria",
                    "narrative", narrativeText
            ));

            badgeJsonMap.put("issuer", BASE_URL + "issuer-" + uniqueIdIssuer + ".json");

            String badgeJson = gson.toJson(badgeJsonMap);

            // SCADENZA
            String dataScadenzaISO = calcolaScadenzaISO(badge);

            // (11) ASSERTION
            String uniqueIdAssertion = UUID.randomUUID().toString().substring(0, 10);
            String assertionFileName = "assertion-" + uniqueIdAssertion + ".json";
            String assertionUrl = BASE_URL + assertionFileName;

            String salt = generateSalt(16);
            String hashedIdentity = hashRecipientEmail(emailDestinatario, salt);

            Map<String, Object> assertion = new HashMap<>();
            assertion.put("@context", "https://w3id.org/openbadges/v2");
            assertion.put("id", assertionUrl);
            assertion.put("type", "Assertion");

            assertion.put("recipient", Map.of(
                    "type", "email",
                    "hashed", true,
                    "identity", hashedIdentity,
                    "salt", salt
            ));

            assertion.put("issuedOn", LocalDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

            assertion.put("badge", BASE_URL + "badge-" + uniqueIdBadge + ".json");
            assertion.put("verification", Map.of(
                    "type", "hosted",
                    "url", assertionUrl
            ));

            if (dataScadenzaISO != null) {
                assertion.put("expires", dataScadenzaISO);
            }

            String assertionJson = gson.toJson(assertion);

            // (12) S3
            S3Client s3 = createS3Client();
            uploadToS3(s3, "issuer-" + uniqueIdIssuer + ".json", issuerJson, infoTrack);
            uploadToS3(s3, criteriaFileName, criteriaHtml, infoTrack);
            uploadToS3(s3, "badge-" + uniqueIdBadge + ".json", badgeJson, infoTrack);
            uploadToS3(s3, assertionFileName, assertionJson, infoTrack);

            // (13) HASH
            String fileUrl = BASE_URL + assertionFileName;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
            HttpResponse<InputStream> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException("Errore download assertion");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = httpResponse.body()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            String hashHex = Numeric.toHexStringNoPrefix(digest.digest());
            String txHash = inviaHashSuBlockchain(hashHex, infoTrack);

            // DB
            Transazione transazione = new Transazione();
            transazione.setTxHash(txHash);
            transazione.setHashHex("0x" + hashHex);
            transazione.setEmail(emailDestinatario);

            JpaUtil.saveTxHashAndHashHexOnDb(transazione);

            // QR
            Utils utilsHelper = new Utils();
            String base64WithQr = null;

            if (base64File != null && !base64File.isBlank()) {
                base64WithQr = utilsHelper.generaBase64ConQR(base64File, assertionUrl);
            }

            response.put("stato", "success");
            response.put("messaggio", "✅ Badge generato con successo.");

            response.put("data_rilascio",
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            );

            String expiresFormatted = null;

            if (dataScadenzaISO != null) {
                expiresFormatted = Instant.parse(dataScadenzaISO)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }

            response.put("data_scadenza", expiresFormatted);

            response.put("badge_links", List.of(
                    BASE_URL + "issuer-" + uniqueIdIssuer + ".json",
                    BASE_URL + "badge-" + uniqueIdBadge + ".json",
                    criteriaUrl,
                    assertionUrl
            ));

            response.put("logo_url", logoUrl);

            response.put("email", emailDestinatario);
            response.put("assertion_hash", hashHex);
            response.put("tx_hash", txHash);

            response.put("base64", base64File);
            response.put("base64_qr", base64WithQr);
            infoTrack.setDescrizione("SUCCESSO - openbadge generato correttamente");
            JpaUtil.salvaInfoTrack(infoTrack);

            return response;

        } catch (Exception e) {

            return buildErrorResponse(
                    infoTrack,
                    e,
                    "Errore durante generazione openbadge"
            );
        }
    }

    private String createCriteriaHtml(String badgeName,
            List<Map<String, Object>> criteria,
            Map<String, Object> user) {
        StringBuilder sb = new StringBuilder();

        sb.append("<html><head><style>")
                .append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #333; line-height: 1.6; padding: 20px; background-color: #f9f9f9; }")
                .append("h1 { color: #0066CC; border-bottom: 2px solid #3498db; padding-bottom: 10px; text-transform: uppercase; font-size: 24px; }")
                .append("h2 { color: #0066CC; margin-top: 30px; font-size: 18px; }")
                .append("table { border-collapse: separate; border-spacing: 0; width: 100%; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 20px; }")
                .append("th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #edf2f7; }")
                .append("th { background-color: #0066CC; color: white; font-weight: 600; text-transform: uppercase; font-size: 13px; }")
                .append("tr:last-child td { border-bottom: none; }")
                .append("tr:nth-child(even) { background-color: #f8fbff; }")
                .append(".label { font-weight: bold; color: #7f8c8d; width: 30%; }")
                .append("</style></head><body>");

        sb.append("<h1>").append(badgeName).append("</h1>");

        sb.append("<h2>Dettagli Utente</h2><table>");
        sb.append("<thead><tr><th>Proprietà</th><th>Valore</th></tr></thead><tbody>");
        user.forEach((k, v)
                -> sb.append("<tr><td class='label'>").append(k)
                        .append("</td><td>").append(v != null ? v : "-")
                        .append("</td></tr>")
        );
        sb.append("</tbody></table>");

        sb.append("<h2>Criteri di Valutazione</h2><table>");
        sb.append("<thead><tr><th>Criterio</th><th>Stato / Valore</th></tr></thead><tbody>");
        for (Map<String, Object> c : criteria) {
            sb.append("<tr><td class='label'>")
                    .append(c.getOrDefault("titolo", "N/D"))
                    .append("</td><td>")
                    .append(c.getOrDefault("valore", "-"))
                    .append("</td></tr>");
        }
        sb.append("</tbody></table></body></html>");

        return sb.toString();
    }

    private String inviaHashSuBlockchain(String hashHex, InfoTrack infoTrack) {
        String nodeUrl = config.getString("aws_peer");
        if (nodeUrl == null || nodeUrl.isBlank()) {
            nodeUrl = "https://ethereum-sepolia-rpc.publicnode.com";
        }

        String privateKey = config.getString("sepolia_private_key");
        String recipient = config.getString("tx_recipient_address");
        if (recipient == null || recipient.isBlank()) {
            recipient = "0x41a7949d7f7fe6B1FA3271e4325cDCE7de5Fd07a";
        }

        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        try {
            Credentials credentials = Credentials.create(privateKey);
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(100_000);

            String data = "0x" + hashHex;
            RawTransaction rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, recipient, BigInteger.ZERO, data);

            RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId.longValue(), new NoOpProcessor(web3j));
            EthSendTransaction resp = txManager.signAndSend(rawTx);

            if (resp.hasError()) {
                String errorMsg = "ERRORE - 502 - Invio hash sulla blockchain fallito: " + resp.getError().getMessage();
                infoTrack.setDescrizione(errorMsg);
                JpaUtil.salvaInfoTrack(infoTrack);
            }

            String txHash = resp.getTransactionHash();
            if (txHash == null || txHash.isBlank()) {
                String errorMsg = "ERRORE - 502 - Hash inviato ma risposta nulla o vuota dalla blockchain.";
                infoTrack.setDescrizione(errorMsg);
                JpaUtil.salvaInfoTrack(infoTrack);
            }

            infoTrack.setDescrizione("SUCCESSO - 200 - Hash registrato sulla blockchain. TxHash: " + txHash);
            JpaUtil.salvaInfoTrack(infoTrack);

            return txHash;

        } catch (Exception e) {
            String errorMsg = "ERRORE - 502 - Eccezione durante invio hash alla blockchain: " + e.getMessage();
            infoTrack.setDescrizione(errorMsg);
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new RuntimeException(e);
        } finally {
            web3j.shutdown();
        }
    }

    private void uploadToS3(S3Client s3, String fileName, String content, InfoTrack infoTrack) throws Exception {
        try {
            String contentType;
            if (fileName.endsWith(".html")) {
                contentType = "text/html";
            } else if (fileName.endsWith(".json")) {
                contentType = "application/json";
            } else {
                contentType = "application/octet-stream";
            }

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3.putObject(putOb, RequestBody.fromString(content));

            infoTrack.setDescrizione("SUCCESSO - File caricato su S3: " + fileName);
            JpaUtil.salvaInfoTrack(infoTrack);

        } catch (S3Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore S3 durante l'upload di " + fileName + ": " + e.awsErrorDetails().errorMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore durante l'upload su S3 (" + fileName + "): " + e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore di connessione S3 durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore di connessione al servizio S3 (" + fileName + "): " + e.getMessage());

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore generico durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
        }
    }

    private String calcolaScadenzaISO(Map<String, Object> badge) {

        Object scadenzaObj = badge.get("scadenza");

        if (scadenzaObj == null) {
            return null;
        }

        if (!(scadenzaObj instanceof Map)) {
            throw new IllegalArgumentException("Formato scadenza non valido");
        }

        Map<String, Object> scadenza = (Map<String, Object>) scadenzaObj;

        Utils utils = new Utils();

        int anni = utils.parseNumero(scadenza.get("anni"));
        int mesi = utils.parseNumero(scadenza.get("mesi"));
        int giorni = utils.parseNumero(scadenza.get("giorni"));

        if (anni == 0 && mesi == 0 && giorni == 0) {
            throw new IllegalArgumentException("La scadenza deve avere almeno anni, mesi o giorni > 0");
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime dataScadenza = now
                .plusYears(anni)
                .plusMonths(mesi)
                .plusDays(giorni);

        if (dataScadenza.isBefore(now)) {
            throw new IllegalArgumentException("La data di scadenza non può essere nel passato");
        }

        return dataScadenza
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    public Map<String, Object> verifica(Map<String, Object> badge) throws Exception {

        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("SERVICE API POST /openbadge/verifica - Verifica badge service");
        Map<String, Object> response = new LinkedHashMap<>();

        try {

            if (badge == null) {
                throw new IllegalArgumentException("Oggetto badge mancante");
            }

            String email = (String) badge.get("email");
            String inputHashAssertion = (String) badge.get("assertion_hash");
            String txHash = (String) badge.get("tx_hash");

            if (email == null || email.isBlank()
                    || inputHashAssertion == null || inputHashAssertion.isBlank()
                    || txHash == null || txHash.isBlank()) {
                throw new IllegalArgumentException(
                        "Tutti i campi sono obbligatori: email, assertion_hash, tx_hash"
                );
            }

            inputHashAssertion = inputHashAssertion.startsWith("0x")
                    ? inputHashAssertion
                    : "0x" + inputHashAssertion;

            Transazione transazioneDb = JpaUtil.trovaTransazioneByHash(txHash);

            if (transazioneDb == null) {
                throw new Exception("Transazione non trovata nel database");
            }

            String onChainHash;

            try (Web3j web3j = Web3j.build(
                    new HttpService("https://ethereum-sepolia-rpc.publicnode.com")
            )) {

                EthTransaction ethTransaction = web3j.ethGetTransactionByHash(txHash).send();

                Transaction tx = ethTransaction.getTransaction()
                        .orElseThrow(() -> new Exception("Transazione non trovata sulla blockchain"));

                onChainHash = tx.getInput();
            }

            boolean emailMatch = email.trim().equalsIgnoreCase(transazioneDb.getEmail());

            boolean matchInputDb
                    = inputHashAssertion.equalsIgnoreCase(transazioneDb.getHashHex());

            boolean matchDbBlockchain
                    = transazioneDb.getHashHex().equalsIgnoreCase(onChainHash);

            boolean valid = emailMatch && matchInputDb && matchDbBlockchain;

            response.put("status", valid ? "success" : "error");
            response.put("valid", valid);

            response.put("messaggio",
                    valid
                            ? "✅ Badge autentico: verifica completata con successo."
                            : "⚠️ Verifica fallita"
            );

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("confronto_email", emailMatch);
            details.put("confronto_db", matchInputDb);
            details.put("confronto_blockchain", matchDbBlockchain);

            response.put("dettagli", details);

            infoTrack.setDescrizione(
                    valid
                            ? "VERIFICA SUCCESSO - badge valido"
                            : "VERIFICA FALLITA - dati non corrispondono"
            );

            JpaUtil.salvaInfoTrack(infoTrack);

            return response;

        } catch (Exception e) {

            return buildError(response, infoTrack, e.getMessage());
        }
    }

    private Map<String, Object> buildError(Map<String, Object> response,
            InfoTrack infoTrack,
            String message) {

        response.put("stato", "errore");
        response.put("valido", false);
        response.put("messaggio", "❌ " + message);

        infoTrack.setDescrizione("ERRORE verifica badge - " + message);
        JpaUtil.salvaInfoTrack(infoTrack);

        return response;
    }

    private Map<String, Object> buildErrorResponse(InfoTrack infoTrack, Exception e, String context) {

        String message = e.getMessage() != null ? e.getMessage() : "Errore sconosciuto";

        infoTrack.setDescrizione("ERRORE - " + context + " - " + message);
        JpaUtil.salvaInfoTrack(infoTrack);

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("stato", "errore");
        errorResponse.put("messaggio", "❌" + context);
        errorResponse.put("dettagli", message);

        return errorResponse;
    }

    private Map<String, Object> mergeIfMissing(Map<String, Object> original, Object fallback) {

        if (!(fallback instanceof Map)) {
            return original;
        }

        Map<String, Object> fb = (Map<String, Object>) fallback;

        if (fb == null || fb.isEmpty()) {
            return original;
        }

        if (original == null || original.isEmpty()) {
            return fb;
        }

        Map<String, Object> result = new HashMap<>(fb);

        result.putAll(original);

        return result;
    }

    private boolean isEmptyMap(Map<String, Object> map) {

        if (map == null || map.isEmpty()) {
            return true;
        }

        return map.values().stream().allMatch(v -> isEmptyValue(v));
    }

    private boolean isEmptyValue(Object v) {

        if (v == null) {
            return true;
        }

        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() || s.equalsIgnoreCase("null");
        }

        if (v instanceof Map) {
            return ((Map<?, ?>) v).isEmpty();
        }

        if (v instanceof List) {
            return ((List<?>) v).isEmpty();
        }

        return false;
    }
}
