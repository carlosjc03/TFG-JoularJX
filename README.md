# JoularJX para el TFG — Guía para no perderse

## Qué es JoularJX

Es un agente de Java que se engancha a tu programa cuando lo arrancas y te dice **cuánta energía consume cada método**, sin tener que tocar tu código para nada. Perfecto para el TFG de comparar Jackson vs Gson.

## Por qué en Linux y no en Windows

Porque en Windows necesitas un driver + un programa intermedio (Scaphandre / WinPowerMonitor) para leer la energía de la CPU, y ese driver tiene un bug conocido con procesadores AMD Ryzen (issue abierto en su GitHub desde 2025, sin arreglo real). Da igual lo que instales, siempre falla con "función incorrecta". En Linux, el propio kernel expone la energía directamente vía RAPL, sin necesitar nada de eso. Menos piezas, menos lío.

Si algún día quieres intentarlo otra vez en Windows, el bug está documentado aquí: https://github.com/joular/WinPowerMonitor/issues/2

## Requisitos

- Ubuntu/Debian (o derivado)
- Kernel 5.11 o superior (para que RAPL funcione bien en Ryzen)
- CPU Intel o AMD Ryzen (o más nuevo)

Comprueba el kernel con:

```bash
uname -r
```

## Paso 1: instalar lo básico

Java, Maven y git, todo de una:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install openjdk-17-jdk maven git acl -y
```

Comprueba que se instaló bien:

```bash
java -version
mvn -version
```

(Con Java 17 vas sobrado, JoularJX solo pide 11+.)

## Paso 2: clonar y compilar JoularJX

```bash
cd ~/Escritorio
git clone https://github.com/joular/joularjx.git
cd joularjx
mvn clean install
```

Si al final te sale `BUILD SUCCESS`, perfecto. El jar te lo genera en `target/joularjx-3.1.0.jar` (el número de versión puede variar, mira dentro de `target/` con `ls target/` si no coincide).

## Paso 3: comprobar que RAPL funciona ANTES de meterte con Java

Esto te ahorra media hora de frustración si algo va mal más adelante. Sin instalar nada de Java, solo mirando si el kernel lee energía real:

```bash
sudo cat /sys/class/powercap/intel-rapl:0/energy_uj
```

Espera 2 segundos y vuelve a ejecutar el mismo comando. Si el número ha subido, RAPL funciona de verdad y puedes seguir tranquilo.

### (Opcional) Dar permisos para no usar sudo cada vez

Si te cansas de poner `sudo` en cada ejecución, puedes dar permiso de lectura a tu usuario (aunque se resetea cada vez que reinicias el PC, así que toca repetirlo cada sesión):

```bash
sudo setfacl -Rm u:$USER:r /sys/class/powercap/intel-rapl:*
```

Si esto te da problemas raros de permisos, no pasa nada, usa `sudo java ...` cada vez y ya está, funciona exactamente igual.

## Paso 4: configurar el filtro (config.properties)

El fichero `config.properties` está en la raíz del repo (`joularjx/config.properties`). Dentro tiene una línea que dice `filter-method-names=Package`, que es un placeholder de ejemplo, no algo real. Hay que cambiarla por el nombre de tu clase o paquete real, si no, la carpeta de resultados filtrados (`app/`) te va a salir vacía.

Para nuestra prueba con `Test.java` (sin paquete):

```bash
sed -i 's/filter-method-names=Package/filter-method-names=Test/' config.properties
```

**Importante:** cuando montes el proyecto de verdad (Jackson/Gson), cambia `Test` por el paquete real de tu proyecto (por ejemplo `com.tfg.jsonbench`). Esto **solo se hace una vez** por proyecto, no hace falta repetirlo en cada ejecución.

## Paso 5: probar con un programa mínimo

Crea un `Test.java` cualquiera que tarde un poco en ejecutarse (para que dé tiempo a medir algo):

```bash
nano Test.java
```

Pega esto:

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

Guarda con `Ctrl+O`, Enter, `Ctrl+X`. Compila y ejecuta con el agente enganchado:

```bash
javac Test.java
sudo java -javaagent:target/joularjx-3.1.0.jar Test
```

Si todo va bien, te sale algo como:

```
Program consumed 37,16 joules
```

## Paso 6: ver los resultados

Cada ejecución crea una carpeta nueva con un timestamp dentro de `joularjx-result/`. Mira cuál es la más reciente:

```bash
ls joularjx-result/
```

Y dentro de esa carpeta, lo que más te interesa es esto:

```bash
cat joularjx-result/CARPETA_QUE_TE_HAYA_SALIDO/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

(Cambia `CARPETA_QUE_TE_HAYA_SALIDO` por el nombre real que te haya salido en el `ls` de arriba.)

Deberías ver algo como:

```
Test.main,34.4440
```

Eso es: tu método, y los julios que ha consumido. Eso es justo lo que necesitas para el TFG.

### Qué carpetas tienes ahí dentro

- `app/total/methods/` → consumo total de TU código, ya filtrado. **Esta es la que más vas a usar.**
- `all/total/methods/` → consumo de TODO, incluido lo del JDK por debajo.
- `runtime/` → cómo evoluciona el consumo mientras se ejecuta el programa.
- `calltrees/` → consumo por árbol de llamadas completo, no solo método suelto.

## El flujo que vas a repetir siempre (guárdate esto)

Cada vez que quieras medir algo nuevo, en el 90% de los casos solo necesitas esto:

```bash
# 1. Compila tu código (solo si lo has cambiado)
javac TuClase.java
# o si es un proyecto Maven completo:
mvn package

# 2. Ejecuta con el agente
sudo java -javaagent:/ruta/a/joularjx-3.1.0.jar TuClasePrincipal

# 3. Mira los resultados (carpeta más reciente)
ls joularjx-result/
cat joularjx-result/CARPETA/app/total/methods/joularJX-*-filtered-methods-energy.csv
```

El `config.properties` (el filtro del paso 4) solo lo tocas UNA VEZ por proyecto, no en cada ejecución.

## Siguiente paso pendiente

Montar el proyecto real con Jackson y Gson (dependencias Maven + casos de prueba con JMH) y repetir todo este proceso sobre ese código para comparar el consumo de las dos librerías. Eso lo dejamos para otra sesión.
