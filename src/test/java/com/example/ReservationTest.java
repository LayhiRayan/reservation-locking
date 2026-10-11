package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import org.junit.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ReservationTest {
    private EntityManagerFactory emf;
    private ReservationServiceImpl service;
    private final LocalDateTime debut = LocalDateTime.of(2026, 11, 12, 9, 0);

    @Before public void initialiser() {
        Map<String, Object> options = new HashMap<>();
        options.put("javax.persistence.jdbc.url", "jdbc:h2:mem:" + UUID.randomUUID());
        options.put("hibernate.hbm2ddl.auto", "create-drop");
        options.put("hibernate.show_sql", "false");
        emf = Persistence.createEntityManagerFactory("reservationPU", options);
        service = new ReservationServiceImpl(emf);
    }
    @After public void fermer() { if (emf != null) emf.close(); }
    private Reservation creer() {
        return service.creerReservation(new Reservation("Salle A", debut, debut.plusHours(2)));
    }
    @Test public void crudEtVersion() {
        Reservation r = creer();
        assertNotNull(r.getId());
        assertEquals(0, r.getVersion());
        r.setSalle("Salle B");
        r = service.modifierReservation(r);
        assertEquals(1, r.getVersion());
        assertEquals("Salle B", service.trouverParId(r.getId()).getSalle());
        assertEquals(1, service.listerReservations().size());
        service.supprimerReservation(r);
        assertNull(service.trouverParId(r.getId()));
    }
    @Test public void copieObsoleteRefusee() {
        Long id = creer().getId();
        Reservation premiere = service.trouverParId(id);
        Reservation obsolete = service.trouverParId(id);
        premiere.setSalle("Salle B");
        service.modifierReservation(premiere);
        obsolete.setSalle("Salle C");
        assertThrows(OptimisticLockException.class, () -> service.modifierReservation(obsolete));
        assertEquals("Salle B", service.trouverParId(id).getSalle());
        assertEquals(1, service.trouverParId(id).getVersion());
    }
    @Test public void retryConserveLesModificationsDemandees() {
        Long id = creer().getId();
        Reservation premiere = service.trouverParId(id);
        Reservation obsolete = service.trouverParId(id);
        premiere.setSalle("Salle B");
        service.modifierReservation(premiere);
        obsolete.setSalle("Salle C");
        obsolete.setDateDebut(debut.plusDays(1));
        obsolete.setDateFin(debut.plusDays(1).plusHours(3));
        Reservation resultat = new ReservationRetryHandler(service).modifierAvecRetry(obsolete);
        Reservation relue = service.trouverParId(id);
        assertEquals(2, resultat.getVersion());
        assertEquals("Salle C", relue.getSalle());
        assertEquals(obsolete.getDateDebut(), relue.getDateDebut());
        assertEquals(obsolete.getDateFin(), relue.getDateFin());
    }
    @Test public void deuxTransactionsOuvertesDetectentLeConflitAuCommit() {
        Long id = creer().getId();
        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();
        try {
            em1.getTransaction().begin();
            em2.getTransaction().begin();
            Reservation r1 = em1.find(Reservation.class, id);
            Reservation r2 = em2.find(Reservation.class, id);
            r1.setSalle("Salle B");
            r2.setSalle("Salle C");
            em1.getTransaction().commit();
            RuntimeException erreur = assertThrows(RuntimeException.class, () -> em2.getTransaction().commit());
            Throwable cause = erreur;
            while (cause != null && !(cause instanceof OptimisticLockException)) cause = cause.getCause();
            assertNotNull("Le commit doit signaler un conflit optimiste", cause);
            assertEquals("Salle B", service.trouverParId(id).getSalle());
        } finally {
            if (em1.getTransaction().isActive()) em1.getTransaction().rollback();
            if (em2.getTransaction().isActive()) em2.getTransaction().rollback();
            em1.close();
            em2.close();
        }
    }
    @Test public void retryBorneEtNouvelleLectureEntreTentatives() {
        Reservation r = creer();
        AtomicInteger tentatives = new AtomicInteger();
        AtomicInteger lectures = new AtomicInteger();
        ReservationServiceImpl conflits = new ReservationServiceImpl(emf) {
            @Override public Reservation modifierReservation(Reservation reservation) {
                tentatives.incrementAndGet();
                throw new OptimisticLockException("Conflit simule");
            }
            @Override public Reservation trouverParId(Long id) {
                lectures.incrementAndGet();
                return super.trouverParId(id);
            }
        };
        assertThrows(OptimisticLockException.class, () -> new ReservationRetryHandler(conflits).modifierAvecRetry(r));
        assertEquals(3, tentatives.get());
        assertEquals(2, lectures.get());
    }
    @Test public void validationSansRetryEtReservationSupprimee() {
        Reservation r = creer();
        r.setDateFin(debut);
        assertThrows(IllegalArgumentException.class, () -> new ReservationRetryHandler(service).modifierAvecRetry(r));
        assertEquals(0, service.trouverParId(r.getId()).getVersion());
        r.setDateFin(debut.plusHours(2));
        service.supprimerReservation(r);
        assertThrows(IllegalArgumentException.class, () -> service.modifierReservation(r));
        assertTrue(service.listerReservations().isEmpty());
    }
}
