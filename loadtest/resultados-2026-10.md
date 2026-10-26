# Resultados de la prueba de carga — octubre de 2026

Entorno: equipo de desarrollo (Windows 11), Oracle Free 23 en contenedor `edi-oracle-dev`, backend
`formulario-edi-backend.jar` (Java 21, pool Hikari por defecto = 10 conexiones) en el puerto 8090
(el 8080 estaba ocupado por otro servicio), k6 `grafana/k6:2.2.0` en Docker. Datos ficticios.
Escenario: rampa a 50 usuarios virtuales en 1 min, 2 min sostenidos, 164 encuestados distintos,
pausa de 4–8 s por página. Sin errores en las dos ejecuciones (3772 peticiones, 0 fallos, 100 % de
verificaciones correctas).

## Ejecución 2 (referencia, JVM ya caliente)
| Endpoint | p50 (ms) | p95 (ms) | p99 (ms) | máx (ms) |
| :-- | --: | --: | --: | --: |
| `GET /auth/csrf` | 1,4 | 2,3 | 2,6 | 3,0 |
| `POST /auth/login` | 3,5 | 5,2 | 6,4 | 7,6 |
| `GET /survey/rules` | 1,5 | 2,5 | 3,2 | 4,3 |
| `GET /survey/progress` | 6,8 | 9,2 | 10,6 | 15,6 |
| `GET /survey/pages/{p}` | 6,7 | 9,5 | 12,8 | 24,3 |
| `PUT /survey/pages/{p}/answers` | 17,1 | 37,9 | 41,5 | 84,5 |
| `POST /survey/finish` | 45,1 | 60,5 | 83,0 | 86,2 |

Tasa de error 0 %; 15,7 peticiones/s en promedio (0,7 flujos/s). Umbrales: cumplidos.

## Ejecución 1 (justo después de arrancar el backend)
Mismo escenario, JVM fría: `GET page` p50 9,9 / p95 42,3 / p99 74,4 ms; `PUT answers` p50 31,0 /
p95 212,4 / p99 423,7 ms (máx. 1,1 s); global p95 161 ms. Error 0 %. Umbrales cumplidos.

## Backend
- Sin evidencia de saturación del pool Hikari: no hubo errores ni advertencias en el log
  (todo en INFO) ni timeouts de conexión. Hikari no publica métricas (solo se expone `health`), así
  que no se midió el uso del pool ni su cola; con 50 usuarios y pausas de 4–8 s la concurrencia
  real de consultas es baja.
- `finish` es el endpoint más lento (p50 45 ms), por escribir observaciones y calcular el radicado.

## Interpretación
- A 50 usuarios simultáneos el flujo cumple holgadamente los umbrales (p95 de PUT 38–212 ms contra
  1000 ms; GET de página 10–42 ms contra 500 ms), con 0 % de errores.
- La diferencia entre ejecuciones es calentamiento de la JVM y del pool: en producción conviene un
  smoke test tras cada despliegue antes de recibir tráfico.
- Limitaciones: es un único equipo con Oracle en contenedor, sin nginx ni red real, y con pausas de
  lectura que reducen la concurrencia efectiva (~15 peticiones/s). No demuestra capacidad con picos
  de miles de usuarios simultáneos; para eso repita con `THINK_MIN=0`, más `VUS` y más usuarios
  CARGA, y active métricas de Hikari/Micrometer en el ambiente de prueba.
