# JoularJX para el TFG — Guía para no perdernos (otra vez)

Esto es la chuleta de todo lo que hicimos para dejar JoularJX funcionando de verdad, en Linux, después de que Windows nos diera por saco con un bug del driver de AMD que ni el propio desarrollador ha arreglado del todo. Está escrito para que dentro de 3 meses, cuando se nos haya olvidado todo, podamos volver aquí y no tengamos que reinventar la rueda.

## Qué es JoularJX

Es un agente de Java que se engancha al programa cuando lo arrancamos y nos dice **cuánta energía consume cada método**, sin tener que tocar el código para nada. Perfecto para el TFG de comparar Jackson vs Gson.

## Por qué en Linux y no en Windows

Porque en Windows hace falta un driver + un programa intermedio (Scaphandre / WinPowerMonitor) para leer la energía de la CPU, y ese driver tiene un bug conocido con procesadores AMD Ryzen (issue abierto en su GitHub desde 2025, sin arreglo real). Da igual lo que se instale, siempre falla con "función incorrecta". En Linux, el propio kernel expone la energía directamente vía RAPL, sin necesitar nada de eso. Menos piezas, menos lío.

Si algún día queremos intentarlo otra vez en Windows, el bug está documentado aquí: https://github.com/joular/WinPowerMonitor/issues/2

## Requisitos

- Ubuntu/Debian (o derivado)
- Kernel 5.11 o superior (para que RAPL funcione bien en Ryzen)
- CPU Intel o AMD Ryzen (o más nuevo)

Comprobar el kernel con:

```bash
uname -r
```

## Paso 1: instalar lo básico

Java, Maven y git, todo de una:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install openjdk-17-jdk maven git acl -y
```

Comprobar que se instaló bien:

```bash
java -version
mvn -version
```

(Con Java 17 vamos sobrados, JoularJX solo pide 11+.)

## Paso 2: clonar y compilar JoularJX

```bash
cd ~/Escritorio
git clone https://github.com/joular/joularjx.git
cd joularjx
mvn clean install
```

Si al final sale `BUILD SUCCESS`, perfecto. El jar se genera en `target/joularjx-3.1.0.jar` (el número de versión puede variar, mirar dentro de `target/` con `ls target/` si no coincide).

## Paso 3: comprobar que RAPL funciona ANTES de meternos con Java

Esto ahorra media hora de frustración si algo va mal más adelante. Sin instalar nada de Java, solo mirando si el kernel lee energía real:

```bash
sudo cat /sys/class/powercap/intel-rapl:0/energy_uj
```

Esperar 2 segundos y volver a ejecutar el mismo comando. Si el número ha subido, RAPL funciona de verdad y podemos seguir tranquilos.

### (Opcional) Dar permisos para no usar sudo cada vez

Si nos cansamos de poner `sudo` en cada ejecución, podemos dar permiso de lectura a nuestro usuario (aunque se resetea cada vez que se reinicia el PC, así que toca repetirlo cada sesión):

```bash
sudo setfacl -Rm u:$USER:r /sys/class/powercap/intel-rapl:*
```

Si esto da problemas raros de permisos, no pasa nada, usamos `sudo java ...` cada vez y ya está, funciona exactamente igual.

## Paso 4: configurar el filtro (config.properties)

El fichero `config.properties` está en la raíz del repo (`joularjx/config.properties`). Dentro tiene una línea que dice `filter-method-names=Package`, que es un placeholder de ejemplo, no algo real. Hay que cambiarla por el nombre de nuestra clase o paquete real, si no, la carpeta de resultados filtrados (`app/`) sale vacía.

Para la prueba con `Test.java` (sin paquete):

```bash
sed -i 's/filter-method-names=Package/filter-method-names=Test/' config.properties
```

**Importante:** cuando montemos el proyecto de verdad (Jackson/Gson), cambiar `Test` por el paquete real del proyecto (por ejemplo `com.tfg.jsonbench`). Esto **solo se hace una vez** por proyecto, no hace falta repetirlo en cada ejecución.

## Paso 5: probar con un programa mínimo

Crear un `Test.java` cualquiera que tarde un poco en ejecutarse (para que dé tiempo a medir algo):

```bash
nano Test.java
```

Pegar esto:

```java
public class Test {
    public static void main(String[] args) throws InterruptedException {
        long sum = 0;
        for (long i = 0; i < 5_000_000_000L; i++) {
            sum += i;
        }
        System.out.println("Resultado: " + sum);
    }
}
```

Guardar con `Ctrl+O`, Enter, `Ctrl+X`. Compilar y ejecutar con el agente enganchado:

```bash
javac Test.java
sudo java -javaagent:target/joularjx-3.1.0.jar Test
```

Si todo va bien, sale algo como:

```
Program consumed 37,16 joules
```

## Paso 6: ver los resultados

Cada ejecución crea una carpeta nueva con un timestamp dentro de `joularjx-result/`. Mirar cuál es la más reciente:

```bash
ls joularjx-result/
```

Y dentro de esa carpeta, lo que más nos interesa es esto:

```bash
cat joularjx-result/CARPETA_QUE_HAYA_SALIDO/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

