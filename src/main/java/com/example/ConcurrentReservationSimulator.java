package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import javax.persistence.OptimisticLockException;
import java.time.LocalDateTime;

public class ConcurrentReservationSimulator {
    public static void main(String[] args) {
        try (ReservationServiceImpl service = new ReservationServiceImpl()) {
            LocalDateTime debut = LocalDateTime.of(2026, 11, 12, 9, 0);
            Reservation initiale = service.creerReservation(new Reservation("Salle A", debut, debut.plusHours(2)));
            Reservation tx1 = service.trouverParId(initiale.getId());
            Reservation tx2 = service.trouverParId(initiale.getId());
            System.out.println("Deux lectures independantes : versions " + tx1.getVersion() + " et " + tx2.getVersion());
            tx1.setSalle("Salle B");
            System.out.println("Transaction 1 : modification reussie : " + service.modifierReservation(tx1));
            tx2.setSalle("Salle C");
            try {
                service.modifierReservation(tx2);
                throw new IllegalStateException("Le conflit attendu n'a pas ete detecte");
            } catch (OptimisticLockException attendu) {
                System.out.println("Transaction 2 : conflit detecte (OptimisticLockException).");
            }
            System.out.println("Etat conserve : " + service.trouverParId(initiale.getId()));
        }
    }
}
