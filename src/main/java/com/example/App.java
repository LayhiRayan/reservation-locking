package com.example;

public class App {
    public static void main(String[] args) {
        System.out.println("=== 1. Detection du conflit avec @Version ===");
        ConcurrentReservationSimulator.main(args);
        System.out.println("\n=== 2. Rechargement et nouvelle tentative ===");
        ConcurrentReservationWithRetry.main(args);
    }
}
