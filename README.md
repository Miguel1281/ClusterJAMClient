# Cluster JAM Client

Cliente Android con Kotlin y Jetpack Compose. La autenticación consume Auth Service mediante
Retrofit. `AppViewModel` coordina la sesión global, `LoginViewModel` conserva el formulario de
acceso, `RegistrationViewModel` comparte registro y confirmación del correo, y `AuthViewModel`
mantiene transitoriamente la recuperación de contraseña.

## Organización del proyecto

Android, el cliente Windows y el backend se mantienen en repositorios independientes.
Este repositorio contiene únicamente el cliente Android y conserva un solo módulo Gradle, `app`.

Dentro de `com.clusterjam.mobile`:

- `app`: composición, navegación, rutas y barra inferior compartida. `MainActivity` inicia
  `ClusterJamApp`; `AppViewModel` observa el estado de sesión publicado por el repositorio.
- `app/di`: inyección manual. `ClusterJamApplication` conserva un `AppContainer` por proceso;
  este comparte un repositorio Auth, un almacenamiento cifrado y un cliente HTTP. Las fábricas
  de ViewModels reciben las dependencias ya construidas.
- `core/network`: configuración Retrofit con `BuildConfig.AUTH_BASE_URL`, los tiempos límite
  existentes, `SessionAccess` para operaciones protegidas y el parser común de Problem Details.
  `core/ui/theme` contiene el tema compartido.
- `features/auth/data`: API y DTO de solicitudes/respuestas, almacenamiento cifrado, mapeadores
  y coordinación del repositorio. Los DTO de solicitudes son independientes de los modelos del dominio.
- `features/auth/domain`: identidad básica, identidad verificada con rol, estados de sesión,
  interfaz del repositorio y validación; no depende de Android, Compose ni Retrofit.
- `features/auth/presentation`: formularios, `LoginViewModel`, `RegistrationViewModel`, `AuthViewModel` transitorio y traducción
  de fallos a mensajes locales de `strings.xml`. Solo consume el contrato del repositorio.
- `features/catalog/presentation` y `features/profile/presentation`: prototipos existentes.

Están integradas las nueve operaciones Auth. `AuthViewModel` conserva temporalmente la
recuperación de contraseña; ese flujo se separará en la rama 7.

Los health checks se consultan directamente mediante `AppContainer.healthApiService`, separado
de la API y del repositorio Auth. No necesitan sesión y no condicionan login ni catálogo.
`readiness()` devuelve una respuesta HTTP para que el llamador pueda distinguir `200` de `503`.
El perfil ya no incluye el botón de diagnóstico.

Antes de retirar `cluster_jam_client.zip` de las fuentes se compararon todos sus archivos con el
código vigente, normalizando los paquetes: no contenía funcionalidades únicas pendientes de
rescatar. Se conservan las mejoras posteriores del contrato, configuración de URL y errores.

## Configurar Auth Service

La propiedad Gradle `authBaseUrl` define la dirección del servicio y genera
`BuildConfig.AUTH_BASE_URL`. La URL predeterminada es `http://10.0.2.2:8080/`, que permite al
emulador Android acceder al puerto 8080 del equipo anfitrión.

Para un dispositivo físico, usar la dirección LAN del equipo o la URL publicada por el proxy,
con el puerto externo que corresponda. Por ejemplo, desde PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug '-PauthBaseUrl=http://192.168.1.100:8081/'
```

También se puede definir `authBaseUrl=https://api.example.com/` en
`%USERPROFILE%\.gradle\gradle.properties`, para que Android Studio la utilice al sincronizar
y compilar. La URL debe usar HTTP o HTTPS y no incluir credenciales, query ni fragmento.
Se agrega una barra final si falta. No agregar `/api/v1` al valor: las rutas de Retrofit ya
incluyen ese prefijo. Un prefijo externo del proxy, si existe, sí puede formar parte de la URL base.

La excepción de HTTP está únicamente en `app/src/debug/AndroidManifest.xml`. El APK debug
permite el emulador y la URL LAN configurada expresamente; release deniega cleartext y exige
`authBaseUrl` explícita con HTTPS, sin heredar la dirección del emulador. Por ejemplo:

```powershell
.\gradlew.bat :app:assembleRelease '-PauthBaseUrl=https://api.example.com/'
```

La construcción release de verificación no requiere firma de publicación. La configuración
de URL no despliega ni comprueba la disponibilidad del backend.

## Contrato HTTP integrado

| Operación | Método y ruta | Respuesta esperada |
| --- | --- | --- |
| Registro | `POST /api/v1/registrations` | `201`, identidad y estado; no devuelve tokens |
| Confirmar correo | `POST /api/v1/registrations/email-confirmations` | `200`, sesión con ambos tokens |
| Reenviar confirmación | `POST /api/v1/registrations/email-confirmation-requests` | `202`, mensaje neutral |
| Login | `POST /api/v1/sessions` | `200`, sesión con ambos tokens |
| Renovación | `POST /api/v1/session-renewals` | `200`, sesión con tokens renovados |
| Identidad actual | `GET /api/v1/sessions/current` | `200`, identidad con `appRole` |
| Cierre de sesión | `DELETE /api/v1/sessions/current` | `204`, sin cuerpo |
| Solicitar recuperación | `POST /api/v1/password-recovery-requests` | `200`, mensaje neutral |
| Recuperar contraseña | `POST /api/v1/password-recoveries` | `204`, sin cuerpo |
| Liveness | `GET /health/live` | `200`, `status: ok` |
| Readiness | `GET /health/ready` | `200`, `status: ok`, o `503` |

Solo la consulta y el cierre de sesión actual envían `Authorization: Bearer <accessToken>`.
`expiresIn` se recibe en segundos y se convierte a una expiración absoluta en milisegundos.
Los códigos de confirmación y recuperación se envían como cadenas de seis dígitos para conservar ceros iniciales.

## Registro y confirmación del correo

Un registro `PENDING_EMAIL_CONFIRMATION` abre la pantalla de confirmación con el correo
precargado. Un registro `ACTIVE` vuelve a login con un aviso, porque el registro no entrega
tokens. Si login devuelve `EMAIL_NOT_CONFIRMED`, ofrece **Confirmar correo** con ese correo.
Entrar o volver a estas pantallas no envía solicitudes automáticamente.

`RegistrationViewModel` coordina formulario, registro, espera del código, confirmación, reenvío,
error y finalización. Permite editar el correo pendiente y reenviar por una acción explícita.
El `202` de reenvío solo muestra un mensaje neutral: no acredita que la cuenta exista ni que
el correo haya llegado. Se muestran errores locales para código inválido, confirmación no
permitida, validación, límite de solicitudes y falta de disponibilidad. Si el servidor devuelve
`Retry-After`, se espera el plazo indicado antes de enviar otra solicitud; el contador no valida
la expiración del código, que corresponde al servidor.

Solo el correo y la etapa se conservan en `SavedStateHandle`. La contraseña y el OTP no se
guardan en preferencias, estado guardado, logs ni argumentos de navegación. Tras recrear el
proceso, el campo del código queda vacío y debe introducirse de nuevo; no se confirma ni se
reenvía automáticamente. Los envíos en curso deshabilitan las acciones para evitar duplicados.

Al confirmar con `200`, el repositorio guarda la sesión y consulta la identidad con las mismas
reglas de acceso exclusivo para clientes que usa login. No hace falta iniciar sesión otra vez.
Si esa consulta falla temporalmente, conserva los tokens y ofrece reintentar **la identidad**,
sin volver a enviar el código consumido. El éxito de `confirmEmail` indica que el POST terminó;
el acceso depende de `SessionState`. El transporte tampoco repite automáticamente peticiones
tras un fallo de conexión. Ante un resultado incierto, el usuario puede intentar login o
solicitar otro código de forma explícita.

## Identidad verificada y acceso Android

Login guarda la sesión recibida y consulta la identidad actual antes de abrir catálogo o perfil.
La restauración, el botón de actualizar identidad y el regreso desde segundo plano usan la misma
comprobación. `AuthIdentity` sigue siendo la identidad básica de registro y tokens; únicamente
`VerifiedIdentity` contiene el rol validado. Login, renovación y registro no requieren `appRole`
en sus respuestas.

