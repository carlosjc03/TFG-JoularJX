package com.tfg.jsonbench;

import java.util.List;

public class Persona {
    public String nombre;
    public int edad;
    public String email;
    public List<String> hobbies;

    public Persona() {}

    public Persona(String nombre, int edad, String email, List<String> hobbies) {
        this.nombre = nombre;
        this.edad = edad;
        this.email = email;
        this.hobbies = hobbies;
    }
}
