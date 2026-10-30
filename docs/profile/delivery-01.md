# Entrega 1 Integración HTTP de perfil

Fecha: 30 de octubre de 2026.
Rama: `feat/profile/add-api-integration`.
Base: `6f79e2fac5981a56398153009fa546691a79126c`.
Estado: preparada para revisión mediante PR hacia main; pendiente de autorización
para squash merge. Las ramas siguientes no se han iniciado.

## Funcionalidad

Se implementa la base de comunicación de Profile siguiendo `AuthApiService`,
los DTO/mapeadores de Auth, `AuthRepository`/`AuthRepositoryImpl` y `AppContainer`.
`ProfileRepository` expone operaciones suspend con `Result`; su implementación
usa `Dispatchers.IO`, conserva cancelación y convierte fallos al modelo de dominio.

`GET /api/v1/profiles/me` devuelve el perfil y su revisión. `PATCH` solo envía
nombre/teléfono/correo de contacto y la cabecera If-Match exacta recibida del
servidor. Se valida la revisión de 64 dígitos hexadecimales entre comillas,
de acuerdo con el backend, y no se fabrica si falta. El resultado conserva
los campos legacy null y el estado PENDING/COMPLETED confirmado por el servidor.

Se reutiliza la validación de correo de Auth; nombre y teléfono anticipan las
restricciones reales de Profile. Los campos omitidos se excluyen del JSON.
La identidad y el estado de onboarding no forman parte del DTO de actualización.
Los códigos PROFILE_NOT_READY, PRECONDITION_FAILED, PRECONDITION_REQUIRED y los
de sesión/red/servidor se conservan sin exponer mensajes internos ni datos privados.

No se añade otro gestor de tokens. `SessionAccess` sigue siendo el repositorio
Auth compartido, con sus reglas actuales de renovación, generación de sesión e
invalidación. Se permite explícitamente el origen/ruta de Profile. Tanto el
interceptor de aplicación como el de red verifican el destino autorizado;
POST/PATCH son cuerpos de un solo envío. El repositorio no reintenta escrituras.

La propiedad Gradle `profileBaseUrl` usa la misma normalización y restricciones
de Auth y puede apuntar a otro puerto. Si se omite, utiliza el gateway de Auth.
Release exige HTTPS y la distribución conserva las restricciones de host real.

No se incorporan pantallas ni se modifica navegación en esta entrega. El
repositorio queda disponible en `AppContainer` para la siguiente rama; las
direcciones se incorporarán en su propia funcionalidad.

## Archivos creados

Todos los Kotlin de esta lista están bajo
`app/src/main/java/com/clusterjam/mobile/features/profile/`:

- `data/remote/ProfileApiService.kt`
- `data/remote/dto/ProfileResponseDto.kt`
- `data/remote/dto/UpdateProfileRequestDto.kt`
- `data/repository/ProfileRepositoryImpl.kt`
- `data/mapper/ProfileResponseMapper.kt`
- `data/mapper/ProfileErrorMapper.kt`
- `domain/repository/ProfileRepository.kt`
- `domain/model/CustomerProfile.kt`
- `domain/model/OnboardingStatus.kt`
- `domain/model/ProfileChanges.kt`
- `domain/model/ProfileSnapshot.kt`
- `domain/model/ProfileFailure.kt`
- `domain/validation/ProfileValidator.kt`

Documentación: `docs/profile/implementation-plan.md`, este informe y
`docs/profile/pull-requests/01-api-integration.md`.

## Archivos modificados

- `app/build.gradle.kts`: URL de Profile, validación común y BuildConfig por variante.
- `app/src/main/java/com/clusterjam/mobile/app/di/AppContainer.kt`: API/repo Profile
  y configuración de la misma sesión compartida.
- `app/src/main/java/com/clusterjam/mobile/core/network/NetworkModule.kt`:
  reutilización de la construcción Retrofit y límites de autorización de Profile.
- `app/src/main/java/com/clusterjam/mobile/core/network/AuthorizationScope.kt`:
  comprobación de origen, puerto, prefijo, ruta y método de Profile.
- `app/src/main/java/com/clusterjam/mobile/core/network/SessionAccess.kt`:
  documentación de envío único de POST/PATCH.
- `app/src/main/java/com/clusterjam/mobile/features/auth/data/repository/AuthRepositoryImpl.kt`:
  extensión explícita de los destinos admitidos por SessionAccess.
- `.github/workflows/android-ci.yml`: solo compilación en estas ramas/squash commits
  de Profile; preserva la verificación previa para otros cambios.
- `README.md`: configuración, alcance y compilación sin pruebas de esta etapa.

Los cambios locales previos en `.idea/inspectionProfiles/Project_Default.xml` y
`app/src/debug/res/xml/network_security_config.xml` permanecen fuera del PR.
No se modifica `cluster-jam` ni la documentación original del proyecto.

## Compilación ejecutada

Se ejecutó la siguiente invocación, incluyendo el filtro Spotless para los archivos
de la rama (argumento `spotlessIdeHook` omitido aquí por longitud):

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache spotlessApply :app:assembleDebug '-PauthBaseUrl=https://auth.clusterjam.invalid/' '-PprofileBaseUrl=https://profile.clusterjam.invalid/'
```

Resultado real: `BUILD SUCCESSFUL in 58s`, 40 tareas, ninguna tarea de pruebas.
Los dominios `.invalid` son valores exclusivos de compilación y no se contactó
con ellos. El formateador requirió rutas absolutas; se aplicó después con
`spotlessApply` y el filtro absoluto de archivos (`BUILD SUCCESSFUL in 18s`), sin
alterar fuentes ajenas ni pruebas existentes. La salida compilada queda en
`app/build/outputs/apk/debug/app-debug.apk` y no se presenta como APK validado
funcionalmente contra el backend.

En el PR, GitHub ejecuta únicamente `:app:assembleDebug :app:assembleRelease`
para esta rama. La tarea existente de pruebas se omite expresamente.
No se han creado ni ejecutado pruebas unitarias, de integración o instrumentadas.
No hay verificación funcional en emulador/dispositivo en esta entrega.

## Siguiente paso

Publicar el PR y esperar autorización, como exige la solicitud. Después de su
squash merge se actualiza main y se crea `feat/profile/add-profile-screen`.
El avance de esta etapa no se declara integración completa de las pantallas.