(Cambiar `CARPETA_QUE_HAYA_SALIDO` por el nombre real que haya salido en el `ls` de arriba.)

Debería salir algo como:

```
Test.main,34.4440
```

Eso es: el método, y los julios que ha consumido. Eso es justo lo que necesitamos para el TFG.

### Qué carpetas hay ahí dentro

- `app/total/methods/` → consumo total de NUESTRO código, ya filtrado. **Esta es la que más vamos a usar.**
- `all/total/methods/` → consumo de TODO, incluido lo del JDK por debajo.
- `runtime/` → cómo evoluciona el consumo mientras se ejecuta el programa.
- `calltrees/` → consumo por árbol de llamadas completo, no solo método suelto.

## El flujo que vamos a repetir siempre (guardar esto)

Cada vez que queramos medir algo nuevo, en el 90% de los casos solo hace falta esto:

```bash
# 1. Compilar el código (solo si se ha cambiado)
javac TuClase.java
# o si es un proyecto Maven completo:
mvn package

# 2. Ejecutar con el agente
sudo java -javaagent:/ruta/a/joularjx-3.1.0.jar TuClasePrincipal

# 3. Mirar los resultados (carpeta más reciente)
ls joularjx-result/
cat joularjx-result/CARPETA/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

El `config.properties` (el filtro del paso 4) solo se toca UNA VEZ por proyecto, no en cada ejecución.

## Crear una clase de prueba con paquete (para probar el filtro tipo com.tfg.xxx)

Esto es útil para practicar el filtro `filter-method-names` tal y como se usará luego con Jackson/Gson (que sí van a tener paquete), en vez de una clase suelta como `Test.java`.

**Paso 1: crear la carpeta que refleja el paquete**

El paquete `com.tfg.pruebas` se traduce en la carpeta `com/tfg/pruebas/`. Crearla desde donde queramos tener el proyecto (no hace falta que esté dentro de `joularjx/`):

```bash
mkdir -p com/tfg/pruebas
nano com/tfg/pruebas/Ejemplo.java
```

**Paso 2: pegar esto**

```java
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
```

Guardar (`Ctrl+O`, Enter, `Ctrl+X`).

**Paso 3: compilar desde la carpeta raíz del proyecto (NO desde dentro de com/tfg/pruebas)**

```bash
javac com/tfg/pruebas/Ejemplo.java
```

Comprobar que se generó el `.class` junto al `.java`:

```bash
ls com/tfg/pruebas/
```

**Paso 4: copiar el config.properties de joularjx a esta carpeta**

Como el proyecto está fuera de `joularjx/`, JoularJX necesita su propia copia del config en el sitio desde donde se ejecuta `java`:

```bash
cp ~/Escritorio/tfg/joularjx-tool/config.properties .
```

**Paso 5: ajustar el filtro al paquete (no solo a la clase)**

```bash
sed -i 's/^filter-method-names=.*/filter-method-names=com.tfg.pruebas/' config.properties
```

**Paso 6: verificar que quedó bien antes de ejecutar (para no fallar a la primera)**

```bash
grep filter-method-names config.properties
```

Debe salir exactamente: `filter-method-names=com.tfg.pruebas`

**Paso 7: ejecutar con el agente, usando el nombre completo con puntos (no la ruta con barras)**

```bash
sudo java -javaagent:$HOME/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar com.tfg.pruebas.Ejemplo
```

**Paso 8: mirar el resultado (carpeta más reciente, automático)**

```bash
cat joularjx-result/$(ls -t joularjx-result/ | head -1)/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

Debería salir algo como:
```
com.tfg.pruebas.Ejemplo.main,0.0002
```

**Nota sobre los números pequeños:** con un bucle suelto como este, es normal que el valor filtrado salga muy bajo o poco significativo (el JIT lo optimiza y la ejecución es demasiado corta para que el muestreo de stacktrace, cada 10ms, capture bien el método). Esto **no es un error** — es justo la razón por la que para Jackson/Gson se usará JMH: fuerza muchas repeticiones controladas para que las medidas sean representativas. Esta prueba solo sirve para confirmar que el pipeline (compilar → filtro → agente → CSV) funciona de punta a punta.

## Referencia completa: todas las opciones de config.properties

Cómo cambiar cualquiera de estas: abrir el fichero con `nano config.properties` (o usar `sed -i 's/opcion=valor_viejo/opcion=valor_nuevo/' config.properties`), cambiar el valor a la derecha del `=`, guardar y ya está. Solo hace falta tocarlo al cambiar de proyecto o al querer experimentar con algo, no en cada ejecución.

