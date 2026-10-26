# moto-tracker

Sistema propio para ubicar la moto con el rastreador **Jimi IoT JM-VL03**, sin depender de la plataforma del vendedor.

```
JM-VL03 ──TCP 5023 (GT06)──▶ Traccar ──webhook JSON──▶ Backend (Spring Boot) ──▶ PostgreSQL + PostGIS
                                ▲                          │
                                └──── (comandos, futuro) ──┤
                                                           └──REST + SSE──▶ App web (PWA) · Telegram
```

- **Traccar** solo traduce el protocolo del equipo. Toda la lógica vive en el backend.
- **Backend** con arquitectura hexagonal: el dominio y los casos de uso no dependen de Spring ni de Traccar.
- **App web** instalable en el celular: mapa en vivo, recorrido del día, zonas (geocercas) y alertas.
- **Alertas** por Telegram: salida o entrada a una zona, moto que se mueve apagada, SOS y corte de alimentación.

## Módulos

| Módulo | Qué contiene | Depende de |
| --- | --- | --- |
| `tracker-domain` | `Position`, `Geofence`, `Alert`, `AlertRules`. Java puro | nada |
| `tracker-application` | Puertos de entrada y salida, `TrackingService` | dominio |
| `tracker-adapter-traccar` | Webhook que recibe posiciones y eventos de Traccar | aplicación, Spring MVC |
| `tracker-adapter-persistence` | Repositorio JDBC y migraciones Flyway | aplicación, JDBC |
| `tracker-adapter-web` | API REST y tiempo real (SSE) para la app | aplicación, Spring MVC |
| `tracker-adapter-telegram` | Notificador de alertas | aplicación |
| `tracker-bootstrap` | Arranque, seguridad, configuración y la app web estática | todos |

Para reemplazar Traccar por un decodificador propio basta con otro adaptador que llame a `IngestPositionUseCase`.

## Por qué la moto puede aparecer donde no está

Cuando el equipo no tiene señal GPS (bajo techo, recién encendido) igual manda posiciones, pero marcadas como
**inválidas** o **aproximadas** (calculadas por antenas). Traccar las dibuja como si fueran reales. Este sistema
las clasifica (`GPS`, `NETWORK`, `INVALID`) y en el mapa solo muestra como "la moto" la última posición GPS válida;
la aproximada aparece aparte, como un círculo punteado.

## Desarrollo local

Requisitos: Java 21, Maven 3.9 y Docker.

```bash
mvn verify                       # compila y corre las pruebas

# Todo el sistema en tu PC (con APP_DOMAIN=localhost y TRACCAR_DOMAIN=traccar.localhost en el .env)
cd deploy
cp .env.example .env             # y edita los valores
docker compose up -d --build
```

Probar todo el flujo sin la moto, con el simulador GT06 incluido:

```bash
python3 tools/gt06sim.py localhost 5023 862476051738192
```

## Despliegue en un VPS

1. Crea un VPS con **Ubuntu 24.04** y 2 GB de RAM.
2. Entra por SSH, clona este repositorio y ejecuta:
   ```bash
   cd moto-tracker/deploy
   sudo bash setup-vps.sh
   ```
   El script instala Docker, abre los puertos 22, 80, 443 y 5023, genera los secretos en `deploy/.env`
   y levanta todo con HTTPS automático (usa dominios gratuitos `sslip.io` con la IP del VPS).
3. Abre `https://traccar.<ip-con-guiones>.sslip.io` **de inmediato**, crea el usuario administrador
   (el primero que se registra es el administrador) y agrega el dispositivo con el IMEI como identificador.
4. Envía al GPS por SMS: `SERVER,0,<IP_DEL_VPS>,5023,0#`. El APN de Claro ya quedó configurado
   (`APN,internet.comcel.com.co#`).
5. Abre `https://moto.<ip-con-guiones>.sslip.io` en el celular, ingresa y usa "Agregar a la pantalla de inicio".

### Telegram (opcional)

1. En Telegram, habla con `@BotFather`, crea un bot y copia el token.
2. Escríbele cualquier cosa a tu bot y abre `https://api.telegram.org/bot<TOKEN>/getUpdates` para ver tu `chat.id`.
3. Pon ambos valores en `deploy/.env` y ejecuta `docker compose up -d`.

## Comandos SMS útiles del JM-VL03

| Comando | Uso |
| --- | --- |
| `STATUS#` | Estado: `GPRS:Link Up` significa que tiene internet |
| `GPRSSET#` | APN y servidor configurados |
| `WHERE#` | Ubicación actual por SMS (sirve aunque el servidor esté caído) |
| `APN,internet.comcel.com.co#` | APN de Claro Colombia, **sin** usuario ni clave |
| `SERVER,0,<ip>,5023,0#` | Apunta el equipo al servidor |

No envíes `RELAY` ni `FACTORY` sin un plan: el primero corta el motor y el segundo borra la configuración.
