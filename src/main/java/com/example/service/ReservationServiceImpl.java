package com.example.service;

import com.example.model.Reservation;
import javax.persistence.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ReservationServiceImpl implements ReservationService, AutoCloseable {
    private final EntityManagerFactory emf;
    private final boolean proprietaire;

    public ReservationServiceImpl() {
        this(Persistence.createEntityManagerFactory("reservationPU"), true);
    }
    public ReservationServiceImpl(EntityManagerFactory emf) { this(emf, false); }
    private ReservationServiceImpl(EntityManagerFactory emf, boolean proprietaire) {
        this.emf = Objects.requireNonNull(emf);
        this.proprietaire = proprietaire;
    }
    private <T> T ecrire(Function<EntityManager, T> operation) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T resultat = operation.apply(em);
            tx.commit();
            return resultat;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            // Le commit peut envelopper le conflit dans une RollbackException.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof OptimisticLockException) throw (OptimisticLockException) cause;
            }
            throw e;
        } finally { em.close(); }
    }
    private static void valider(Reservation r) {
        if (r == null || r.getSalle() == null || r.getSalle().trim().isEmpty()
                || r.getDateDebut() == null || r.getDateFin() == null
                || !r.getDateDebut().isBefore(r.getDateFin())) {
            throw new IllegalArgumentException("Salle et intervalle de reservation valides obligatoires");
        }
    }
    @Override public Reservation creerReservation(Reservation r) {
        valider(r);
        if (r.getId() != null) throw new IllegalArgumentException("Reservation deja persistante");
        return ecrire(em -> { em.persist(r); return r; });
    }
    @Override public Reservation modifierReservation(Reservation r) {
        valider(r);
        return ecrire(em -> {
            verifierExistence(em, r);
            // merge conserve la version detachee : une copie obsolete doit echouer.
            return em.merge(r);
        });
    }
    private void verifierExistence(EntityManager em, Reservation r) {
        if (r == null || r.getId() == null || em.find(Reservation.class, r.getId()) == null)
            throw new IllegalArgumentException("Reservation non trouvee");
    }
    @Override public void supprimerReservation(Reservation r) {
        ecrire(em -> { verifierExistence(em, r); em.remove(em.merge(r)); return null; });
    }
    @Override public Reservation trouverParId(Long id) {
        EntityManager em = emf.createEntityManager();
        try { return em.find(Reservation.class, Objects.requireNonNull(id)); }
        finally { em.close(); }
    }
    @Override public List<Reservation> listerReservations() {
        EntityManager em = emf.createEntityManager();
        try { return em.createQuery("SELECT r FROM Reservation r ORDER BY r.id", Reservation.class).getResultList(); }
        finally { em.close(); }
    }
    @Override public void close() { if (proprietaire && emf.isOpen()) emf.close(); }
}
