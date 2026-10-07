# Entrega de la máquina Xein

## Versión vigente: 7 de octubre de 2026

El informe `-3` establece **una única VM Ubuntu Server 22.04.5** con tres contenedores Docker. La VM `Xein-Lab-Ready` usa 4 vCPU, 6 GiB de RAM y un disco dinámico de 50 GiB. Se reserva al menos 15 GiB libres para registros y capturas. La consola usa teclado español (`es`); no lleva escritorio gráfico.

En `/opt/xein` están la web, el simulador Python, el SQL privado y la configuración privada. El JAR está en `delivery/xein.jar`. `xein-lab.service` arranca los servicios, `xein-time-sync.service` refresca NTP y `xein-audit-rotation.timer` rota las consultas MySQL cada quince minutos.

La cuenta de administración Linux es `xein`; la contraseña se entrega por canal privado. `.env` usa permiso 600. El SQL usa 640 y grupo numérico 999 para permitir que MySQL lo importe. No publicar esos archivos ni la OVA en GitHub.

## Acceso desde Windows

1. Importar la OVA en VirtualBox, comprobando recursos y espacio libre.
2. Adaptador 1: NAT; adaptador 2: red interna llamada `xein-lab`.
3. Si el importador no conserva los reenvíos NAT, añadir `127.0.0.1:18443` hacia `192.168.77.10:8443`. Opcional para administración: `127.0.0.1:2222` hacia el puerto 22 de la VM.
4. Encender y esperar a que Docker arranque. Abrir `https://localhost:18443/products`. El certificado es de laboratorio y requiere aceptar la advertencia.

La IP del ejercicio es `192.168.77.10` en `enp0s8`. Otras VMs autorizadas conectadas a la misma red interna pueden entrar en `https://192.168.77.10:8443/products`. `8080` redirige a `8443`; el backend 3306/9090 no se publica. Para varios ordenadores, acordar una red aislada distinta y adaptar la IP de Ubuntu y `XEIN_BIND_ADDRESS` en `.env`.

## Comprobaciones dentro de Ubuntu

```bash
cd /opt/xein
bash scripts/vm-preflight.sh
docker compose ps
systemctl status xein-lab.service --no-pager
timedatectl show -p NTPSynchronized
```

Los tres contenedores deben responder; MySQL y pagos muestran `healthy`. El catálogo inicial tiene 50 productos. Las pruebas funcionales generan datos sintéticos, por lo que deben ejecutarse en una copia de validación antes del ejercicio. Conservar el SQL original y no reinicializar los volúmenes durante la recogida de evidencias.

Los cambios en GitHub **no actualizan una OVA ya descargada**. Para incorporar cambios a una copia existente, actualizar el proyecto y reconstruir la web, o importar la OVA nueva. No es necesario reinstalar Ubuntu.

## Registros y monitorización

Ver `NETWORK_TOPOLOGY.md` para interfaces, filtros y limitaciones de HTTPS. Se entrega soporte de captura compatible con Wireshark; no necesita escritorio en Ubuntu. El otro equipo conserva la responsabilidad de desplegar sus defensas. Las reglas y evidencias privadas de preparación del Grupo N no forman parte de su carpeta de entrega.

## Restauración

`docker compose down` conserva los datos. `docker compose down --volumes` **borra** el estado del laboratorio y solo se usa para reinicializar una copia de pruebas: el siguiente arranque importa el SQL privado. No usarlo durante un incidente ni antes de preservar evidencias.
