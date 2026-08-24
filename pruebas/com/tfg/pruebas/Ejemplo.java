package com.tfg.pruebas;

public class Ejemplo {
    public static void main(String[] args) {
        long suma = 0;
        for (long i = 0; i < 3_000_000_000L; i++) {
            suma += i * 2;
        }
        System.out.println("Resultado: " + suma);
    }
}
