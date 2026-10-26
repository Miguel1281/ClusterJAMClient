# Cluster JAM Client

Cliente Android con Kotlin y Jetpack Compose. La autenticación consume Auth Service mediante
Retrofit; las vistas acceden al repositorio a través de `AuthViewModel`.

## Organización del proyecto

Android, el cliente Windows y el backend se mantienen en repositorios independientes.
Este repositorio contiene únicamente el cliente Android y conserva un solo módulo Gradle, `app`.

Dentro de `com.clusterjam.mobile`:

- `app`: composición, navegación, rutas y barra inferior compartida. `MainActivity` inicia
  `ClusterJamApp`, que conserva la restauración de sesión y los recorridos actuales.
- `app/di`: inyección manual. `ClusterJamApplication` conserva un `AppContainer` por proceso;
  este comparte un repositorio Auth, un almacenamiento cifrado y un cliente HTTP. Las fábricas
  de ViewModels reciben las dependencias ya construidas.
- `core/network`: configuración Retrofit con `BuildConfig.AUTH_BASE_URL`, los tiempos límite
  existentes y el parser común de Problem Details. `core/ui/theme` contiene el tema compartido.
- `features/auth/data`: API y DTO de solicitudes/respuestas, almacenamiento cifrado, mapeadores
  y coordinación del repositorio. Los DTO de solicitudes son independientes de los modelos del dominio.
- `features/auth/domain`: modelos, interfaz del repositorio y validación que devuelve códigos;
  no depende de Android, recursos, Compose ni Retrofit.
- `features/auth/presentation`: pantallas por flujo, `AuthViewModel` transitorio y traducción
  de fallos a mensajes locales de `strings.xml`. Solo consume el contrato del repositorio.
- `features/catalog/presentation` y `features/profile/presentation`: prototipos existentes.

`AuthViewModel` conserva temporalmente sus operaciones; la separación de estados y ViewModels
por flujo se realizará en las ramas funcionales del roadmap. Esta reorganización conserva las
siete operaciones Auth, sin anticipar la confirmación de correo ni la verificación de roles.

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

La configuración de URL no modifica el despliegue del backend. HTTP permite las pruebas locales;
la dirección de un despliegue debe utilizar HTTPS.

## Contrato HTTP integrado

| Operación | Método y ruta | Respuesta esperada |
| --- | --- | --- |
| Registro | `POST /api/v1/registrations` | `201`, identidad y estado; no devuelve tokens |
| Login | `POST /api/v1/sessions` | `200`, sesión con ambos tokens |
| Renovación | `POST /api/v1/session-renewals` | `200`, sesión con tokens renovados |
| Identidad actual | `GET /api/v1/sessions/current` | `200`, identidad |
| Cierre de sesión | `DELETE /api/v1/sessions/current` | `204`, sin cuerpo |
| Solicitar recuperación | `POST /api/v1/password-recovery-requests` | `200`, mensaje neutral |
| Recuperar contraseña | `POST /api/v1/password-recoveries` | `204`, sin cuerpo |
| Liveness | `GET /health/live` | `200`, `status: ok` |
| Readiness | `GET /health/ready` | `200`, `status: ok`, o `503` |

Solo la consulta y el cierre de sesión actual envían `Authorization: Bearer <accessToken>`.
`expiresIn` se recibe en segundos y se convierte a una expiración absoluta en milisegundos.
Los códigos de recuperación se envían como cadenas de seis dígitos para conservar ceros iniciales.

Los errores HTTP se interpretan como Problem Details. `AuthFailure` conserva el código, el estado
HTTP real, los errores de validación y el identificador de traza cuando existen. Los mensajes
mostrados al usuario se eligen en presentación mediante `AuthMessageMapper` y `strings.xml`,
sin mostrar diagnósticos del proveedor. El logging de
red se activa únicamente en debug y registra método, ruta y estado; omite cuerpos y cabeceras.

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

## Verificación y entrega en GitHub Actions

El workflow `.github/workflows/android-ci.yml`, **Android CI and APK delivery**, se ejecuta en
PR hacia `main`, pushes a `main` y manualmente. El trabajo **Verify Android** comprueba formato,
pruebas JVM, Android Lint y construcción debug en Ubuntu 24.04. Usa una URL ficticia y no accede
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
La comprobación de release con una URL HTTPS ficticia se añadirá cuando la rama 6 establezca su
política HTTPS; la entrega inicial seguirá usando el APK debug firmado de pruebas.