El repositorio publica un único `SessionState`: comprobando, sin sesión, cliente autenticado,
acceso denegado o error recuperable. Reconoce los valores exactos `cliente` y `administrador`;
Android admite exclusivamente `cliente`. Comprueba además que `userId` coincida con la sesión.
No se concede acceso por tener tokens almacenados ni se asigna un rol predeterminado.

Una cuenta administradora produce el fallo local `CLIENT_APP_ACCESS_DENIED`, con este mensaje:
“Esta aplicación es exclusiva para cuentas de cliente. No tienes acceso desde este dispositivo”.
Se bloquea el acceso y se borran los tokens antes de intentar `DELETE /api/v1/sessions/current`
con el token de Android, sin renovar ni repetir ese cierre desde el repositorio. El rechazo persiste aunque falle el
cierre remoto. Se utiliza el contrato de cierre de sesión actual del backend, con alcance local;
no se solicita cerrar otras sesiones, incluidas las de Windows.

Un rol ausente o desconocido produce `INVALID_IDENTITY_ROLE`; el `403 INVALID_APP_ROLE` del
servidor conserva su código y estado HTTP. Ambos casos bloquean el acceso y descartan la sesión.
Si falla el borrado local, el proceso conserva el bloqueo y permite reintentar la limpieza.
Los errores temporales de identidad conservan los tokens y muestran acciones de reintento o
cierre local. Reintentar consulta identidad; no vuelve a enviar login.

Mientras se comprueba identidad o se muestra un rechazo/error recuperable, el grafo privado
de navegación no está compuesto. Al salir del estado autenticado se descarta su pila; el botón
Atrás no puede recuperar catálogo o perfil. `AppViewModel` dirige esta transición desde el estado
observable; el formulario no autentica por su cuenta ni depende de recibir un aviso pasajero.
La confirmación de correo reutiliza el establecimiento y verificación de sesión del
repositorio, sin exigir un login adicional ni duplicar las reglas del rol.

Los errores HTTP se interpretan como Problem Details. `AuthFailure` conserva el código, el estado
HTTP real, los errores de validación y el identificador de traza cuando existen. Los mensajes
mostrados al usuario se eligen en presentación mediante `AuthMessageMapper` y `strings.xml`,
sin mostrar diagnósticos del proveedor. El logging de
red se activa únicamente en debug y registra método, ruta y estado; omite cuerpos y cabeceras.

## Coordinación y persistencia de la sesión

`AppContainer.authRepository` y `AppContainer.sessionAccess` apuntan a la misma instancia de
`AuthRepositoryImpl`, con el mismo almacenamiento. `SessionAccess.execute` recibe una fábrica
de `retrofit2.Call` que añade el encabezado proporcionado y construye la llamada sin ejecutarla.
Está destinada a repositorios de datos; ViewModels y pantallas siguen sin recibir tokens.
Solo admite operaciones protegidas del origen y prefijo de API configurados. El transporte
también impide enviar `Authorization` a las rutas públicas de Auth o a hosts externos.

La coordinación conserva el mutex para modificar la sesión, pero ejecuta las peticiones
ordinarias fuera de él. Una solicitud que esperaba renovar vuelve a leer los tokens y
reutiliza una renovación ya completada. GET, HEAD y DELETE admiten como máximo un reintento
tras un `401`; POST no se repite automáticamente. La renovación no se llama a sí misma ni
usa el mecanismo de peticiones protegidas. Se conserva la protección del transporte frente
a reenvíos por conexión fallida o por `503` con `Retry-After: 0`.

Cerrar o descartar invalida inmediatamente los resultados pendientes antes de esperar al
mutex. Una respuesta antigua no puede guardar tokens ni publicar una identidad autenticada.
Un `401` de refresh o el rechazo del token renovado termina la sesión; `409`, `429`, red y
`503` conservan los tokens. La renovación y la consulta de identidad respetan `Retry-After`
en segundos o fecha HTTP; la pantalla muestra la espera. Un `403` de permisos de otra API
solo falla esa operación y no cierra la sesión. Las reglas específicas de identidad y rol
se mantienen en la consulta de sesión actual.

