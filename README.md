<img width="1414" height="784" alt="portada" src="https://github.com/user-attachments/assets/61845a3a-7b36-4f36-9ae7-807ac078f8af" />


## ¿Qué hace?

Es un programa de mensajería en tiempo real con dos partes:

1. **Un servidor** — un programa que se queda corriendo en una computadora (o servidor en la nube), esperando que la gente se conecte. Es como el "anfitrión" de la fiesta: recibe a todos los invitados y reparte los mensajes a quien corresponda.

2. **Varios clientes** — la ventana gráfica que cada usuario abre en su propia computadora para escribir y leer mensajes. Cada persona que quiera chatear abre su propio cliente y se conecta al servidor.

Los usuarios pueden:
- Conectarse con un nombre de usuario.
- Crear una **sala de chat** (como un grupo temático) o unirse a una que ya exista.
- Enviar mensajes que **solo ven los miembros de esa sala** (no todo el servidor).
- Ver la lista de salas disponibles y cuántas personas hay en cada una.
- Recibir notificaciones cuando alguien entra o sale de la sala.
- Usar emojis con un solo clic.

https://github.com/user-attachments/assets/c8246225-fb87-4f6a-9510-870e82f38539

---

## ¿Por qué es interesante técnicamente?

La mayoría de los chats sencillos que se enseñan en clase usan **un hilo (thread) por cada cliente conectado**. Funciona, pero si tienes 10,000 usuarios, tendrías 10,000 hilos compitiendo por los recursos de la computadora — algo muy pesado.

Esta práctica resuelve eso usando **Java NIO (New I/O)**, una tecnología que permite que **un solo hilo** atienda a todos los clientes conectados al mismo tiempo, sin bloquearse esperando a ninguno en particular.

| Enfoque clásico (bloqueante) | Enfoque NIO (no bloqueante) |
|---|---|
| 1 hilo por cliente | 1 solo hilo para todos los clientes |
| El hilo se "congela" esperando datos | El hilo nunca espera: pregunta "¿quién tiene algo nuevo?" |
| No escala bien con miles de usuarios | Escala mucho mejor |

Es como la diferencia entre tener un mesero dedicado a cada mesa de un restaurante (ineficiente si hay 200 mesas) versus un solo mesero que va revisando rápidamente cuál mesa necesita algo y atendiéndola al vuelo.

---

## Estructura del proyecto

```
Practica5/
├── MANIFEST.MF                  ← Le dice a Java cuál es la clase principal
├── build.sh                     ← Script de compilación (Mac/Linux)
├── build.bat                    ← Script de compilación (Windows)
├── ChatNIO.jar                  ← Programa ya compilado, listo para ejecutar
└── src/
    ├── ChatApp.java             ← Decide si arrancar como servidor o cliente
    ├── Protocol.java            ← El "idioma" en que cliente y servidor se hablan
    ├── Message.java             ← Representa un mensaje de chat (quién, qué, cuándo)
    ├── User.java                ← Representa a un usuario conectado (lado servidor)
    ├── ChatRoom.java            ← Representa una sala de chat y sus miembros
    ├── Server.java              ← El servidor: motor NIO que atiende a todos
    ├── Client.java              ← El cliente: capa de red que habla con el servidor
    ├── LoginWindow.java         ← Ventanita donde escribes tu nombre, IP y puerto
    └── MainWindow.java          ← La ventana principal del chat
```

---

## ¿Cómo funciona?

### 1. El "idioma" que usan para comunicarse (el Protocolo)

Cliente y servidor no se entienden por telepatía: necesitan un formato de mensaje acordado. `Protocol.java` define ese formato. Cada línea de texto que se envían tiene esta forma:

```
COMANDO|argumento1|argumento2
```

Por ejemplo, si escribes "Hola a todos" en la sala "Java", el cliente le manda al servidor:
```
MESSAGE|Hola a todos
```

Y el servidor reenvía a todos los miembros de la sala:
```
MSG|Ana|Hola a todos
```

---

### 2. El servidor: un solo hilo, mil conversaciones