```properties
# --- Filtrado (la que más vamos a tocar) ---
filter-method-names=Package
# Nombre del paquete o clase tal cual aparece en el código (ej: com.tfg.jsonbench).
# Si no coincide con nada, la carpeta app/ sale vacía.
# Se pueden poner varios separados por coma: filter-method-names=com.tfg.jsonbench,com.tfg.otra

# --- Datos en tiempo real (runtime) ---
save-runtime-data=true
# Guarda el consumo cada segundo en CSV. false = no genera estos ficheros.

overwrite-runtime-data=true
# true = un solo fichero que se sobrescribe cada ciclo. false = un fichero nuevo por ciclo (se llena de archivos rápido).

# --- Logging ---
logger-level=INFO
# Valores: OFF, INFO, WARNING, SEVERE. Bajarlo si el terminal se llena de logs.

# --- Evolución del consumo por método ---
track-consumption-evolution=true
# Genera un CSV por método con su consumo en cada timestamp. Útil para gráficas de evolución en la memoria del TFG.

evolution-data-path=evolution
# Carpeta donde se guardan esos CSV de evolución.

# --- Limpieza de datos ---
hide-agent-consumption=true
# Oculta el consumo del propio agente JoularJX para no ensuciar las medidas. Dejarlo en true.

# --- Árboles de llamadas ---
enable-call-trees-consumption=true
# Genera un CSV con el consumo total por árbol de llamadas al final de la ejecución. Bueno para encontrar hotspots.

save-call-trees-runtime-data=true
# Como save-runtime-data pero para árboles de llamadas (cada segundo).

overwrite-call-trees-runtime-data=true
# Como overwrite-runtime-data pero para árboles de llamadas.

# --- Precisión del muestreo ---
stack-monitoring-sample-rate=10
# Milisegundos entre cada lectura del stacktrace (rango 1-1000).
# Más bajo = más preciso pero más overhead del propio agente.
# Interesante para la sección de "limitaciones" del TFG: comparar resultados con distintos valores.

# --- Servidores de aplicaciones ---
application-server=false
# Solo ponerlo a true si se corre sobre Spring Boot/Tomcat. Para JMH standalone (nuestro caso), false.

# --- Solo Windows (no aplica en nuestro setup Linux) ---
powermonitor-path=C:\\joularjx\\PowerMonitor.exe

# --- Máquinas virtuales (no aplica salvo que midamos dentro de una VM) ---
vm-monitoring=false
vm-power-path=/tmp/power.csv
vm-power-format=watts
# Formatos soportados: powerjoular (CSV de PowerJoular) o watts (fichero con un solo valor float).

# --- Joular Core (fuente de energía alternativa a RAPL, no la necesitamos) ---
joular-core=false
joular-core-ring-buffer=false
joular-core-path=/usr/bin/joularcore
joular-core-parameters=-c cpu -i
joular-core-ringbuffer-path=/dev/shm/joularcorering
```

**En resumen, para nuestro caso (Linux, JMH standalone) solo tocamos normalmente:**
- `filter-method-names` → una vez por proyecto, obligatorio
- `stack-monitoring-sample-rate` → opcional, solo si queremos estudiar precisión vs overhead
- El resto ya viene bien por defecto tal cual está en el repo

### Sobre el JoularJX Reader (GUI)