Si el cierre remoto falla, se intenta igualmente la limpieza local y se comunica que el
cierre fue solo local. Si falla el borrado persistente, el acceso queda bloqueado y se puede
reintentar la limpieza. Abrir claves/preferencias, leer, guardar y borrar tienen fallos de
almacenamiento identificables. La apertura es diferida para poder mostrar el error durante
la restauración, sin interrumpir la construcción de dependencias. Un contenido incompleto
se informa como error de lectura; no se borra ni se trata silenciosamente como sesión ausente.
Un guardado fallido bloquea los tokens anteriores hasta su limpieza explícita.

Se mantiene `EncryptedSharedPreferences`, con ambos tokens y la expiración guardados juntos;
no hay sustitución por texto plano. `cluster_jam_secure_prefs.xml` está excluido del respaldo
en `backup_rules.xml` y del respaldo y transferencia en `data_extraction_rules.xml`.

## Verificación

El toolchain actual usa JDK 25 (`gradle/gradle-daemon-jvm.properties`), Gradle Wrapper 9.6.0,
Android Gradle Plugin 9.4.1, la plataforma `platforms;android-37.0` y Build Tools 36.0.0.
Java 11 en `sourceCompatibility` es el nivel del código compilado; no es el JDK del daemon.
CI instala Temurin 25 y usa el wrapper versionado, incluido su checksum. La combinación de
Gradle y SDK respeta la [compatibilidad de AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

Con ese SDK y JDK configurados, ejecutar desde la raíz del repositorio:

```powershell
.\gradlew.bat spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Para comprobar también release, sin servidor real ni firma de publicación:

```powershell
.\gradlew.bat spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease '-PauthBaseUrl=https://ci.clusterjam.invalid/'
```

El formato se fija con Spotless 8.10.4 y ktfmt 0.65, estilo Kotlin con 4 espacios y ancho de
100 caracteres. Incluye fuentes, pruebas y scripts Gradle Kotlin; las versiones están en
`gradle/libs.versions.toml`. Se aplica de forma incremental: solo exige formato en archivos nuevos
o modificados desde `f50da2e6f520db701560988a4e36eac02ba14d4c`, la base anterior a incorporar CI.
Los archivos existentes que no se toquen conservan su formato. Actions descarga el historial
completo para disponer de esa referencia. Para corregir el formato antes de verificar:

```powershell
.\gradlew.bat spotlessApply
```

En Linux/macOS usar `bash ./gradlew` con las mismas tareas. No hace falta configurar una clave
de entrega para ejecutar estos comandos ni las pruebas JVM; el debug local conserva su firma
habitual de desarrollo.

Las pruebas de contrato usan MockWebServer local y almacenamiento en memoria. Cubren las rutas,
los cuerpos de solicitud, respuestas `201`/`204`, la conversión de expiración, rotación de tokens
y errores Problem Details. No requieren un backend activo ni credenciales de Supabase.
Las pruebas de health están en `core/network`; los mensajes locales se verifican en presentación
y las reglas de validación en dominio. Las pruebas automatizadas no acreditan ejecución contra
el backend real ni una revisión visual en emulador.

Las pruebas de identidad cubren cliente, administrador, rol ausente/desconocido, `403`, discrepancia
de usuario, fallo temporal, token renovado y limpieza local fallida. Las pruebas Compose de esta
rama verifican rechazo en login/restauración, revalidación al volver del segundo plano, reintento
y ausencia de pantallas privadas tras pulsar Atrás. Con un emulador disponible:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.clusterjam.mobile.VerifiedCustomerNavigationTest'
```

Estas pruebas usan MockWebServer y almacenamiento ficticio; no prueban cuentas reales de Supabase
ni la conservación de una sesión de Windows contra un despliegue real.
La rama 4 verificó 52 pruebas JVM y cuatro pruebas Compose en Pixel_8 (API 36), todas aprobadas.
La confirmación añade solo seis pruebas JVM esenciales: contrato HTTP y código con cero inicial,
reintento de identidad sin repetir el OTP, errores/reenvío con `Retry-After`, restauración sin
secretos ni reenvío automático, prevención de doble envío y entrada desde login. Usan
MockWebServer, almacenamiento ficticio y reloj controlado; no requieren credenciales reales.
En la rama 5 pasaron las 58 pruebas JVM, formato, Android Lint y las construcciones del APK
debug y del APK de instrumentación. Las pruebas Compose existentes se adaptaron al nuevo
ViewModel, pero no se ejecutaron en emulador durante esta rama.
La comprobación del correo recibido y del flujo completo contra el backend real queda pendiente.

La rama 6 añade nueve pruebas JVM puntuales de expiración, concurrencia, rechazo/espera de
refresh, cierre con operaciones pendientes, alcance de autorización y fallos de almacenamiento.
También amplía casos existentes de cierre en presentación y mensajes de almacenamiento.
El conjunto suma 67 pruebas JVM. La comprobación de las preferencias usa dobles: no sustituye
una prueba del keystore real en un dispositivo. Las pruebas Compose existentes se compilan,
pero no se ejecutan en emulador en esta etapa.
Pasaron las 67 pruebas, formato, Lint de ambas variantes y las construcciones debug, release
sin firma y del APK de instrumentación. También se verificó el rechazo de release sin URL
explícita o con HTTP; estas comprobaciones no contactan con el backend.

## Verificación y entrega en GitHub Actions

El workflow `.github/workflows/android-ci.yml`, **Android CI and APK delivery**, se ejecuta en
PR hacia `main`, pushes a `main` y manualmente. El trabajo **Verify Android** comprueba formato,
pruebas JVM, Android Lint y construcciones debug y release en Ubuntu 24.04. Usa una URL HTTPS ficticia y no accede
al backend. `--continue` permite obtener los reportes de las comprobaciones independientes
aunque otra falle; la ejecución sigue teniendo resultado fallido si cualquier tarea falla.

Los PR solo verifican: no reciben el keystore de entrega ni publican APK. Tras pasar las
comprobaciones en `main`, **Deliver testing APK** construye el APK debug con la firma estable
de pruebas, verifica la firma y lo publica mediante `actions/upload-artifact`. Una ejecución
manual desde otra rama solo verifica; para entregar, seleccionar `main`.

Los artefactos de reportes se conservan incluso si falla una comprobación y duran 30 días.
El artefacto del APK se sube al final, únicamente si pasan las verificaciones, la construcción,
la comprobación de firma y la limpieza temporal. Las acciones están fijadas a commits concretos.
Para impedir merges con verificaciones fallidas, configurar **Verify Android** como comprobación
obligatoria en la regla de protección de `main` del repositorio.

### Preparar la firma una sola vez

Crear en **Settings → Environments** el entorno `apk-testing` y limitar **Deployment branches
and tags** a la rama `main`. Guardar allí estos cuatro secretos:

| Secreto | Contenido |
| --- | --- |
| `ANDROID_TEST_KEYSTORE_BASE64` | Archivo del keystore completo codificado en Base64. |
| `ANDROID_TEST_KEYSTORE_PASSWORD` | Contraseña del keystore. |
| `ANDROID_TEST_KEY_ALIAS` | Alias de la clave, por ejemplo `clusterjam-testing`. |
| `ANDROID_TEST_KEY_PASSWORD` | Contraseña de esa clave. |

Si el equipo todavía no tiene un keystore de pruebas, generarlo una vez con el JDK, fuera del
repositorio. En PowerShell, este comando solicita las contraseñas de forma interactiva:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null
keytool -genkeypair -storetype JKS `
  -keystore "$env:USERPROFILE\.android\clusterjam-testing.jks" `
  -alias clusterjam-testing -keyalg RSA -keysize 3072 -validity 10000 `
  -dname 'CN=Cluster JAM Testing'
```

Para copiar el archivo codificado y pegarlo directamente en el secreto de GitHub:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$env:USERPROFILE\.android\clusterjam-testing.jks")
) | Set-Clipboard
```

Conservar el archivo y sus contraseñas para las futuras actualizaciones. No regenerarlo en cada
ejecución ni versionar claves, contraseñas o archivos de propiedades de firma. Los secretos solo
se usan en el trabajo de entrega autorizado; el keystore se restaura en un directorio temporal
y se elimina antes de subir el APK. La construcción firmada desactiva la caché de configuración
para evitar serializar las contraseñas. Estas protecciones se apoyan en los
[entornos de GitHub](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

### Configurar la URL y ejecutar manualmente

Mientras el backend esté en desarrollo, obtener la IPv4 LAN de la computadora que lo ejecuta
con `ipconfig`. El teléfono debe poder acceder a esa dirección y al puerto externo del servicio;
normalmente ambos equipos estarán en la misma red Wi-Fi. El servidor debe escuchar en una
interfaz accesible por LAN y el firewall debe permitir ese puerto.

1. Abrir **Actions → Android CI and APK delivery → Run workflow**.
2. Seleccionar la rama **main**.
3. En el input **AUTH_BASE_URL**, reemplazar `http://192.168.1.X:3000/` por la dirección real,
   por ejemplo `http://192.168.1.50:3000/`. El ejemplo con `X` no es una dirección válida de entrega.