`Server.java` es el corazón técnico de la práctica. Usa una pieza central llamada **`Selector`**, que funciona como un vigilante que constantemente pregunta: *"de todos los clientes conectados, ¿cuáles tienen algo nuevo para mí?"*

El ciclo de vida del servidor es:

```
1. Abrir un "Selector" (el vigilante)
2. Abrir un canal que escucha conexiones nuevas
3. Bucle infinito:
     a. Preguntar al Selector: "¿algo pasó?"
     b. Por cada evento:
        - ¿Es una conexión nueva?  → aceptarla
        - ¿Un cliente mandó datos? → leerlos y procesarlos
        - ¿Hay datos pendientes por enviar a un cliente? → enviarlos
```

Esto se conoce como un **bucle de eventos (event loop)**, el mismo patrón que usan tecnologías como Node.js para manejar muchas conexiones con pocos recursos.

**El servidor mantiene en memoria:**
- Un mapa de **qué usuario corresponde a cada conexión**.
- Un mapa de **qué salas existen** y quién está en cada una.
- Una "cola de mensajes pendientes" por cada cliente (para no perder mensajes si el envío no se completa de una vez).

---

### 3. Procesar un comando paso a paso

Cuando llega, por ejemplo, `JOIN|Java`, el servidor (`processCommand` en `Server.java`) hace esto:

1. Busca qué usuario corresponde a esa conexión.
2. Si la sala "Java" no existe, responde con un error.
3. Si existe, saca al usuario de su sala anterior (si tenía una).
4. Lo agrega a la sala "Java".
5. Le confirma con `OK|Unido a: Java` y `ROOM|Java`.
6. Avisa a los demás miembros de la sala que alguien nuevo se unió (`JOINED|usuario`).

Cuando alguien manda un mensaje (`MESSAGE|texto`), el servidor lo reenvía **a todos los miembros de esa sala, incluido quien lo escribió** (para que vea su propio mensaje en el orden correcto).

---

### 4. El cliente: su propio mini-motor de red

`Client.java` funciona de forma parecida al servidor, pero más simple: solo tiene **una** conexión que vigilar (la suya con el servidor). También usa NIO, corriendo en un hilo separado para no congelar la ventana gráfica mientras espera mensajes.

El cliente expone funciones simples para la interfaz gráfica:
```java
client.sendCreate("Java");      // Crear una sala
client.sendJoin("Java");        // Unirse a una sala
client.sendMessage("Hola!");    // Enviar un mensaje
client.sendList();              // Pedir lista de salas
```

Y recibe notificaciones del servidor mediante **callbacks** (funciones que se ejecutan automáticamente cuando llega algo), que conectan directamente con la ventana gráfica para actualizar la pantalla en tiempo real.

---

## Cómo compilar y ejecutar

### Requisitos previos

- **Java JDK 11 o superior** (con `javac` y `jar` disponibles)
- No necesita ninguna librería externa

**Verificar instalación:**
```bash
java -version
javac -version
```

---

### Opción A — Ejecutar directamente (ya viene compilado)

```bash
unzip name.zip
cd name

# Ver instrucciones completas de uso abajo
java -jar ChatNIO.jar --server 9090
```

---

### Opción B — Compilar con el script incluido (Mac/Linux)

```bash
unzip name.zip
cd name

chmod +x build.sh
./build.sh
```

Esto genera (o regenera) el archivo `ChatNIO.jar`.

---

### Opción C — Compilar manualmente sin script

```bash
unzip name.zip
cd name

mkdir -p out
javac -encoding UTF-8 -d out src/*.java
jar cfm ChatNIO.jar MANIFEST.MF -C out .
```

---

## Cómo usar la aplicación paso a paso

### 1. Arrancar el servidor (solo una vez)

En una terminal, ejecuta:
```bash
java -jar ChatNIO.jar --server 9090
```

Verás en la terminal:
<p align="center">
  <img
    src="https://github.com/user-attachments/assets/720c5a56-c880-4373-9730-175c7cd27f57"
    alt="Captura de pantalla"
    width="500"
  />
</p>