Existe una GUI oficial llamada JoularJX Reader para visualizar los resultados, pero **es solo para Windows** (hecha en C#/WinUI3, necesita Visual Studio para compilarla). En Linux no sirve. Para ver los CSV basta con abrirlos en LibreOffice Calc, o si queremos graficarlos para la memoria, con Python (pandas + matplotlib).

## Montar el proyecto de Jackson + Gson (dependencias con Maven)

### Qué son estas piezas y por qué

- **Jackson** y **Gson** son las dos librerías que vamos a comparar en el TFG. Las dos hacen lo mismo (convertir objetos Java a JSON y viceversa) pero con arquitecturas internas distintas, por eso interesa medir cuál gasta más energía.
- **JMH (Java Microbenchmark Harness)** NO es una librería de JSON. Es la herramienta que ejecuta el código en bucle de forma controlada y fiable, para que las mediciones sean representativas (con un `main` suelto los números salen como ruido, ver la nota de los números pequeños más arriba).

Estas tres cosas NO se "instalan" como programas sueltos. Se declaran como dependencias en un fichero `pom.xml` y Maven las descarga solo desde Maven Central. El `pom.xml` es el fichero de configuración de Maven: es donde se dice "este proyecto usa Jackson versión X, Gson versión Y, JMH versión Z", y Maven se encarga de bajarlas y meterlas en el classpath. Es lo mismo que hace el propio JoularJX (por eso en el Paso 2 hicimos `mvn clean install`).

### Por qué UN solo proyecto y no dos separados

Aunque comparemos dos librerías, montamos **un único proyecto Maven** con las dos dentro (no un proyecto para Jackson y otro para Gson). Motivos:

- **Mismas condiciones de medición:** las dos corren con el mismo `pom.xml`, mismo `config.properties`, mismo JMH. Si fueran dos proyectos, algo se podría desincronizar (una versión distinta, otra config) y la comparación dejaría de ser justa.
- **No duplicar el pom.xml:** con uno solo, si cambiamos algo (ej. versión de JMH) se cambia una vez, no en dos sitios.
- **JMH está pensado así:** varias clases `@Benchmark` en el mismo proyecto se comparan entre sí en la misma ejecución, y JMH saca ya una tabla comparativa él solo.
- **Más simple para JoularJX:** un solo `filter-method-names=com.tfg.jsonbench` capta las dos clases a la vez, porque están bajo el mismo paquete padre.

### Nota sobre la versión de Java

En la máquina hay Java 25, que es muy reciente y no es LTS. JoularJX solo pide 11+, así que en teoría vale. Pero si aparece algún fallo raro de compatibilidad con Maven o las librerías, lo primero a probar es cambiar a un JDK LTS asentado (17 o 21). El `pom.xml` está fijado a compilar con nivel 17 (`maven.compiler.source/target=17`) para ir sobre seguro.

### Paso 1: crear la carpeta del proyecto (una sola, unificada)

```bash
mkdir -p ~/Escritorio/tfg/pruebas/jackson-gson-benchmark
cd ~/Escritorio/tfg/pruebas/jackson-gson-benchmark
```

### Paso 2: crear el pom.xml

```bash
nano pom.xml
```

Pegar esto:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.tfg</groupId>
    <artifactId>jsonbench</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.18.2</version>
        </dependency>

        <!-- Gson -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>

        <!-- JMH -->
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-core</artifactId>
            <version>1.37</version>
        </dependency>
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-generator-annprocess</artifactId>
            <version>1.37</version>
        </dependency>
    </dependencies>
</project>
```

Guardar (`Ctrl+O`, Enter, `Ctrl+X`).

### Paso 3: descargar las dependencias (esto es el "instalar")

```bash
mvn compile
```

Si sale `BUILD SUCCESS`, ya están Jackson, Gson y JMH descargados y listos, sin haber escrito ni una clase todavía. Es normal que avise `No sources to compile` (aún no hay ningún `.java`, no es un error). Maven las guarda en `~/.m2/repository/` para siempre, disponibles para cualquier proyecto.

### Paso 4: comprobar qué se descargó (opcional)

```bash
mvn dependency:tree
```

Debería listar Jackson, Gson y JMH con sus dependencias internas colgando (jackson-core, jackson-annotations, commons-math3 que usa JMH, etc.).

### Paso 5: copiar el config.properties al proyecto

```bash
cp ~/Escritorio/tfg/joularjx-tool/config.properties .
```

El filtro se ajustará a `com.tfg.jsonbench` cuando escribamos las clases (siguiente sesión).

### Cómo se organizará el código (para la próxima sesión)

Un solo proyecto, pero con un subpaquete por librería para tenerlo ordenado:

```
jackson-gson-benchmark/
├── pom.xml
├── config.properties
└── src/main/java/com/tfg/jsonbench/
    ├── jackson/
    │   └── JacksonBenchmark.java    → package com.tfg.jsonbench.jackson;
    └── gson/
        └── GsonBenchmark.java       → package com.tfg.jsonbench.gson;
```

Como los dos subpaquetes cuelgan de `com.tfg.jsonbench`, con poner `filter-method-names=com.tfg.jsonbench` en el config, JoularJX filtra los dos a la vez.

### Sobre NetBeans / IDEs

No hace falta ningún IDE para nada de esto. Se puede escribir el código en NetBeans, VSCode o lo que sea (por comodidad, autocompletado, etc.), pero **para medir con JoularJX siempre vamos por terminal**, no por el botón "Run" del IDE. Razón: para inyectar el agente hay que añadir el flag `-javaagent:...` a la JVM, y aunque los IDEs permiten poner "VM Options" personalizadas, es un lío extra (rutas raras, el config.properties no siempre se coge de donde esperas) frente a un simple `mvn package` + `sudo java -javaagent:... ClaseMain` en terminal, que es 100% reproducible.

## Siguiente paso pendiente

Escribir las clases `JacksonBenchmark.java` y `GsonBenchmark.java` con las anotaciones `@Benchmark` de JMH (serialización y deserialización, con distintos tamaños de payload: pequeño, mediano, grande), ajustar `filter-method-names=com.tfg.jsonbench` en el config, y lanzar las primeras mediciones reales para comparar el consumo de las dos librerías.
