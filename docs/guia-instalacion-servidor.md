# Guía paso a paso: instalar el Formulario EDI en otro equipo conectado a la base real

Esta guía es para alguien que nunca ha instalado la aplicación. Sígala en orden, sin saltarse pasos.
Cada paso termina con **"Cómo sé que salió bien"**: si no ve eso, no siga; vaya a la sección
[Si algo falla](#10-si-algo-falla).

> **Importante antes de empezar**
>
> - Esta guía conecta la aplicación a la **base de datos real** (con datos de personas). Trabaje con
>   cuidado y no copie claves ni datos en correos, chats ni capturas de pantalla.
> - Para **producción institucional** el paso formal sigue siendo el pipeline con el formato
>   FR-FT-GST-02 (`Requisitos_Entrega.xlsx`), RFC y aprobación del CAB (ver README, _Deployment_).
>   Esta guía sirve para montar un ambiente de QA o un servidor de prueba conectado a la base real.
> - **Nunca** use `docker-compose.yml` del repositorio para esto: es el de desarrollo, crea una
>   Oracle falsa y **borra y recarga** datos de prueba al arrancar. Aquí se usa un archivo aparte
>   (paso 6).

---

## 0. Qué necesita tener a la mano

Pídale estos datos a quien administra la base (o búsquelos usted) **antes** de empezar:

| Dato                                               | Ejemplo (inventado)                                    | Para qué                                                |
| -------------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------- |
| Servidor de Oracle (nombre o IP)                   | `10.0.0.50`                                            | Saber a dónde conectarse                                |
| Puerto de Oracle                                   | `1521`                                                 | Casi siempre es 1521                                    |
| Nombre del servicio de Oracle                      | `ORCLPDB`                                              | Va en la URL de conexión                                |
| Usuario de la aplicación                           | `USUARIO_APP`                                          | Con el que la aplicación entra a la base                |
| Clave de ese usuario                               | (secreta)                                              | No la escriba en ningún lado salvo en el archivo `.env` |
| Esquema dueño de las tablas                        | `DANE_DIRPEN`                                          | Donde están `EDIN_MUESTRA`, `FRM_*`, `GRAL_*`           |
| Año de la encuesta activa                          | `2026`                                                 | La aplicación atiende un año a la vez                   |
| Dirección con la que la gente abrirá la aplicación | `http://10.0.0.80:8081` o `https://edi.ejemplo.gov.co` | Va en la configuración de seguridad                     |
| ¿Es QA o producción?                               | QA                                                     | Decide si se puede editar el cuestionario               |

**Permisos que debe tener el usuario de la aplicación** en la base (confírmelos, paso 3):

- Leer (`SELECT`): `FRM_*`, `GRAL_*`, `EDIN_MUESTRA` y las tablas de capítulos `EDIN_20_CAP_*`.
- Guardar respuestas (`INSERT` y `UPDATE`): tablas de capítulos `EDIN_20_CAP_*`.
- Novedades y claves (`UPDATE`): `EDIN_MUESTRA` (columnas `IDFNOV`, `NOV_OTRA_CAUSA`, `LGN`, `PSW`).
- En **producción**, sobre `FRM_*` solo `SELECT` (el cuestionario llega con el paquete de promoción).

**El equipo** debe tener: Windows 10/11 de 64 bits (o un servidor Linux), 8 GB de RAM o más, 10 GB
libres en disco, permiso de administrador para instalar programas y acceso por red al servidor de
Oracle.

---

## 1. Instalar los programas

Solo se necesitan dos programas: **Git** (para descargar el código) y **Docker Desktop** (para
correr la aplicación sin instalar Java, Node ni nginx a mano).

### 1.1 Git

1. Entre a <https://git-scm.com/download/win> y descargue el instalador de 64 bits.
2. Ejecútelo y deje todas las opciones como vienen ("Next" hasta el final).

### 1.2 Docker Desktop

1. Entre a <https://www.docker.com/products/docker-desktop/> y descargue Docker Desktop para Windows.
2. Ejecútelo. Si pregunta, deje marcada la opción **"Use WSL 2"**.
3. Reinicie el equipo cuando lo pida.
4. Abra **Docker Desktop** desde el menú Inicio y espere a que abajo a la izquierda diga
   **"Engine running"** (motor en marcha). Puede tardar uno o dos minutos la primera vez.

> En un servidor Linux, instale `git`, `docker` y el complemento `docker compose` con el gestor de
> paquetes del sistema; los demás pasos son iguales (cambie PowerShell por la terminal).

### Cómo sé que salió bien

Abra **PowerShell** (tecla Windows, escriba `PowerShell`, Enter) y escriba, uno por uno:

```powershell
git --version
```

```powershell
docker --version
```

```powershell
docker compose version
```

Los tres deben responder con un número de versión. Si alguno dice "no se reconoce como comando",
cierre PowerShell, ábralo de nuevo y repita; si sigue igual, reinstale ese programa.

---

## 2. Descargar el código

1. En PowerShell, vaya a la carpeta donde quiere dejar el proyecto. Por ejemplo `C:\apps`:

   ```powershell
   mkdir C:\apps
   ```

   ```powershell
   cd C:\apps
   ```

2. Descargue el código (le pedirá iniciar sesión en GitHub si el repositorio es privado):

   ```powershell
   git clone https://github.com/xiliosd/EDIS.git formulario-web
   ```

3. Entre a la carpeta:

   ```powershell
   cd C:\apps\formulario-web
   ```

### Cómo sé que salió bien

```powershell
dir
```

Debe ver, entre otras, las carpetas `backend`, `frontend`, `db`, `docs` y el archivo `README.md`.

---

## 3. Comprobar que el equipo llega a la base y que la base está lista

### 3.1 ¿Llego al servidor de Oracle?

Cambie `10.0.0.50` por su servidor y `1521` por su puerto:

```powershell
Test-NetConnection 10.0.0.50 -Port 1521
```

**Cómo sé que salió bien:** al final dice `TcpTestSucceeded : True`. Si dice `False`, el equipo no
ve la base (firewall, VPN o red). Hable con soporte de red antes de seguir; nada de lo que sigue va a
funcionar.

### 3.2 ¿La base tiene lo que la aplicación espera?

En el repositorio está el script **de solo lectura** `db/verificacion/verificar-base-produccion.sql`.
No cambia nada en la base: solo consulta el diccionario de Oracle y cuenta filas, sin mostrar datos
de personas.

1. Ábralo con la herramienta de base de datos que use (SQL Developer, SQL*Plus, DBeaver…),
   conectado con un usuario que pueda leer el esquema `DANE_DIRPEN`.
2. Ejecútelo completo.
3. Lea el comentario de cada consulta: dice qué resultado esperar y qué hacer si no se cumple.
   Revise en especial:
   - el juego de caracteres (`WE8ISO8859P15` en producción);
   - consultas **13 a 18** (Fase 4): mismo identificador de usuario en usuarios y accesos, cédulas
     repetidas en los capítulos B y H, que "sin novedad" sea vacío y no 0, códigos de entidad y
     departamento numéricos, cómo está guardada la "otra causa" y departamentos fuera del catálogo;
   - el código de "otra causa" en `GRAL_NOVEDAD` (anótelo: lo necesita en el paso 5).

**Cómo sé que salió bien:** todas las consultas dan el resultado esperado que dice su comentario.
Si alguna no, anote cuál y resuélvala antes de poner la aplicación al servicio de usuarios.

---

## 4. Generar las dos claves secretas de la aplicación

La aplicación necesita dos claves largas al azar, distintas entre sí: `JWT_SECRET` (firma la
sesión) y `RECEIPT_KEY` (firma el radicado que recibe el encuestado al terminar).

En PowerShell, ejecute este bloque **dos veces** y copie cada resultado aparte (en un bloc de notas
temporal que borrará al terminar):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Cada vez sale un texto distinto de 64 caracteres, por ejemplo `kQ3v...==` (no use este ejemplo).

> **Ojo con `RECEIPT_KEY`:** una vez el ambiente esté en uso, **no la cambie nunca**. Si cambia, los
> radicados ya entregados no se pueden volver a calcular. Guárdela en el gestor de secretos o el
> lugar seguro que use su equipo.

---

## 5. Crear el archivo de configuración `.env`

El archivo `.env` guarda la configuración y las claves. **Nunca se sube a GitHub** (git lo ignora) y
nunca se envía por correo.

1. Cree el archivo a partir del ejemplo:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Ábralo con el Bloc de notas:

   ```powershell
   notepad .env
   ```

3. **Borre todo su contenido** y pegue esto, cambiando lo que está entre `<...>`:

   ```ini
   # --- Conexión a la base real ---
   DB_URL=jdbc:oracle:thin:@//<servidor>:<puerto>/<servicio>
   DB_USER=<usuario de la aplicación>
   DB_PASSWORD=<clave de ese usuario>
   DB_SCHEMA=DANE_DIRPEN

   # --- Encuesta activa ---
   SURVEY_CODE=EDIN
   SURVEY_YEAR=<año, por ejemplo 2026>
   RULES_DIR=/data/rules
   REPORTS_DIR=/data/reports
   RULES_SEED_DIR=
   NOVELTY_OTHER_CAUSE_CODE=<código de "otra causa" que anotó en el paso 3.2>
   # true solo en QA (allí se edita el cuestionario); false en producción.
   QUESTIONNAIRE_WRITES_ENABLED=false

   # --- Seguridad ---
   JWT_SECRET=<primera clave del paso 4>
   RECEIPT_KEY=<segunda clave del paso 4>
   SESSION_MINUTES=30
   # true si la gente entra por https://; false SOLO si entra por http:// (ver nota abajo).
   COOKIE_SECURE=true
   # La dirección EXACTA con la que se abre la aplicación en el navegador, sin "/" al final.
   CORS_ALLOWED_ORIGIN=<por ejemplo https://edi.ejemplo.gov.co>
   SWAGGER_ENABLED=false
   SWAGGER_PUBLIC=false
   LOG_FORMAT=ecs
   ```

4. Guarde (Ctrl+G o Ctrl+S) y cierre el Bloc de notas.

**Explicación de los valores que más se equivocan:**

- `DB_URL`: ejemplo completo `jdbc:oracle:thin:@//10.0.0.50:1521/ORCLPDB`. Las dos barras `//`
  después de la `@` van siempre.
- `CORS_ALLOWED_ORIGIN`: debe ser **idéntico** a lo que se ve en la barra del navegador: esquema
  (`http` o `https`), nombre o IP y puerto si lo hay. `http://10.0.0.80:8081` y
  `http://10.0.0.80:8081/` **no** son lo mismo (sobra la barra final). Si no coincide, nadie puede
  iniciar sesión.
- `COOKIE_SECURE`: con `true` el navegador solo envía la sesión por `https://`. Si en una prueba
  interna la gente entra por `http://`, póngalo en `false`; **en producción siempre `https://` y
  `true`**.
- `QUESTIONNAIRE_WRITES_ENABLED`: en producción `false`. Novedades, claves y cortes funcionan igual
  con `false`.

### Cómo sé que salió bien

```powershell
Select-String -Path .env -Pattern '<|cambie|genere'
```

No debe mostrar nada. Si muestra líneas, todavía quedan valores sin cambiar.

---

## 6. Crear el archivo de arranque para el servidor

En la carpeta `C:\apps\formulario-web`, cree el archivo `docker-compose.servidor.yml`:

```powershell
notepad docker-compose.servidor.yml
```

(si pregunta si desea crearlo, diga **Sí**). Pegue exactamente esto y guarde:

```yaml
# Arranque contra la base REAL. No crea Oracle ni carga datos de prueba.
name: formulario-edi-servidor

services:
  backend:
    build: ./backend
    env_file: .env
    restart: unless-stopped
    volumes:
      - rules-data:/data/rules
      - reports-data:/data/reports

  frontend:
    build: ./frontend
    restart: unless-stopped
    environment:
      BACKEND_URL: http://backend:8080
    ports:
      - "8081:8080"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  rules-data:
  reports-data:
```

Qué hace, en palabras simples:

- **backend**: el servidor de la aplicación. Lee la configuración de `.env` y se conecta a la base
  real. Guarda las reglas del cuestionario y los cortes de cobertura en dos "volúmenes" (carpetas
  que Docker conserva aunque se apague el equipo).
- **frontend**: la página web. Atiende en el puerto **8081** del equipo y le pasa al backend lo que
  empieza por `/api`.
- A diferencia del archivo de desarrollo, **no** hay Oracle de prueba ni carga de `db/dev/*.sql`.
- Para usar otro puerto (por ejemplo el 80), cambie solo el primer número: `"80:8080"`, y ajuste
  `CORS_ALLOWED_ORIGIN` en `.env`.

### Cómo sé que salió bien

```powershell
docker compose -f docker-compose.servidor.yml config --quiet
```

No debe mostrar nada (sin errores). Si dice algo de `yaml`, revise que la sangría (espacios al
inicio de las líneas) quedó igual que arriba: son espacios, no tabulaciones.

---

## 7. Construir y arrancar la aplicación

Asegúrese de que Docker Desktop está abierto ("Engine running") y ejecute:

```powershell
docker compose -f docker-compose.servidor.yml up -d --build --wait
```

- La **primera vez** tarda entre 5 y 15 minutos: descarga y compila todo. Las siguientes, mucho menos.
- `--wait` hace que el comando termine solo cuando todo está sano.

### Cómo sé que salió bien

```powershell
docker compose -f docker-compose.servidor.yml ps
```

Los dos (`backend` y `frontend`) deben decir **`healthy`**. Luego abra el navegador en la dirección
que puso en `CORS_ALLOWED_ORIGIN` (en el mismo equipo: <http://localhost:8081>). Debe ver la
pantalla de ingreso.

Si el backend no queda `healthy`, mire qué dice:

```powershell
docker compose -f docker-compose.servidor.yml logs backend --tail 100
```

y busque el mensaje en [Si algo falla](#10-si-algo-falla).

---

## 8. Probar que funciona con la base real

Haga estas pruebas con usuarios reales que existan en la base (`GRAL_USUARIOS_ADM` para
administradores, `EDIN_MUESTRA` para encuestados). Pídale las credenciales a su dueño; no las
anote en ningún documento.

1. **Administrador nacional (ROL 1):** ingrese como administrador. Debe ver el menú con
   _Cuestionario_, _Reportes_ y _Claves_. Abra **Reportes → Cobertura**: deben aparecer
   departamentos y entidades con cifras.
2. **Operativo (ROL 3 o 4):** ve _Reportes_ de todos los departamentos, sin _Claves_ ni
   _Gestión de novedades_.
3. **Territorial (ROL 2):** ve _Reportes_ solo de sus departamentos y _Gestión de novedades_.
   No asigne novedades "de prueba" a funcionarios reales.
4. **Encuestado:** solo si tiene un usuario de prueba acordado. Recuerde que **cada página se
   guarda una sola vez** y queda en la base real.

### Cómo sé que salió bien

Cada usuario ve solo lo que dice arriba, y las cifras de cobertura tienen sentido frente a lo que
se conoce del operativo (las diferencias con el PHP están explicadas en
`docs/fase-4-diferencias-reportes.md`).

---

## 9. Uso diario

Todos los comandos se ejecutan en PowerShell dentro de `C:\apps\formulario-web`.

| Quiero…                       | Comando                                                                 |
| ----------------------------- | ----------------------------------------------------------------------- |
| Ver si está corriendo         | `docker compose -f docker-compose.servidor.yml ps`                      |
| Apagarla (sin perder nada)    | `docker compose -f docker-compose.servidor.yml down`                    |
| Encenderla de nuevo           | `docker compose -f docker-compose.servidor.yml up -d --wait`            |
| Ver los mensajes del servidor | `docker compose -f docker-compose.servidor.yml logs backend --tail 200` |

> **Nunca** use `down -v`: la `-v` borra los volúmenes, es decir, las reglas del cuestionario y el
> histórico de cortes.

Si el equipo se reinicia, Docker Desktop vuelve a encender la aplicación sola (`restart:
unless-stopped`) siempre que Docker Desktop esté configurado para iniciar con Windows
(_Settings → General → Start Docker Desktop when you sign in_).

### Actualizar a una versión nueva

```powershell
git pull
```

```powershell
docker compose -f docker-compose.servidor.yml up -d --build --wait
```

Antes de actualizar, lea el `CHANGELOG.md` por si la versión nueva pide algo adicional (por
ejemplo, correr un script en la base).

### Copia de seguridad de las reglas y los cortes

Crea dos archivos `.tgz` en la carpeta actual:

```powershell
docker run --rm -v formulario-edi-servidor_rules-data:/datos -v ${PWD}:/copia alpine tar czf /copia/reglas.tgz -C /datos .
```

```powershell
docker run --rm -v formulario-edi-servidor_reports-data:/datos -v ${PWD}:/copia alpine tar czf /copia/cortes.tgz -C /datos .
```

Guárdelos en el lugar de respaldo de su equipo. Los cortes solo tienen cifras (sin datos de
personas); las reglas no tienen datos de personas.

---

## 10. Si algo falla

| Lo que ve                                                             | Qué significa                                                                    | Qué hacer                                                                    |
| --------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `TcpTestSucceeded : False`                                            | El equipo no llega a la base                                                     | Red, VPN o firewall: soporte de red                                          |
| En los logs: `ORA-12541` o `ORA-12514`                                | Servidor, puerto o nombre de servicio mal escritos                               | Revise `DB_URL` en `.env` (paso 5)                                           |
| En los logs: `ORA-01017`                                              | Usuario o clave de la base incorrectos                                           | Revise `DB_USER` y `DB_PASSWORD`                                             |
| En los logs: `ORA-00942` (la tabla no existe)                         | El usuario no ve las tablas o falta un sinónimo/permiso                          | Revise `DB_SCHEMA` y los permisos (paso 0)                                   |
| El backend no arranca y los logs nombran `JWT_SECRET` o `RECEIPT_KEY` | Siguen con el valor de ejemplo de `.env.example` o tienen menos de 32 caracteres | Genérelas de nuevo (paso 4)                                                  |
| Al ingresar: "Invalid CORS request" o 403                             | `CORS_ALLOWED_ORIGIN` no es igual a la barra del navegador                       | Corríjalo (sin `/` al final) y reinicie (paso 7)                             |
| El ingreso "funciona" pero vuelve a pedir la clave                    | `COOKIE_SECURE=true` y está entrando por `http://`                               | Use `https://` o, solo en pruebas internas, `COOKIE_SECURE=false`            |
| "Demasiados intentos"                                                 | 5 intentos fallidos en 15 minutos con ese usuario desde ese equipo               | Espere 15 minutos                                                            |
| "port is already allocated" al arrancar                               | El puerto 8081 lo usa otro programa                                              | Cambie `"8081:8080"` por otro puerto (paso 6) y ajuste `CORS_ALLOWED_ORIGIN` |
| "Cuestionario de solo lectura" al editar el cuestionario              | Es lo esperado con `QUESTIONNAIRE_WRITES_ENABLED=false`                          | En producción no se edita; el cambio llega con el paquete de promoción       |
| Un reporte responde "Reporte no disponible"                           | El cuestionario del año no tiene las preguntas B07 y H06                         | Revise el año (`SURVEY_YEAR`) y el cuestionario de ese año                   |
| `docker` no responde                                                  | Docker Desktop no está abierto                                                   | Ábralo y espere "Engine running"                                             |

Después de cambiar `.env`, siempre reinicie con:

```powershell
docker compose -f docker-compose.servidor.yml up -d --wait
```

---

## 11. Reglas de seguridad que no se negocian

- El archivo `.env` contiene claves: no lo copie a USB, correo, chat ni a GitHub. Si sospecha que
  se filtró, cambie la clave de la base y genere un `JWT_SECRET` nuevo (`RECEIPT_KEY` solo si es
  imprescindible, ver paso 4).
- No active el registro detallado (`TRACE`) de `org.springframework.jdbc`: imprimiría claves y
  cédulas en los logs.
- En producción: `https://`, `COOKIE_SECURE=true`, `SWAGGER_ENABLED=false`,
  `QUESTIONNAIRE_WRITES_ENABLED=false`.
- Los archivos Excel que descargue de la aplicación (claves, estado del operativo, novedades,
  rendimientos) tienen datos de personas: guárdelos solo donde la Ley 1581 lo permita y bórrelos
  cuando ya no se necesiten.
- No conecte el `docker-compose.yml` de desarrollo a la base real: borra y recarga datos al arrancar.
