package com.tfg.jsonbench.gson;

import com.google.gson.Gson;
import com.tfg.jsonbench.Persona;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"-javaagent:/home/carlos/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar"})
public class GsonBenchmark {

    private Gson gson;
    private Persona persona;
    private String json;

    @Setup
    public void preparar() {
        gson = new Gson();
        persona = new Persona("Carlos", 23, "carlos@tfg.com",
                List.of("leer", "correr", "programar"));
        json = gson.toJson(persona);
    }

    @Benchmark
    public String serializar() {
        return gson.toJson(persona);
    }

    @Benchmark
    public Persona deserializar() {
        return gson.fromJson(json, Persona.class);
    }
}
