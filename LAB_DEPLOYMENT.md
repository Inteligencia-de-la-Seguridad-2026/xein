# Despliegue del laboratorio Xein

## Arranque

Antes del primer arranque, copia el SQL de entrega **por un canal privado** a `database/xein-seed.sql` y crea `.env` a partir de `.env.example`. El SQL y `.env` están excluidos de Git. En `.env` se configuran la IP de escucha y los datos ficticios de la tarjeta publicada para el ejercicio. No uses una tarjeta real.

Con Docker Desktop encendido, ejecuta desde la raíz del repositorio:

```powershell
docker compose up --build -d
docker compose ps
```

Abre `https://localhost:8443/`. El puerto `8080` redirige a `8443`, tal como hace la aplicación original. El certificado es de prueba y el navegador mostrará una advertencia. El archivo SQL privado es necesario para que la web arranque con el esquema y los datos acordados.

Para ver el registro de la web:

```powershell
docker compose logs -f web
```

Para detener el laboratorio sin borrar el volumen de MySQL:

```powershell
docker compose down
```

## Topología

- `web` ejecuta la aplicación Spring Boot con Java 17. Está conectada a las redes `dmz` y `backend`.
- `database` ejecuta MySQL 8 en `backend`. No publica el puerto de la base de datos al ordenador anfitrión.
- `payment` ejecuta el simulador de transacciones en Python por el puerto interno `9090`. Guarda los registros en un volumen y no publica el puerto al ordenador anfitrión.
- Solo los puertos web `8080` y `8443` se publican. Por defecto escuchan únicamente en `127.0.0.1`. En la VM de entrega, establece `XEIN_BIND_ADDRESS` con la IP de su interfaz del laboratorio aislado antes de arrancar. Evita publicar la web en interfaces ajenas al laboratorio.
- La base `xein` se crea y se carga desde `database/xein-seed.sql` cuando el volumen de MySQL está vacío. La cuenta de la aplicación recibe únicamente permisos de lectura y escritura de datos mediante `database/permissions.sql`. El volumen conserva los datos al reiniciar los contenedores.

Las claves incluidas son solo para esta práctica aislada. La contraseña de la cuenta MySQL de la aplicación se define en `database/permissions.sql` y `compose.yaml`; cámbiala en ambos archivos antes de crear un laboratorio nuevo si lo necesitas. La clave de administración se puede cambiar con `XEIN_DB_ROOT_PASSWORD` antes del primer arranque. Si el volumen ya existe, cambiar esas variables no cambia las claves guardadas en MySQL.

El estado inicial se entrega al Blue Team como `database/xein-seed.sql` por separado del repositorio público. El archivo crea la base `xein`, las tablas y los datos ficticios. Docker lo importa al inicializar un volumen de MySQL vacío. La web detecta que esos datos ya existen y no los duplica. El SQL no debe publicarse en un nuevo commit.

### Historial público pendiente de revisar

El SQL ya se publicó en commits anteriores, entre ellos `baab6ce` y `dbeb9f0`. Eliminarlo de la versión actual y añadirlo a `.gitignore` evita nuevas publicaciones, pero no lo retira del historial. El conjunto de datos sigue siendo recuperable desde esos commits y desde las copias existentes del repositorio.

Antes del ejercicio, el grupo debe decidir cómo retirar el conjunto completo del historial público y de los forks, o preparar un estado inicial distinto y actualizar de forma coherente la entrega privada y los informes. Reescribir el historial requiere coordinación porque cambia los identificadores de los commits y afecta a las copias de todos. La pista técnica ficticia prevista para el reconocimiento en GitHub debe mantenerse sin publicar la base de datos completa.

El ejecutable compilado para la entrega se deja en `delivery/xein.jar`, también excluido de Git. `delivery/SHA256SUMS` contiene los hashes del JAR y del SQL privado para comprobar que se han copiado sin cambios.

Los tres servicios escriben registros con hora UTC. Docker rota sus registros al llegar a 10 MB y conserva hasta cinco archivos por servicio. Esto limita su tamaño, pero el Blue Team debe copiar a su almacén de evidencias los registros y PCAP que necesite conservar para el análisis forense.

La web también escribe `/var/log/xein/app.log` en un volumen persistente con rotación. MySQL guarda las consultas en `general.log` dentro de su volumen de datos. El script `database/rotate-audit.sh` conserva hasta cinco archivos cuando el log alcanza 10 MB; el Blue Team debe programar su ejecución periódica en la VM y exportar los registros antes de que caduquen.

La zona horaria UTC no sincroniza los relojes por sí sola. Antes del ejercicio, el Blue Team debe comprobar la sincronización NTP de la VM Linux y del almacén de evidencias.

El diagrama con los segmentos, sensores previstos y puntos de captura está en `NETWORK_TOPOLOGY.md`.

## Simulador de pagos

El servicio está en `payment/server.py` dentro de este mismo repositorio. `GET /health` comprueba que responde, `POST /transactions` registra una compra ficticia y `GET /transactions` permite revisar las últimas 100 operaciones desde la red interna. Al confirmar un pedido, la web envía el pago al simulador desde el servidor; el navegador no puede acceder directamente a la red interna. Solo se comprueba el formato de la tarjeta para una compra ordinaria.

`POST /api/payments/transfer` en la web remite al simulador una transferencia de laboratorio. Para aprobarla, el simulador exige que número, fecha y CVV coincidan con la tarjeta ficticia configurada **solo en `.env`**. Una operación nueva aprobada devuelve HTTP 200 y queda registrada en SQLite y en los logs, sin guardar ni imprimir los datos de la tarjeta. Este endpoint forma parte del escenario autorizado, no es una pasarela bancaria real.

Ejemplo de petición a `POST /api/payments/transfer` a través de la web:

```json
{
  "reference": "lab-transfer-001",
  "payer": "lab-admin",
  "payee": "lab-destination",
  "amount_cents": 2599,
  "currency": "LAB",
  "card_number": "FICTITIOUS_CARD_FROM_OSINT",
  "expiry": "MM/YY",
  "cvv": "LAB_CVV"
}
```

La referencia impide crear dos operaciones iguales al repetir una petición. Cada operación queda guardada con su hora UTC e identificador y genera un registro útil para el Blue Team. Para una prueba real hay que sustituir los marcadores por los valores ficticios preparados para el laboratorio.

## Pendiente para el escenario completo

- Se ha construido una VM Ubuntu 22.04.5 y probado desde Windows el acceso a la web y el aislamiento del backend. Falta validar la comunicación desde la máquina Red y la red definitiva acordadas para el ejercicio. La configuración y la entrega se describen en `VM_HANDOFF.md`.
- Las reglas de Suricata, capturas y validación de alertas de la fase 2 corresponden a la configuración defensiva del Blue Team.