4. Ejecutar el workflow y esperar a que ambos trabajos terminen correctamente.

El input se pasa a la propiedad Gradle `authBaseUrl` y se incluye en `BuildConfig.AUTH_BASE_URL`.
Cada ejecución manual permite cambiar la IP sin editar código, secretos ni variables. No agregar
`/api/v1`: Retrofit ya lo incluye. La compilación de entrega exige una URL explícita y rechaza
el ejemplo con `X`, `10.0.2.2`, localhost y direcciones de loopback. No comprueba la disponibilidad
del servidor durante la compilación; los tests siguen usando MockWebServer.

Para la entrega automática después de un push/merge en `main`, crear además la **variable**
`AUTH_BASE_URL` dentro de `apk-testing` con una URL LAN real o una URL publicada. Un push no tiene
inputs manuales. Si la IP cambia, actualizar esa variable para las próximas entregas automáticas;
el input manual siempre tiene prioridad. Si faltan la URL o los secretos de firma, la entrega
falla de forma explícita y no publica un APK con la configuración de emulador o una firma aleatoria.

### Descargar e instalar

Abrir **Actions → ejecución exitosa → Artifacts** y descargar el artefacto
`ClusterJAM-Android-<número de ejecución>-<intento>-<commit corto>`.
El ZIP contiene el APK, sus metadatos, `BUILD-INFO.txt` con commit/versión/URL y `SHA256SUMS.txt`.
Los artefactos **caducan a los 30 días**; descargar antes de esa fecha o ejecutar de nuevo el workflow.

