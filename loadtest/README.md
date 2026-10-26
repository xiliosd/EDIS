# Prueba de carga del flujo del encuestado

Mide el flujo completo de una persona encuestada contra el backend **local** con datos ficticios:
`csrf → login → reglas → avance → (GET página + PUT respuestas) × 9 páginas → terminar`.
No apunta a QA ni a producción. Herramienta: [k6](https://k6.io) `grafana/k6:2.2.0` (imagen oficial
de Docker Hub, publicada el 2026-08-10; nunca `latest`).

## Qué mide
- Latencia (p50, p95, p99) y tasa de error por endpoint (`csrf`, `login`, `rules`, `progress`,
  `get_page`, `put_answers`, `finish`).
- Escenario: sube a 50 usuarios virtuales en 1 minuto y se mantiene 2 minutos. Cada usuario virtual
  recorre el flujo con un encuestado distinto y pausa 4–8 s por página (lectura); por eso en la
  prueba completa se usan unos 165 de los 200 usuarios.
- Umbrales (informativos, el informe se genera igual): `http_req_failed < 1 %`, p95 de
  `PUT answers` < 1000 ms y p95 de `GET page` < 500 ms.

## Prerrequisitos
- Docker Desktop, contenedor `edi-oracle-dev` y backend corriendo (ver «Local sin contenedor de
  aplicación» en el [README principal](../README.md)). El backend recarga `db/dev/data.sql` al
  arrancar, lo que borra los usuarios de carga: **cárguelos después de que el backend esté UP**.
- Si el puerto 8080 está ocupado, arranque el backend con `SERVER_PORT=8090` y use `BASE_URL`.

## Cómo ejecutarla
1. Cargar los 200 usuarios ficticios (`CARGA001`..`CARGA200`, C_C `9100000001`..`9100000200`; clave
   `Cg#` + número a 5 dígitos, p. ej. `CARGA007` → `Cg#00007`, porque `PSW` es `VARCHAR2(8)`):
   ```bash
   docker cp db/dev/loadtest-users.sql edi-oracle-dev:/tmp/loadtest-users.sql
   docker exec edi-oracle-dev bash -c 'sqlplus -S $APP_USER/$APP_USER_PASSWORD@//localhost:1521/FREEPDB1 @/tmp/loadtest-users.sql'
   ```
2. Ejecutar k6 (desde la raíz del repositorio):
   ```bash
   docker run --rm -v "$PWD/loadtest:/loadtest:ro" \
     -e BASE_URL=http://host.docker.internal:8080 \
     grafana/k6:2.2.0 run /loadtest/encuestado.js
   ```
   Variables opcionales: `BASE_URL` (por defecto `http://host.docker.internal:8080`), `VUS` (50),
   `RAMP` (1m), `HOLD` (2m), `THINK_MIN` / `THINK_MAX` (segundos de pausa por página, 4 y 8),
   `USER_COUNT` (200). Para una prueba rápida: `-e VUS=1 -e RAMP=1s -e HOLD=1s -e THINK_MIN=0
   -e THINK_MAX=0.1`. En Git Bash para Windows anteponga `MSYS_NO_PATHCONV=1`.
   Para exportar el resumen en JSON (no se versiona) agregue `-v <carpeta>:/out` y
   `--summary-export=/out/summary.json`.

## Reiniciar los datos
Cada página se guarda una sola vez: repetir la prueba sin limpiar produce 409. Antes de cada
ejecución:
```bash
docker cp loadtest/reset.sql edi-oracle-dev:/tmp/reset.sql
docker exec edi-oracle-dev bash -c 'sqlplus -S $APP_USER/$APP_USER_PASSWORD@//localhost:1521/FREEPDB1 @/tmp/reset.sql'
```
`reset.sql` solo borra las respuestas de los C_C `9100000001`..`9100000200`.

## Cómo leer los resultados
- `http_req_duration{endpoint:...}`: `med` es el p50; compare `p(95)` y `p(99)` con los umbrales.
- `http_req_failed` y `checks`: cualquier valor distinto de 0 % merece revisión (401/409/422/5xx).
- `http_reqs`: peticiones totales y por segundo; `iterations`: flujos completos.
- El tiempo de cada iteración incluye las pausas de lectura; no es una medida de rendimiento.
- Un resultado de referencia está en [resultados-2026-10.md](resultados-2026-10.md). Para que sea
  comparable, ejecute siempre contra el mismo equipo y con el backend recién arrancado o ya caliente
  (la primera ejecución tras arrancar es más lenta).
