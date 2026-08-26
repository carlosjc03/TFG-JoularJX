package com.tfg.jsonbench.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class JacksonBenchmark {

    private ObjectMapper mapper;
    private Persona persona;
    private String json;

    @Setup
    public void preparar() throws Exception {
        mapper = new ObjectMapper();
        persona = new Persona("Carlos", 23, "carlos@tfg.com",
                List.of("leer", "correr", "programar"));
        json = mapper.writeValueAsString(persona);
    }

    @Benchmark
    public String serializar() throws Exception {
        return mapper.writeValueAsString(persona);
    }

    @Benchmark
    public Persona deserializar() throws Exception {
        return mapper.readValue(json, Persona.class);
    }
}
