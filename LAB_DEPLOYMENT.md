# Despliegue del laboratorio Xein

## Arranque

Con Docker Desktop encendido, ejecuta desde la raíz del repositorio:

```powershell
docker compose up --build -d
docker compose ps
```

Abre `https://localhost:8443/`. El puerto `8080` redirige a `8443`, tal como hace la aplicación original. El certificado es de prueba y el navegador mostrará una advertencia.

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
- Solo los puertos web `8080` y `8443` se publican. Por defecto escuchan únicamente en `127.0.0.1`. Para una máquina Red separada, establece `XEIN_BIND_ADDRESS` con la dirección de la interfaz del laboratorio aislado antes de arrancar.
- La base `xein` se crea y se carga desde `database/xein-seed.sql` cuando el volumen de MySQL está vacío. La cuenta de la aplicación recibe únicamente permisos de lectura y escritura de datos mediante `database/permissions.sql`. El volumen conserva los datos al reiniciar los contenedores.

Las claves incluidas son solo para esta práctica aislada. La contraseña de la cuenta MySQL de la aplicación se define en `database/permissions.sql` y `compose.yaml`; cámbiala en ambos archivos antes de crear un laboratorio nuevo si lo necesitas. La clave de administración se puede cambiar con `XEIN_DB_ROOT_PASSWORD` antes del primer arranque. Si el volumen ya existe, cambiar esas variables no cambia las claves guardadas en MySQL.

El estado inicial se ha exportado a `database/xein-seed.sql` para entregarlo al Blue Team. El archivo crea la base `xein`, las tablas y los datos ficticios. Hay que importarlo en una base vacía antes de arrancar la web del Blue Team. La web detecta que esos datos ya existen y no los duplica.

Los tres servicios escriben registros con hora UTC. Docker rota sus registros al llegar a 10 MB y conserva hasta cinco archivos por servicio. Esto limita su tamaño, pero el Blue Team debe copiar a su almacén de evidencias los registros y PCAP que necesite conservar para el análisis forense.

La zona horaria UTC no sincroniza los relojes por sí sola. Antes del ejercicio, el Blue Team debe comprobar la sincronización NTP de las dos máquinas Linux y del almacén de evidencias.

El diagrama con los segmentos, sensores previstos y puntos de captura está en `NETWORK_TOPOLOGY.md`.

## Simulador de pagos

El servicio está en `payment/server.py` dentro de este mismo repositorio. `GET /health` comprueba que responde, `POST /transactions` registra una operación ficticia y `GET /transactions` permite revisar las últimas 100 operaciones desde la red interna. No se conecta con servicios financieros reales.

Ejemplo de petición a `POST /transactions` desde un servicio de la red `backend`:

```json
{
  "reference": "lab-order-001",
  "payer": "demo-user",
  "payee": "xein-store",
  "amount_cents": 2599,
  "currency": "LAB"
}
```

La referencia impide crear dos operaciones iguales al repetir una petición. Cada operación queda guardada con su hora UTC e identificador y genera un registro útil para el Blue Team.

## Pendiente para el escenario completo

- La web todavía no llama al simulador al confirmar un pedido. El grupo debe decidir cómo encaja la operación de pago en el escenario antes de conectar ese flujo.
- Las reglas de Suricata, capturas y validación de alertas de la fase 2 corresponden a la configuración defensiva del Blue Team.
