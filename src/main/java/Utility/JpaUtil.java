/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

import Entity.InfoTrack;
import Entity.Transazione;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @author Salvatore
 */
public class JpaUtil {

    final static EntityManagerFactory emf = Persistence.createEntityManagerFactory("openbadge");
    final static EntityManager em = emf.createEntityManager();

    public static InfoTrack salvaInfoTrack(InfoTrack InfoTrack) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(InfoTrack);
            tx.commit();
            return InfoTrack;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            System.out.println(e);
            return null;
        }
    }

    public static Transazione saveTxHashAndHashHexOnDb(Transazione transazione) {
        EntityTransaction tx = em.getTransaction();
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - saveTxHashAndHashHexOnDb() - effettua salvataggio transazione.");
        try {
            tx.begin();
            em.persist(transazione);
            tx.commit();
            infotrack.setDescrizione("SUCCESSO - 200 - Salvataggio transazione con id " + transazione.getId() + " effettuato con successo.");
            salvaInfoTrack(infotrack);
            return transazione;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile effettuare il salvataggio della transazione: " + e.getMessage());
            salvaInfoTrack(infotrack);
            return null;
        }
    }

    public static Transazione trovaTransazioneByHash(String txHash) {
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - trovaTransazioneByHash() - ricerca transazione per hash.");

        try {
            TypedQuery<Transazione> query = em.createQuery(
                    "SELECT t FROM Transazione t WHERE t.txHash = :txHash", Transazione.class);
            query.setParameter("txHash", txHash);

            List<Transazione> risultati = query.getResultList();

            if (risultati.isEmpty()) {
                infotrack.setDescrizione("WARNING - 404 - Nessuna transazione trovata per l'hash: " + txHash);
                salvaInfoTrack(infotrack);
                return null;
            }

            Transazione transazione = risultati.get(0);
            infotrack.setDescrizione("SUCCESSO - 200 - Transazione recuperata con successo.");
            salvaInfoTrack(infotrack);
            return transazione;

        } catch (Exception e) {
            infotrack.setDescrizione("ERRORE - 500 - Errore durante il recupero della transazione: " + e.getMessage());
            salvaInfoTrack(infotrack);
            return null;
        }
    }
}
