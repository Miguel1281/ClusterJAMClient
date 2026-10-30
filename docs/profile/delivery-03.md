# Entrega 3 Onboarding y edición de contacto

Fecha: 30 de octubre de 2026.
Rama: `feat/profile/add-profile-forms`.
Base: `1c608a39c6938d37d8aba9d44c04b49d954fa2d4`.

El PR #14 se integró mediante squash con autorización explícita, tras comprobar
Verify Android exitoso. Se actualizó main antes de crear esta rama. Esta entrega
está preparada para su propio PR, revisión y autorización de integración.

## Funcionalidad y patrones

Onboarding y edición comparten `ProfileFormScreen` y `ProfileFormViewModel`.
Se reutilizan directamente AuthForm, AuthField, AuthFeedback, PrimaryButton,
ViewModelFactories y el mecanismo de SavedStateHandle empleado por el registro.
El repositorio y la sesión son los ya integrados en las entregas anteriores;
no hay una segunda autenticación, otro NavHost ni nuevas bibliotecas.

El formulario consulta `GET /api/v1/profiles/me`, precarga nombre/teléfono/correo
de contacto reales y valida los tres campos con ProfileValidator. Solo esos
campos se envían en `PATCH /api/v1/profiles/me`, con el ETag actual en If-Match.
No se permite editar identidad, correo de acceso o estado de onboarding.

El guardado muestra progreso, bloquea edición/doble envío y conserva el borrador
ante un fallo. El éxito muestra la respuesta canónica y una confirmación con
Continuar. Solo un estado COMPLETED recibido del backend permite continuar al
catálogo; si el servidor devuelve PENDING se informa y se mantiene el acceso al
perfil para revisión. Ningún resultado se declara guardado por una copia local.

Si hay conflicto 412/428, sesión inválida o resultado incierto, Guardar queda
bloqueado hasta consultar de nuevo. Se conservan los campos, se muestran los
valores actuales y se pide elegir conservar el borrador o reemplazarlo. Después
hace falta pulsar Guardar otra vez. No se reenvía automáticamente PATCH. Se
aplica la espera Retry-After cuando el fallo la proporciona. Una respuesta con
otro propietario no se publica.

## Navegación y restauración

Las nuevas rutas `profile-gate`, `profile-onboarding` y `profile-edit` pertenecen
al NavHost existente. Tras autenticación/restauración se consulta el perfil:

- PROFILE_NOT_READY conserva las lecturas acotadas existentes, sin crear perfiles.
- PENDING abre Completa tu perfil y no habilita el catálogo.
- COMPLETED continúa al catálogo y ofrece edición desde Perfil.

La pantalla de espera permite reintentar, comprobar la sesión o cerrarla si falla
Profile. Desde onboarding se puede volver a Perfil para gestionar la cuenta.
Atrás confirma antes de descartar campos modificados y se bloquea durante una
operación. Las escrituras confirmadas y lecturas actuales se reflejan en el
perfil compartido para actualizar datos y navegación; ante datos inciertos se refresca.

Los ViewModels Profile se crean en el ámbito Activity mediante la misma fábrica
manual, como los formularios Auth. El perfil consultado permanece en memoria
del flujo autenticado para decidir la navegación; ya no se limita a la entrada
Perfil de la entrega 2. Se limpia al suspender el flujo, pasar a segundo plano o
perder la identidad y se vuelve a consultar antes de mostrar catálogo.

El formulario guarda únicamente propietario, modo, campos de contacto y estado
del borrador en SavedStateHandle. No guarda tokens, contraseñas, revisión HTTP ni
bandera de onboarding completado. Se borra al cerrar sesión, denegar el acceso,
cambiar de propietario o descartar. Las comprobaciones temporales de identidad
conservan el borrador sin mostrar contenido autenticado mientras la raíz Auth
está comprobando la sesión. Al restaurarlo se lee una revisión nueva; un borrador
modificado sin revisión vigente exige revisión explícita. Si el backend ya indica
COMPLETED, un borrador pendiente se retoma como edición en lugar de repetir el
onboarding; sin borrador modificado se continúa al catálogo.

## Archivos

Creados bajo `app/src/main/java/com/clusterjam/mobile/features/profile/presentation`:

- `ProfileFormUiState.kt`: modo, campos, carga/guardado, revisión y confirmación.
- `ProfileFormViewModel.kt`: lectura/guardado real, validación, borrador y sesión.
- `ProfileFormScreen.kt`: formulario compartido y confirmación al descartar.
- `ProfileGateScreen.kt`: espera, errores y acciones previas a navegar.

Modificados:

- `app/src/main/java/com/clusterjam/mobile/app/di/ViewModelFactories.kt`:
  creación del formulario con SavedStateHandle y repositorios existentes.
- `app/src/main/java/com/clusterjam/mobile/app/navigation/AppNavHost.kt`:
  coordinación de perfil/formulario, rutas, estado del servidor y ciclo de vida.
- `app/src/main/java/com/clusterjam/mobile/app/navigation/Routes.kt`:
  rutas constantes, sin datos personales ni tokens en argumentos.
- `app/src/main/java/com/clusterjam/mobile/app/navigation/MainScaffold.kt`:
  disponibilidad del catálogo según perfil confirmado.
- `app/src/main/java/com/clusterjam/mobile/features/profile/presentation/ProfileScreen.kt`:
  acceso real a onboarding o edición.
- `app/src/main/java/com/clusterjam/mobile/features/profile/presentation/ProfileViewModel.kt`:
  actualización desde snapshots reales y compartidos, validando propietario.
- `app/src/main/res/values/strings.xml`: textos de formulario, confirmación y revisión.
- `README.md`, `docs/profile/implementation-plan.md`, este informe y
  `docs/profile/pull-requests/03-profile-forms.md`: comportamiento y entrega.

Los cambios locales previos de `.idea/inspectionProfiles/Project_Default.xml` y
`app/src/debug/res/xml/network_security_config.xml` se conservan fuera del PR.
No se modifica backend, documentación original ni fuentes de pruebas.

## Compilación ejecutada

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache spotlessApply :app:assembleDebug '-PauthBaseUrl=https://auth.clusterjam.invalid/' '-PprofileBaseUrl=https://profile.clusterjam.invalid/'
```

La invocación añadió `spotlessIdeHook` con rutas absolutas, limitado a los archivos
Kotlin de esta funcionalidad. La compilación inicial pasó en 41 s; los ajustes de
actualización y restauración también compilaron (34 s y 30 s). La última salida
local devolvió `BUILD SUCCESSFUL in 30s`, 40 tareas, sin tareas de pruebas. El APK queda en
`app/build/outputs/apk/debug/app-debug.apk`; las URL .invalid son exclusivas de
compilación y no se contactaron.

El CI de Profile compila debug/release sin ejecutar las pruebas existentes.
No se crearon ni ejecutaron pruebas unitarias, de integración o instrumentadas.
No se afirma validación funcional, de restauración o visual en dispositivo/
emulador: la evidencia ejecutada corresponde a compilación y revisión del código.

Tras revisión y autorización de este PR, la última rama será
`feat/profile/add-address-management`, desde main actualizado. No se ha iniciado
una rama dependiente mientras esta integración está pendiente.