Extraer el ZIP y copiar el APK al teléfono. La instalación es manual: abrirlo y autorizar la
instalación desde esa aplicación cuando Android lo solicite. También se puede instalar con
`adb install -r ruta/al/archivo.apk` desde un equipo con depuración USB configurada.

Las entregas usan `versionCode = 100000 + github.run_number` y conservan
`applicationId = com.clusterjam.mobile`. Cada nueva ejecución obtiene un código superior;
reintentar una ejecución conserva su código. Para una actualización nueva, iniciar otra ejecución
desde `main`, en vez de instalar una ejecución anterior. Si se sustituye el workflow por otro con
una numeración reiniciada, aumentar la base de 100000 antes de entregar.

Las actualizaciones requieren conservar la misma clave de firma. Si ya está instalada una
compilación firmada con otra clave, como el debug de Android Studio, será necesario desinstalarla
una vez antes de instalar el primer APK de Actions; esa desinstalación elimina sus datos locales.
Las siguientes entregas con la clave estable pueden instalarse como actualizaciones, conforme a
las [reglas de firma de Android](https://developer.android.com/studio/publish/app-signing).

Esta etapa distribuye APK de pruebas por Actions. No publica en una tienda ni despliega el backend.
La comprobación de release utiliza una URL HTTPS ficticia y no requiere firma de publicación;
la entrega continúa usando el APK debug con firma estable de pruebas y admite HTTP por LAN.
