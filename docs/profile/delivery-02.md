# Entrega 2 Consulta real del perfil

Fecha: 30 de octubre de 2026.
Rama: `feat/profile/add-profile-screen`.
Base: `4bdaee90e4d19d7e9c00df32b255049adaf5635c`.

El PR #13 se integró mediante squash con autorización explícita, después de
comprobar Verify Android exitoso y la entrega de APK omitida para el PR. Se
actualizó main antes de crear esta rama. Esta entrega queda pendiente de revisión
y autorización para su propio squash merge.

## Cambios

La pestaña Perfil consulta `GET /api/v1/profiles/me` mediante el repositorio real
de la primera entrega. Muestra nombre, teléfono, correo de contacto y el estado
PENDING/COMPLETED recibido del backend. Los null se presentan como datos sin
completar; no se generan valores de contacto de ejemplo ni se decide localmente
si el onboarding está completo.

`ProfileViewModel`/`ProfileUiState` siguen `LoginViewModel` y sus estados:
`MutableStateFlow`, datos inmutables, `viewModelScope` y resultados del repositorio.
Se amplía `ViewModelFactories` y se usa el AppContainer existente desde la
composición del NavHost; el ViewModel pertenece a la entrada de navegación
Perfil. No se crea otro sistema de inyección, sesión ni navegación.

La pantalla distingue carga, aprovisionamiento y fallo. Solo 404
`PROFILE_NOT_READY` admite tres reintentos automáticos tras 1, 2 y 4 segundos,
además de la lectura inicial. Al agotarlos, ofrece reintento explícito. PENDING
es un perfil ya existente y no activa ese sondeo. Red/timeout, permisos,
respuestas inválidas y fallos de servicio muestran mensajes locales reutilizando
AuthMessageMapper; no exponen mensajes internos ni consultas infinitas.

La visibilidad STARTED de la entrada controla la consulta. Salir, pasar a segundo
plano o perder la identidad cancela esperas/trabajo y limpia los datos en memoria.
Un identificador de operación descarta respuestas tardías. La identidad firmada
verificada por Auth determina al propietario esperado; una respuesta con otro ID
no se muestra. Al volver a entrar se consulta de nuevo. No hay persistencia de
contacto ni bandera local de onboarding completado.

Se reutilizan StatusMessage, superficies, tipografía y espacios del tema actual,
con scroll y mensajes accesibles. El correo de acceso sigue en su sección Auth.
Consultar Profile no bloquea las acciones existentes de recuperar contraseña,
actualizar identidad ni cerrar sesión.

Todavía no se incorporan formularios de onboarding/edición, direcciones ni el
desvío inicial según onboarding: pertenecen a las dos ramas siguientes. La
navegación existente de catálogo/autenticación se conserva en esta entrega.

## Archivos

Creados bajo `app/src/main/java/com/clusterjam/mobile/features/profile/presentation`:

- `ProfileViewModel.kt`: consulta, sondeo acotado y límite de sesión/visibilidad.
- `ProfileUiState.kt`: estados y datos de la presentación.
- `ProfileMessageMapper.kt`: traducción local de fallos de Profile con reutilización de Auth.

Modificados:

- `app/src/main/java/com/clusterjam/mobile/features/profile/presentation/ProfileScreen.kt`:
  carga/aprovisionamiento/error, datos reales, estado del servidor y actualización.
- `app/src/main/java/com/clusterjam/mobile/app/navigation/AppNavHost.kt`:
  inyección del ViewModel en la ruta existente y observación de ciclo de vida.
- `app/src/main/java/com/clusterjam/mobile/app/di/ViewModelFactories.kt`:
  dependencias y creación de ProfileViewModel, conservando las firmas Auth existentes.
- `app/src/main/res/values/strings.xml`: textos locales para estados/campos/errores.
- `README.md` y `docs/profile/implementation-plan.md`: avance y comportamiento actual.
- Este informe y `docs/profile/pull-requests/02-profile-screen.md`: entrega y descripción del PR.

Los cambios previos en `.idea/inspectionProfiles/Project_Default.xml` y
`app/src/debug/res/xml/network_security_config.xml` permanecen excluidos.
No se cambia el backend, las fuentes de pruebas ni los documentos originales.

## Compilación

Se ejecutó:

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache spotlessApply :app:assembleDebug '-PauthBaseUrl=https://auth.clusterjam.invalid/' '-PprofileBaseUrl=https://profile.clusterjam.invalid/'
```

La invocación añadió `spotlessIdeHook` con las rutas absolutas de los seis archivos
Kotlin de esta entrega, limitando el formato a ellos. Resultado real:
`BUILD SUCCESSFUL in 47s`, 40 tareas, ninguna tarea de pruebas. El APK se genera
en `app/build/outputs/apk/debug/app-debug.apk`. Las URL `.invalid` se usan solo
para compilación y no se contactaron.

El CI existente para ramas `/profile/` compila debug/release sin ejecutar pruebas.
No se crearon ni ejecutaron pruebas unitarias, de integración o instrumentadas.
La compilación no acredita funcionamiento de la pantalla con un backend en
ejecución, pruebas visuales ni validación funcional en dispositivo/emulador.

Después de revisión y autorización se integrará mediante squash, se actualizará
main y se creará `feat/profile/add-profile-forms`.
