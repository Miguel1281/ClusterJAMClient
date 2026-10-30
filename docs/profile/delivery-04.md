# Entrega 4 Gestión de direcciones

Fecha: 30 de octubre de 2026.
Rama: `feat/profile/add-address-management`.
Base: `a9e0b0442378b43e85706d3c9fde1dc1887669b1`.

El PR #15 se integró mediante squash con autorización explícita y checks
satisfactorios. Se actualizó main antes de crear esta última rama. El PR de
direcciones queda preparado para revisión y autorización de su propio squash.

## Funcionalidad

Mis direcciones, accesible desde Perfil, usa GET/POST de
`/api/v1/profiles/me/addresses` y GET/PATCH/DELETE del ítem `{addressId}`.
No se incluyen userId/roles en cuerpos ni rutas arbitrarias: se reutiliza la
sesión propietaria y el ProfileApiService/repositorio existente.

La lista muestra carga, vacío confirmado por el servidor, errores locales y los
datos/predeterminada reales. Alta y edición comparten AuthForm/AuthField con
todos los campos del DTO, scroll, validación, progreso, mensajes y control de
envíos duplicados. Los límites corresponden al backend; país se normaliza a
mayúsculas y se valida como ISO alpha-2 con Locale. El servidor sigue siendo la
autoridad final de validación. Código postal permanece como String, con ceros
iniciales. Solo interiorNumber es opcional; vacío se normaliza a null.

AddressRequestJsonAdapter serializa ese null explícitamente, únicamente para el
DTO de dirección. El Gson de Profile/Auth no cambia y PATCH de perfil sigue
omitiendo campos ausentes. Es un adaptador de solicitud de solo escritura;
las respuestas usan su propio DTO/mapeador.

Crear requiere el ETag de la colección. Editar usa el del ítem obtenido desde
GET. Elegir predeterminada lee primero la revisión actual y envía solo
`isDefault=true`. Eliminar obtiene el ítem actual, muestra sus datos en la
confirmación y envía DELETE con If-Match; 204 no exige cuerpo. Todo cambio
confirmado consulta nuevamente la lista completa, sin marcar predeterminadas
de manera optimista. No se selecciona la primera automáticamente ni se busca
un reemplazo al borrar la predeterminada. El formulario permite desmarcarla.

SessionAccess no reenvía POST/PATCH ni DELETE condicionado después de un 401.
Se mantiene el comportamiento anterior de Auth para DELETE sin If-Match y la
renovación existente de la sesión. Los límites de autorización incluyen solo
los métodos/rutas propietarios y UUID válidos del origen Profile configurado.

Los fallos/conflictos requieren lectura nueva antes de otra mutación. El borrador
se conserva y se revisa contra el ítem/directorio actual. Un alta incierta muestra
la lista real para revisar si ya existe antes de conservar el borrador y guardar
otra vez; no repite POST automáticamente ni infiere éxito comparando datos locales.
La confirmación de eliminación no se persiste ni se reanuda automáticamente.

## Estado y navegación

AddressViewModel sigue los patrones Auth/Profile de StateFlow, Result,
SavedStateHandle, inyección manual y ViewModelFactories. Se añaden dos rutas al
NavHost existente: `profile-addresses` y `profile-address-form`. Un borrador se
restaura en el contexto del mismo propietario y se vuelve a consultar; no se
restauran ETags. Atrás confirma el descarte y las escrituras bloquean otro envío.

La observación de visibilidad cubre conjuntamente lista/formulario, evitando
cancelar una operación al cambiar entre esas dos vistas. Salir/pasar al fondo
cancela trabajo y exige nueva lectura al volver. Se comprueba identidad entre
lecturas y escrituras para descartar operaciones/respuestas de otra sesión.
Cerrar sesión, denegar acceso o cambiar de propietario limpia el estado guardado.
Las direcciones no cambian onboarding y son accesibles también con PENDING.

## Archivos creados

Bajo `app/src/main/java/com/clusterjam/mobile/features/profile`:

- `domain/model/CustomerAddress.kt`: campos, ítem y snapshots de ítem/colección.
- `domain/validation/AddressValidator.kt`: ID, límites, normalización y país.
- `data/remote/dto/AddressResponseDto.kt`: DTO de ítem/lista.
- `data/remote/dto/AddressRequestDto.kt`: cuerpos de alta/edición/predeterminada.
- `data/remote/dto/AddressRequestJsonAdapter.kt`: null explícito del interior.
- `data/mapper/AddressResponseMapper.kt`: contrato, revisiones y mapeo de campos.
- `presentation/addresses/AddressUiState.kt`: lista, formulario y borrador.
- `presentation/addresses/AddressViewModel.kt`: operaciones y estado propietario.
- `presentation/addresses/AddressMessageMapper.kt`: errores locales de dirección.
- `presentation/addresses/AddressScreens.kt`: lista/formulario/confirmaciones.

## Archivos modificados

- `features/profile/data/remote/ProfileApiService.kt`,
  `features/profile/data/repository/ProfileRepositoryImpl.kt` y
  `features/profile/domain/repository/ProfileRepository.kt`: operaciones reales.
- `features/profile/presentation/ProfileScreen.kt`: acceso a Mis direcciones.
- `core/network/AuthorizationScope.kt` y `SessionAccess.kt`: rutas y envío único.
- `features/auth/data/repository/AuthRepositoryImpl.kt`: conserva la renovación
  central, excluyendo DELETE con If-Match del reenvío automático.
- `app/di/ViewModelFactories.kt`, `app/navigation/AppNavHost.kt`,
  `app/navigation/Routes.kt`: creación y rutas en la navegación existente.
- `app/src/main/res/values/strings.xml`: textos de campos/estados/confirmaciones.
- `README.md`, `implementation-plan.md`, este informe, `final-report.md` y
  `pull-requests/04-address-management.md`: documentación y entrega.

Los paquetes Kotlin modificados de esta lista están bajo
`app/src/main/java/com/clusterjam/mobile`. Se preservan fuera del PR los cambios
previos de `.idea/inspectionProfiles/Project_Default.xml` y
`app/src/debug/res/xml/network_security_config.xml`. Backend, documentos
originales, dependencias y fuentes de pruebas permanecen sin cambios.

## Compilación ejecutada

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache spotlessApply :app:assembleDebug '-PauthBaseUrl=https://auth.clusterjam.invalid/' '-PprofileBaseUrl=https://profile.clusterjam.invalid/'
```

Se añadió `spotlessIdeHook` con rutas absolutas para limitar el formato a fuentes
de esta funcionalidad. La compilación inicial pasó en 48 s. Tras el ajuste de tamaño
de lista y presentación simultánea de éxito/error de refresco, la salida final
fue `BUILD SUCCESSFUL in 33s`, 40 tareas, sin tareas de pruebas. El APK está en
`app/build/outputs/apk/debug/app-debug.apk`; las URL .invalid solo se usaron para
compilar y no se contactaron.

El CI existente de Profile compila debug/release y omite las tareas de pruebas.
No se crearon ni ejecutaron pruebas unitarias, de integración o instrumentadas.
No se realizaron pruebas funcionales/visuales, llamadas al backend de negocio ni
verificación en dispositivo. La compilación no acredita esos comportamientos.

El informe final reúne todas las entregas. Solo resta revisión/autorización e
integración del último PR; no se ha iniciado ninguna rama adicional.
