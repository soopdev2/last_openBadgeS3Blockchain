/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Api;

import Entity.InfoTrack;
import Services.Filter.Secured;
import Services.OpenBadges.OpenBadgeService;
import Utility.JpaUtil;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.time.LocalDateTime;

/**
 *
 * @author Salvatore
 */
@Path("/openbadge")
public class OpenBadgeApi {

    @POST
    @Path("/genera")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response generaBadge(Map<String, Object> input) {

        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /openbadge/genera - Generazione badge");
        OpenBadgeService openBadgeService = new OpenBadgeService();

        try {
            Map<String, Object> badge = (Map<String, Object>) input.get("badge");
            Map<String, Object> mittente = (Map<String, Object>) input.get("mittente");
            Map<String, Object> destinatario = (Map<String, Object>) input.get("destinatario");
            Map<String, Object> criteri = (Map<String, Object>) input.get("criteri");
            String base64File = (String) input.get("file");

            Map<String, Object> result = openBadgeService.genera(badge, mittente, destinatario, criteri, base64File);
            infoTrack.setDescrizione("SUCCESSO - 200 - L'openbadge è stato salvato e registrato sulla blockchain.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.ok(result).build();

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();

        }
    }

    @POST
    @Path("/verifica")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response verificaBadge(Map<String, Object> input) {

        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /openbadge/verifica");

        OpenBadgeService service = new OpenBadgeService();

        try {
            Map<String, Object> badge = (Map<String, Object>) input.get("badge");

            Map<String, Object> result = service.verifica(badge);

            infoTrack.setDescrizione("SUCCESSO - 200 - Verifica completata");
            JpaUtil.salvaInfoTrack(infoTrack);

            return Response.ok(result).build();

        } catch (IllegalArgumentException e) {
            infoTrack.setDescrizione("ERRORE VALIDAZIONE - " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ))
                    .build();

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "status", "error",
                            "message", "Errore interno"
                    ))
                    .build();
        }

    }

}
