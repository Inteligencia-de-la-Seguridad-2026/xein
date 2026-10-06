# Entrega de la máquina Xein al Blue Team

## Máquina preparada

Se ha construido una VM con **Ubuntu Server 22.04.5 LTS**, 2 vCPU, 5 GiB de RAM y disco virtual dinámico de 50 GiB. Contiene el proyecto en `/opt/xein`, Docker Engine, Compose, los tres servicios, el SQL privado y la configuración privada de pagos.

- `xein-lab.service` arranca el laboratorio automáticamente al encender Ubuntu.
- `xein-time-sync.service` refresca NTP después de que la red esté disponible; la red NAT debe permitir acceso al servidor horario.
- `xein-audit-rotation.timer` ejecuta la rotación de consultas MySQL cada quince minutos.
- Se han comprobado NTP, los 50 productos, una compra completa, el rechazo y aprobación de transferencias ficticias, el vínculo social del administrador y el aislamiento externo de 3306, 33060 y 9090.
- P1 y P2 tienen capturas de referencia verificadas en `/opt/xein/evidence`, con registros y hashes. Los datos activos se restauraron al SQL original después de las pruebas.
- La cuenta Linux de administración es `xein`, con contraseña y sudo estándar. Sus credenciales se entregan por separado; no se publican en Git.

La VM preparada se llama `Xein-Lab-Ready` y usa la red interna de VirtualBox `xein-lab`, con la IP fija `192.168.77.10`. Desde su ordenador anfitrión se abre la web en `https://localhost:18443/products`, mediante un reenvío NAT limitado a ese ordenador. Red y Blue deben conectar sus VMs a la misma red interna o adaptar la interfaz a la red aislada acordada. La red NAT se usa para mantenimiento y NTP, y la red Docker `backend` mantiene privados los servicios de datos y pagos.

Al copiar manualmente el SQL a Linux, su grupo debe permitir lectura al usuario MySQL del contenedor. En esta imagen se usa el grupo numérico 999 y permiso 640, manteniendo la carpeta del proyecto restringida. El archivo `.env` tiene permiso 600.

El archivo `.ova`, la contraseña de Ubuntu y las capturas contienen información privada del laboratorio. Se entregan por el canal privado acordado con el Blue Team.

## Qué representa este montaje

El informe inicial pide **dos máquinas Linux**, una para la web y otra para la base de datos y pagos. Esta variante empaqueta los tres servicios en **una VM Linux** con Docker Compose y dos redes internas (`dmz` y `backend`). Es más fácil de trasladar, pero no reproduce dos máquinas físicas o virtuales independientes. El grupo y el Blue Team deben aceptar expresamente esta variante antes de darla por equivalente al informe.

## Antes de importar o exportar la VM

1. Preparar una VM Linux de 64 bits con al menos 2 vCPU, 4 GiB de RAM y 15 GiB libres. Para los tres servicios juntos se recomienda disponer de más memoria y espacio si la máquina anfitriona lo permite.
2. Instalar Docker Engine y el complemento Compose. Habilitar Docker al arrancar la VM.
3. Configurar una interfaz de la red aislada del ejercicio con una IP conocida. En `.env`, poner esa IP en `XEIN_BIND_ADDRESS`. `127.0.0.1` solo sirve para pruebas dentro de la propia VM. Publicar en `0.0.0.0` abriría la web en todas sus interfaces.
4. Copiar por separado a la VM el archivo privado `database/xein-seed.sql` y el archivo privado `.env` con la tarjeta ficticia. El repositorio público no debe contener estos datos.
   El JAR ya compilado está en `delivery/xein.jar`, y `delivery/SHA256SUMS` permite comprobar su integridad junto con la del SQL.
5. Activar NTP en Linux y comprobarlo con `timedatectl`. Compartir la misma referencia horaria con el Blue Team.
6. Desde la raíz del proyecto, ejecutar `bash scripts/vm-preflight.sh` y resolver cada fallo antes de continuar.
7. Ejecutar `docker compose up --build -d` y `docker compose ps`. Los tres servicios deben estar levantados y MySQL y pagos deben indicar estado saludable.
8. Desde la VM y desde la máquina Red autorizada, comprobar que `https://<IP_DEL_LAB>:8443/products` responde. Comprobar que 3306 y 9090 **no** son accesibles desde la máquina Red. El puerto 8080 solo redirige a HTTPS.
9. Ejecutar `python3 scripts/smoke_lab.py --url https://<IP_DEL_LAB>:8443` para comprobar productos y transferencia ficticia. Para probar una compra completa, configurar en `.env` un usuario ficticio de prueba y ejecutar `python3 scripts/smoke_checkout.py --url https://<IP_DEL_LAB>:8443`.
10. Comprobar la aparición de `/var/log/xein/app.log` en el contenedor web y `general.log` en el volumen de MySQL. Acordar con el Blue Team cómo extraer y conservar estos ficheros y las capturas de red.

En la VM, programar cada 15 minutos `docker compose exec -T database sh /opt/xein/rotate-audit.sh` desde la carpeta del proyecto. El script rota el log general de MySQL al alcanzar 10 MB y conserva cinco copias. La tarea programada debe seguir funcionando tras un reinicio de la VM.

El equipo Blue debe colocar sensores de manera que vean los puntos P1 y P2 descritos en `NETWORK_TOPOLOGY.md`. Una captura de la interfaz de la VM no garantiza ver el tráfico entre contenedores; hay que verificarlo con una petición de prueba.

## Exportación

Tras superar las comprobaciones, apagar la VM de forma ordenada y exportarla a `.ova` desde VirtualBox o el hipervisor acordado. Volver a importarla en otra máquina y repetir los pasos 7 a 10 antes de entregarla. La exportación conserva el SQL y `.env` privados dentro de la VM: entregar la imagen solo al Blue Team por el canal acordado.

La importación de la imagen base preparada, el acceso desde Windows, NTP y los puntos de captura se han comprobado. Sigue pendiente validar el acceso desde la máquina Red en la red definitiva del ejercicio; esa comprobación debe hacerse antes de comenzar el ataque.
