# JoularJX — Apuntes

## Qué es JoularJX

Es un agente de Java que se engancha al programa cuando lo arrancamos y nos dice **cuánta energía consume cada método**, sin tener que tocar el código para nada
## Por qué en Linux y no en Windows

Porque en Windows hace falta un driver + un programa intermedio (Scaphandre / WinPowerMonitor) para leer la energía de la CPU, y ese driver tiene un bug conocido con procesadores AMD Ryzen (issue abierto en su GitHub desde 2025 https://github.com/joular/WinPowerMonitor/issues/2 ). En Linux, el propio kernel expone la energía directamente vía RAPL, sin necesitar nada de eso.

## Estructura de carpetas del TFG

Así está organizado todo:

```
tfg/
├── joularjx-tool/                    → la herramienta JoularJX
│                                       NO se toca su código ni su pom.xml.
│                                       Aquí vive el jar del agente: target/joularjx-3.1.0.jar
│
├── pruebas/                          → todo el código que medimos
│   ├── ejemplo-basico/               → la prueba de verificación del entorno
│   │   ├── com/tfg/pruebas/          → Ejemplo.java (+ .class al compilar)
│   │   ├── config.properties         → con filter-method-names=com.tfg.pruebas
│   │   └── joularjx-result/          → resultados de esta prueba (ignorado por Git)
│   │
│   └── jackson-gson-benchmark/       → el proyecto real del TFG
│       ├── pom.xml                   → dependencias (Jackson, Gson, JMH) + plugins
│       ├── config.properties         → con filter-method-names=com.tfg.jsonbench
│       ├── src/main/java/com/tfg/jsonbench/
│       │   ├── Persona.java          → objeto de datos común
│       │   ├── jackson/JacksonBenchmark.java
│       │   └── gson/GsonBenchmark.java
│       ├── target/                   → lo que genera Maven (ignorado por Git)
│       └── joularjx-result/          → resultados de los benchmarks (ignorado por Git)
│
├── resultados/                       → copia de las mediciones BUENAS, organizadas
│                                       y con nombres legibles
│
├── 00-PhDTFMTFG-LaTeX-Template-UAH-v4.2.4h/  → plantilla LaTeX de la UAH
└── README.md                         → este documento
```

Cada proyecto que se quiera medir vive en su propia carpeta dentro de `pruebas/`, y cada uno lleva **su propio `config.properties`** (con su filtro ajustado a su paquete). Así no hay que estar cambiando el filtro cada vez que se salta de un proyecto a otro.

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
sudo apt install openjdk-25-jdk maven git acl -y
```

Comprobar que se instaló bien:

```bash
java -version
mvn -version
```

(Con Java 25 vamos sobrados, JoularJX solo pide 11+.)

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

Pegar codigo de ejemplo:

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

Si todo va bien, sale:

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

Debería salir:

```
Test.main,34.4440
```

Eso es: el método, y los julios que ha consumido

### Qué carpetas hay ahí dentro

- `app/total/methods/` → consumo total de NUESTRO código, ya filtrado. **En teoría esta es la que más vamos a usar.**
- `all/total/methods/` → consumo de TODO, incluido lo del JDK por debajo.
- `runtime/` → cómo evoluciona el consumo mientras se ejecuta el programa.
- `calltrees/` → consumo por árbol de llamadas completo, no solo método suelto.

## El flujo que vamos a repetir siempre (guardar esto)

Cada vez que queramos medir algo nuevo, en el 90% de los casos solo hace falta esto:

```bash
# 0. Situarse en la carpeta del proyecto que se va a medir
cd ~/Escritorio/tfg/pruebas/EL_PROYECTO

# 1. Compilar el código (solo si se ha cambiado)
javac com/ruta/TuClase.java
# o si es un proyecto Maven completo:
mvn clean package

# 2. Ejecutar con el agente
#    - Clase suelta: el agente va en el comando
sudo java -javaagent:$HOME/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar com.paquete.TuClase
#    - Proyecto JMH: el agente ya va dentro del código (@Fork), así que solo:
sudo java -jar target/benchmarks.jar

# 3. Mirar los resultados (carpeta más reciente)
cat joularjx-result/$(ls -t joularjx-result/ | head -1)/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

Dos cosas que solo se hacen UNA VEZ por proyecto, no en cada ejecución:
- Copiar el `config.properties` a la carpeta del proyecto.
- Ajustar su `filter-method-names` al paquete de ese proyecto.

## Crear una clase de prueba con paquete (para probar el filtro tipo com.tfg.xxx)

Esto es útil para practicar el filtro `filter-method-names` tal y como se usará luego con Jackson/Gson (que sí van a tener paquete), en vez de una clase suelta como `Test.java`.

Esta prueba vive en `~/Escritorio/tfg/pruebas/ejemplo-basico/`. Todos los comandos de esta sección se ejecutan desde ahí.

**Paso 1: crear la carpeta del proyecto y la que refleja el paquete**

El paquete `com.tfg.pruebas` se traduce en la carpeta `com/tfg/pruebas/`. Ojo: son dos cosas distintas — la carpeta del proyecto (`ejemplo-basico/`) y, dentro de ella, el árbol de carpetas del paquete:

```bash
mkdir -p ~/Escritorio/tfg/pruebas/ejemplo-basico
cd ~/Escritorio/tfg/pruebas/ejemplo-basico
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

**Paso 3: compilar desde la raíz del proyecto (desde `ejemplo-basico/`, NO desde dentro de com/tfg/pruebas)**

```bash
javac com/tfg/pruebas/Ejemplo.java
```

Comprobar que se generó el `.class` junto al `.java`:

```bash
ls com/tfg/pruebas/
```

**Paso 4: copiar el config.properties de joularjx a esta carpeta**

JoularJX busca el `config.properties` en el directorio desde donde se lanza `java`. Como el proyecto está fuera de `joularjx-tool/`, necesita aquí su propia copia:

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
# Interesante para la sección de "limitaciones": comparar resultados con distintos valores.

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

**En resumen, para este caso (Linux, JMH standalone) solo tocamos normalmente:**
- `filter-method-names` → una vez por proyecto, obligatorio
- `stack-monitoring-sample-rate` → opcional, solo si queremos estudiar precisión vs overhead
- El resto ya viene bien por defecto tal cual está en el repo


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

El filtro se ajustará a `com.tfg.jsonbench` en la siguiente sección, cuando ya tengamos las clases escritas.

### Cómo se organiza el código

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

Como los dos subpaquetes cuelgan de `com.tfg.jsonbench`, con poner `filter-method-names=com.tfg.jsonbench` en el config, JoularJX filtra los dos a la vez. Los ficheros `.java` concretos se crean en la siguiente sección.

### Sobre NetBeans / IDEs

No hace falta ningún IDE para nada de esto. Se puede escribir el código en NetBeans, VSCode o lo que sea (por comodidad, autocompletado, etc.), pero **para medir con JoularJX siempre vamos por terminal**, no por el botón "Run" del IDE. Razón: para inyectar el agente hay que añadir el flag `-javaagent:...` a la JVM, y aunque los IDEs permiten poner "VM Options" personalizadas, es un lío extra (rutas raras, el config.properties no siempre se coge de donde esperas) frente a un simple `mvn package` + `sudo java -javaagent:... ClaseMain` en terminal, que es 100% reproducible.

## Dejar preparadas las clases de benchmark (Jackson y Gson con JMH)

### El "fork" de JMH y por qué importa para JoularJX

Cuando ejecutamos un benchmark con JMH, JMH **no mide en el mismo proceso Java desde el que lo lanzamos**. Por defecto arranca un **proceso Java nuevo y separado** (un "fork") y hace las mediciones ahí dentro, a propósito: un proceso limpio y aislado da resultados más fiables.

El problema: el agente JoularJX se engancha con `-javaagent:...` a UN proceso. Si lo enganchamos al proceso principal (el que lanza JMH), el agente no llega al proceso hijo, que es donde JMH mide de verdad. Resultado: JoularJX no captura nada útil.

La solución es decirle a JMH que, al crear el proceso hijo, lo arranque ya con el agente enganchado. Eso se hace con la anotación `@Fork` en cada clase de benchmark:

```java
@Fork(value = 1, jvmArgs = {"-javaagent:/home/carlos/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar"})
```

Así el `-javaagent` viaja al proceso donde JMH mide de verdad, y JoularJX sí captura el consumo. Este es el único truco fino de todo el montaje.

### Las DOS formas de enganchar el agente (importante para no liarse)

Hay dos maneras de enganchar JoularJX, según el tipo de proyecto. Es el MISMO agente, pero enganchado distinto porque el proceso arranca distinto:

**Forma A — por línea de comandos (para clases sueltas, tipo `Ejemplo.java`)**

El agente se escribe a mano en el comando cada vez que se ejecuta:

```bash
sudo java -javaagent:$HOME/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar com.tfg.pruebas.Ejemplo
```

Esto sirve cuando el programa es normal (arranca, hace algo, termina). El agente se engancha directo al proceso que arranca.

**Forma B — con la anotación `@Fork` (para proyectos JMH, tipo Jackson/Gson)**

Aquí el agente NO se pone en el comando. El comando es simplemente:

```bash
sudo java -jar target/benchmarks.jar
```

Y el agente va metido DENTRO del código, en la anotación `@Fork` de cada clase de benchmark. Motivo: JMH se va a un proceso hijo a medir (el famoso fork), y si pusiéramos el `-javaagent` en el comando, se engancharía al proceso padre, no al hijo donde se mide de verdad. El `@Fork` es lo que mete el agente en el proceso hijo correcto.

**Regla para saber cuál usar:** ¿es un programa suelto (un `main` normal)? → Forma A (comando). ¿Es un benchmark con JMH? → Forma B (`@Fork` dentro del código).

### ¿Qué clases llevan `@Fork` y cuáles no?

El `@Fork` va en la cabecera (encima del `public class`) de **toda clase que tenga métodos `@Benchmark`**. No en cualquier clase.

- **SÍ lleva `@Fork`:** `JacksonBenchmark`, `GsonBenchmark`, y cualquier clase futura de benchmark que se cree con JMH.
- **NO lleva `@Fork`:** clases normales que no miden nada. Por ejemplo `Persona.java` (es solo el objeto de datos, no un benchmark) no lo lleva. Tampoco la clase suelta `Ejemplo.java` (a esa se le engancha el agente por comando, Forma A).

O sea: `@Fork` = "esta clase mide energía con JMH". Si una clase solo guarda datos o hace utilidades, no lo lleva.

### Cuidado con la ruta escrita a pelo en el @Fork

El `@Fork` tiene la ruta al jar del agente escrita completa y fija:

```java
@Fork(value = 1, jvmArgs = {"-javaagent:/home/carlos/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar"})
```

Si algún día se mueve la carpeta del TFG a otro sitio, o se abre en otro ordenador, esa ruta dejará de existir y fallará. Por eso, si en el futuro algo peta con el agente en los benchmarks, lo PRIMERO que hay que revisar es que esa ruta del `@Fork` siga apuntando al jar real (comprobar con `ls` esa ruta exacta).

### Paso 1: crear los subpaquetes

```bash
cd ~/Escritorio/tfg/pruebas/jackson-gson-benchmark
mkdir -p src/main/java/com/tfg/jsonbench/jackson
mkdir -p src/main/java/com/tfg/jsonbench/gson
```

### Paso 2: crear una clase de datos común (el objeto que vamos a serializar)

```bash
nano src/main/java/com/tfg/jsonbench/Persona.java
```

```java
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
```

### Paso 3: el benchmark de Jackson

```bash
nano src/main/java/com/tfg/jsonbench/jackson/JacksonBenchmark.java
```

```java
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
```

### Paso 4: el benchmark de Gson (mismo objeto, misma estructura)

```bash
nano src/main/java/com/tfg/jsonbench/gson/GsonBenchmark.java
```

```java
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
```

**Ojo:** en el `@Fork` de las dos clases hay que poner la ruta real al jar del agente.  `/home/carlos/Escritorio/tfg/joularjx-tool/target/joularjx-3.1.0.jar` (de momento asi que es como tengo organizado todo por carpetas, a malas lo puedo comprobanr con ls)

### Paso 5: añadir los plugins al pom.xml

Hacen falta DOS plugins en el `pom.xml`:

1. **`maven-compiler-plugin`** con el procesador de anotaciones de JMH. JMH genera al compilar un índice de todos los `@Benchmark` (un fichero `META-INF/BenchmarkList`). A partir de Java moderno (Java 22+, y desde luego con el 25 que tenemos), el compilador ya NO ejecuta procesadores de anotaciones por defecto, así que hay que activarlo a mano. **Sin esto, al lanzar el jar sale el error `Unable to find the resource: /META-INF/BenchmarkList`** (JMH arranca pero no encuentra ningún benchmark).
2. **`maven-shade-plugin`** para empaquetar todo en un único jar ejecutable (un "uber-jar" que incluye nuestro código + JMH + las librerías dentro). Sin él, `mvn package` genera un jar normal que no sabe arrancar JMH.

Editar el `pom.xml` y añadir este bloque **justo después de** `</dependencies>` (antes de `</project>`). Importante el orden: el compiler primero, el shade después.

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.openjdk.jmh</groupId>
                            <artifactId>jmh-generator-annprocess</artifactId>
                            <version>1.37</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <finalName>benchmarks</finalName>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>org.openjdk.jmh.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

(Para ver como queda el `pom.xml` entero con todo junto, está montado completo al final de este readme, en el apartado "pom.xml completo de referencia".)

### Paso 6: ajustar el filtro de JoularJX al paquete

```bash
sed -i 's/^filter-method-names=.*/filter-method-names=com.tfg.jsonbench/' config.properties
grep filter-method-names config.properties
```

Debe salir: `filter-method-names=com.tfg.jsonbench` (capta los dos subpaquetes, jackson y gson, a la vez).

### Paso 7: compilar y empaquetar

```bash
mvn clean package
```

Si sale `BUILD SUCCESS`, se genera `target/benchmarks.jar`.

### Paso 8: lanzar los benchmarks

El agente JoularJX ya va inyectado vía el `@Fork` de las clases, así que NO hace falta pasarlo por línea de comandos. Se lanza el jar directamente:

```bash
sudo java -jar target/benchmarks.jar
```

Esto tarda un par de minutos. Por consola se ven muchas iteraciones (`Warmup Iteration 1, 2...` e `Iteration 1, 2...`): es JMH ejecutando cada operación en bucle, primero calentando motores (warmup) y luego midiendo 10 veces para sacar una media fiable. Es normal que salgan tantas. Al final JMH saca una tabla comparando Jackson vs Gson.

### Paso 9: entender los DOS tipos de resultado

Aquí hay que tener clara una cosa que despista mucho: salen **dos resultados distintos que miden cosas diferentes**.

**a) La tabla final de JMH = TIEMPO (velocidad), no energía.** Es la tabla que aparece al final en la consola:

```
Benchmark                                    Mode  Cnt  Score   Error  Units
c.t.j.gson.GsonBenchmark.deserializar        avgt   10  0,484 ± 0,017  us/op
c.t.j.gson.GsonBenchmark.serializar          avgt   10  0,492 ± 0,009  us/op
c.t.j.jackson.JacksonBenchmark.deserializar  avgt   10  0,453 ± 0,003  us/op
c.t.j.jackson.JacksonBenchmark.serializar    avgt   10  0,236 ± 0,003  us/op
```

`us/op` = microsegundos por operación. Cuanto MÁS BAJO, más rápido. Esto mide velocidad, no consumo. Está bien tenerlo como dato complementario, pero NO es el objetivo del TFG.

**b) Los CSV de JoularJX = ENERGÍA (julios).** Esto es lo que de verdad importa para el TFG, y va aparte, en ficheros CSV. Cada benchmark genera su propia carpeta dentro de `joularjx-result/` (así que salen 4 carpetas: gson-serializar, gson-deserializar, jackson-serializar, jackson-deserializar).

### La jerarquía de carpetas de JoularJX (cuál mirar y por qué)

Dentro de cada carpeta de resultado hay MUCHAS subcarpetas. La regla de oro: para el TFG siempre se usa **`app/total/methods/`**. El resto son variantes que no hacen falta (o son para análisis avanzado). Nivel por nivel:

- **`app/` vs `all/`**
  - `app/` → **solo NUESTRO código** (lo que filtramos con `com.tfg.jsonbench`). **Esta es la buena.**
  - `all/` → absolutamente todo, incluido el JDK, JMH y las librerías por debajo. Ensucia la comparación, no la queremos.
- **`total/` vs `runtime/`**
  - `total/` → el consumo **final acumulado** de cada método (un número por método). **Esta es la buena.**
  - `runtime/` → el consumo **segundo a segundo** (muchas líneas con timestamps). Solo sirve para gráficas de evolución temporal. Para comparar Jackson vs Gson no hace falta.
- **`methods/` vs `calltrees/`**
  - `methods/` → consumo por método individual. **Esta es la buena.**
  - `calltrees/` → consumo por árbol de llamadas (A llamó a B llamó a C). Útil para hotspots profundos, pero no para la comparación básica.

En resumen: `app/total/methods/` = "el consumo total de cada uno de mis métodos". Es exactamente lo que compara Jackson vs Gson.

### Paso 10: sacar los julios de las 4 mediciones de golpe

Como cada benchmark está en una carpeta distinta (con nombres tipo `14879-1787756656320`, donde el número de delante es el PID del proceso, no hace falta usarlo), este comando saca directamente solo las 4 líneas que importan, ya limpias:

```bash
cd ~/Escritorio/tfg/pruebas/jackson-gson-benchmark
grep -rh "Benchmark\.\(serializar\|deserializar\)," joularjx-result/*/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

Debería salir algo como:

```
com.tfg.jsonbench.jackson.JacksonBenchmark.serializar,495.5177
com.tfg.jsonbench.jackson.JacksonBenchmark.deserializar,517.3687
com.tfg.jsonbench.gson.GsonBenchmark.serializar,526.7525
com.tfg.jsonbench.gson.GsonBenchmark.deserializar,485.9583
```

Formato de cada línea: `paquete.Clase.metodo,julios`.

### Cómo leer cada CSV (por qué salen 3 líneas y cuál mirar)

Dentro de cada CSV aparecen 3 métodos, pero solo UNO cuenta:

- `...Benchmark.serializar` (o `deserializar`) → **ESTE es el bueno**, el método real. El de los ~500 julios.
- `...jmh_generated...jmhStub` → código que genera JMH para orquestar el bucle. Ruido, ignorar (gasta ~0-3 julios).
- `...preparar` → es el método `@Setup` (crear el objeto persona). No es lo que medimos, ignorar.

Regla simple: en cada CSV, quedarse con la línea cuyo método sea `serializar` o `deserializar` "a secas" (sin `jmh_generated` ni `preparar`). El comando `grep` del Paso 10 ya filtra justo esas.

### Cuidado: el "Program consumed X joules" del log NO es el dato

Durante la ejecución sale por consola `Program consumed 528,89 joules` (o similar) varias veces. Ese número es el consumo de **TODO el proceso** (arranque de la JVM, warmup, JMH, todo junto). El CSV filtrado (`app/`) es el que aísla solo el consumo de NUESTRO método, que es el que sirve para comparar de forma justa. No confundir uno con otro.

### MUY IMPORTANTE: una sola ejecución no vale

Estos números (~500 julios) son de UNA sola ejecución. Hay variabilidad real entre corridas (estado de la CPU, temperatura, RAPL, etc.). Cuando las diferencias son de ~30 julios sobre 500, podrían estar dentro del margen de ruido. Para la memoria hay que **repetir la medición varias veces (5-10 corridas), sacar la media y la desviación**, y solo entonces sacar conclusiones. Una sola ejecución sirve para verificar que el montaje funciona, no como resultado definitivo.

### Ideas para ampliar (para el análisis del TFG)

- Repetir cada medición varias veces (5-10) y promediar, para tener resultados estadísticamente sólidos (esto es lo más importante).
- Añadir objetos de distintos tamaños de payload (pequeño / mediano / grande), como pide el anteproyecto.
- Añadir un objeto "complejo" (anidado, con listas de listas) además del simple.
- Probar distintos valores de `stack-monitoring-sample-rate` para ver el trade-off precisión vs overhead (sección de limitaciones).
- Explorar los datos adicionales (`runtime/` para evolución temporal, `calltrees/` para hotspots) como material complementario.

## Guardar las mediciones buenas en resultados/

### Por qué hace falta (el tema del .gitignore)

El `.gitignore` es la lista de cosas que Git NO sube a GitHub. En este proyecto tiene `joularjx-result/`, lo cual está bien puesto (no queremos subir cada resultado de cada prueba suelta), pero tiene una consecuencia importante: **las mediciones solo existen en el portátil**. Si se pierde el disco, se pierden los datos.

La carpeta `resultados/` **no** está ignorada, así que lo que se meta ahí sí viaja a GitHub. De ahí la rutina: cuando una tanda de mediciones sea buena (de las que van a la memoria), se copia a `resultados/` y queda a salvo y versionada.

### ANTES de copiar: recuperar la propiedad de los ficheros

Como el agente se ejecuta con `sudo` (hace falta para leer RAPL), **todo lo que escribe JoularJX queda a nombre de root**. Al intentar mover o copiar esas carpetas con el usuario normal sale `Permiso denegado`.

Solución, cada vez que se termina una tanda de mediciones:

```bash
sudo chown -R $USER:$USER ~/Escritorio/tfg/pruebas/jackson-gson-benchmark/joularjx-result
```

(Cambiar la ruta por la del proyecto que toque.) Si alguna vez sale "permiso denegado" sobre una carpeta de resultados, **siempre es esto**.

### Copiar la tanda con nombres legibles

Las carpetas que genera JoularJX se llaman tipo `14879-1787756656320` (PID + timestamp), que no dice nada. Al copiarlas se les pone un nombre que se entienda.

```bash
cd ~/Escritorio/tfg
mkdir -p resultados/2026-08-24_jackson-gson_run1

cp -r pruebas/jackson-gson-benchmark/joularjx-result/CARPETA_1 \
      resultados/2026-08-24_jackson-gson_run1/gson-deserializar
cp -r pruebas/jackson-gson-benchmark/joularjx-result/CARPETA_2 \
      resultados/2026-08-24_jackson-gson_run1/gson-serializar
cp -r pruebas/jackson-gson-benchmark/joularjx-result/CARPETA_3 \
      resultados/2026-08-24_jackson-gson_run1/jackson-deserializar
cp -r pruebas/jackson-gson-benchmark/joularjx-result/CARPETA_4 \
      resultados/2026-08-24_jackson-gson_run1/jackson-serializar
```

Notas:
- `cp -r` copia la carpeta entera con todo lo de dentro (no solo el CSV). Merece la pena: dentro va también `runtime/`, `calltrees/` y `evolution/`, que hoy no usamos pero que pueden hacer falta en el análisis. Pesan poco.
- Lo de `run1` es a propósito: como hay que repetir las mediciones varias veces para promediar, luego habrá `run2`, `run3`... y así no se mezclan.
- Para saber qué carpeta es cuál, o se mira el log de la ejecución (JoularJX imprime `Results will be stored in joularjx-result/XXXX/` antes de cada benchmark), o se mira dentro del CSV qué método aparece.

### Generar un resumen con las cifras clave

Esto rebusca dentro de las cuatro carpetas las líneas que importan (las de julios de cada método) y las junta en un solo fichero, para no tener que abrir cuatro CSV distintos:

```bash
cd ~/Escritorio/tfg
grep -rh "Benchmark\.\(serializar\|deserializar\)," \
  resultados/2026-08-24_jackson-gson_run1/*/app/total/methods/joularJX-*-filtered-methods-energy.csv \
  > resultados/2026-08-24_jackson-gson_run1/resumen.csv

cat resultados/2026-08-24_jackson-gson_run1/resumen.csv
```

El `>` significa "guarda la salida en este fichero" en vez de imprimirla por pantalla.

### Cómo queda al final

```
resultados/
└── 2026-08-24_jackson-gson_run1/
    ├── gson-serializar/          → copia completa de la medición
    ├── gson-deserializar/
    ├── jackson-serializar/
    ├── jackson-deserializar/
    └── resumen.csv               → las 4 cifras de julios, juntas
```

Y cuando se repitan las mediciones, al lado irán `2026-XX-XX_jackson-gson_run2/`, `run3/`, etc.

## Siguiente paso pendiente

El montaje completo funciona de punta a punta. Lo que queda es el trabajo de análisis del TFG: repetir las mediciones varias veces (run2, run3...) para promediar, ampliar los casos de prueba (tamaños de payload, objeto complejo), ir guardando cada tanda en `resultados/`, y redactar el análisis comparativo (consumo por operación, hotspots, limitaciones).

## pom.xml completo de referencia

Este es el `pom.xml` entero, con las dependencias (Paso 2 de la sección de Maven) y los dos plugins (Paso 5) ya montados juntos. Si en algún momento hay dudas de dónde va cada cosa, este es el fichero final tal cual debe quedar:

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

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.openjdk.jmh</groupId>
                            <artifactId>jmh-generator-annprocess</artifactId>
                            <version>1.37</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <finalName>benchmarks</finalName>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>org.openjdk.jmh.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