El servidor se queda corriendo ahí, esperando conexiones. **No lo cierres** mientras quieras seguir chateando.

> [!NOTE]
> Si no escribes un número de puerto, se usará el **9090** por defecto.


---

### 2. Conectar el primer cliente

Abre **otra** terminal (deja la del servidor abierta) y ejecuta:
```bash
java -jar ChatNIO.jar
```

Se abrirá la ventana de login. Llena:
- **Usuario:** el nombre con el que quieres aparecer
- **IP del servidor:** `127.0.0.1` si el servidor corre en tu misma computadora
- **Puerto:** `9090` (o el que hayas usado al arrancar el servidor)

Presiona **Conectar**.

<p align="center">
  <img
    src="https://github.com/user-attachments/assets/4848d619-5472-4a67-b3ea-aa8a5d27d6e4"
    alt="Captura de pantalla"
    width="500"
  />
</p>

---

### 3. Conectar más clientes para probar el chat

Repite el paso 2 en nuevas terminales con nombres distintos para simular varios usuarios chateando entre sí.

> [!NOTE]
> Puedes probar todo esto en una sola computadora abriendo varias terminales, o conectar clientes desde computadoras distintas en la misma red usando la IP real del servidor en vez de `127.0.0.1`.

---

### 4. Crear y usar salas

1. Presiona **"Crear Sala"** y escribe un nombre, por ejemplo `Java`.
2. Desde otro cliente, presiona **"Unirse a Sala"** y escribe el mismo nombre `Java`.
3. Ambos ya están en la misma sala — escribe un mensaje y presiona **Enviar** o la tecla Enter.
4. El otro cliente verá el mensaje aparecer en tiempo real.
5. Presiona **"Ver Salas"** en cualquier momento para ver qué salas existen y cuántos usuarios tiene cada una.



---

## Arquitectura general

<img width="1408" height="768" alt="arquitectura" src="https://github.com/user-attachments/assets/08005bf0-4c39-4089-9b61-1a099d9cff9f" />


**Reglas importantes del sistema:**
- Un solo `Selector` en el servidor atiende a **todos** los clientes y **todas** las salas — no hay un hilo por cliente.
- Los mensajes de una sala solo llegan a los miembros de esa sala, nunca a otras salas.
- Si una sala se queda sin nadie, se elimina automáticamente.
- Si un cliente se desconecta abruptamente (cierra la ventana, se va internet, etc.), el servidor lo detecta y lo limpia de su sala sin que nadie tenga que hacer nada manualmente.

---


## Posibles errores y soluciones

| Error o situación | Causa probable | Solución |
|---|---|---|
| `No se pudo conectar a 127.0.0.1:9090` | El servidor no está corriendo, o el puerto/IP están mal escritos | Verifica que el servidor esté activo en otra terminal con el mismo puerto |
| `Nombre en uso` | Otro cliente ya está conectado con ese mismo nombre | Elige un nombre de usuario distinto |
| `Sala ya existe` | Intentaste crear una sala con un nombre que ya está en uso | Usa "Unirse a Sala" en vez de "Crear Sala", o elige otro nombre |
| `Sala no existe` | Escribiste mal el nombre al unirte | Usa "Ver Salas" para confirmar el nombre exacto |
| `No estás en una sala` | Intentaste enviar un mensaje sin haberte unido a ninguna sala | Crea o únete a una sala antes de escribir |
| El chat no avanza / parece "congelado" | El servidor se cerró o se perdió la conexión | Revisa que la terminal del servidor siga abierta y sin errores |
| `java: command not found` | Java no está instalado o no está en el PATH | Instala Java (ver sección de instalación arriba) |
| `Puerto inválido` | El puerto escrito no es un número entre 1 y 65535 | Usa un puerto válido, por ejemplo 9090 |

---

## Autores

Chat en tiempo real con salas, implementado sobre Java NIO (sockets no bloqueantes) y Swing.

**María Fernanda García Hernández | Mora Olvera Abraham**

<img width="2000" height="965" alt="b2b" src="https://github.com/user-attachments/assets/6b2e0963-b16f-4400-a449-fa9a39dcb198" />
