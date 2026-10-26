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

Con el SDK Android y el JDK configurados para el Gradle Wrapper del proyecto:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Las pruebas de contrato usan MockWebServer local y almacenamiento en memoria. Cubren las rutas,
los cuerpos de solicitud, respuestas `201`/`204`, la conversión de expiración, rotación de tokens
y errores Problem Details. No requieren un backend activo ni credenciales de Supabase.
Las pruebas de health están en `core/network`; los mensajes locales se verifican en presentación
y las reglas de validación en dominio. Las pruebas automatizadas no acreditan ejecución contra
el backend real ni una revisión visual en emulador.
