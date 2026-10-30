# Integración de ProfileService en Android

Análisis del 30 de octubre de 2026. Base del cliente:
`6f79e2fac5981a56398153009fa546691a79126c` (`main`, PR #12).
Contrato backend consultado en `cluster-jam`, commit
`41f620b581853a3e8cffdca43d296ff38f910e08` (Profile PR #53).

Avance: PR #13 integrado por squash con autorización explícita en `4bdaee9`,
PR #14 en `1c608a3` y PR #15 en `a9e0b04`. La última rama,
`feat/profile/add-address-management`, parte de ese main actualizado y añade
direcciones e informe final. Solo queda integrar su PR tras revisión/autorización.
No se crean ni ejecutan pruebas automatizadas.

## Arquitectura existente y referencias

El cliente tiene un único módulo Android `app`, con paquetes bajo
`com.clusterjam.mobile`. No usa Hilt, Koin ni una capa separada de casos de uso.
`ClusterJamApplication` mantiene un `AppContainer` por proceso. Las fábricas
manuales proporcionan los repositorios a los ViewModels.

| Responsabilidad | Referencia Auth existente | Aplicación en Profile |
| --- | --- | --- |
| HTTP | `features/auth/data/remote/AuthApiService.kt` y `core/network/NetworkModule.kt` | Interfaz Retrofit y DTO Gson en `features/profile/data/remote` |
| Repositorio | `features/auth/domain/repository/AuthRepository.kt`, `data/repository/AuthRepositoryImpl.kt` | Contrato de dominio y repositorio con resultados `Result` y coroutines |
| Respuestas/fallos | `AuthResponseMapper.kt`, `AuthErrorMapper.kt`, `ProblemDetailsParser.kt` | Modelos separados de DTO; fallos por código sin mostrar mensajes internos |
| Sesión | `SecureStorageImpl.kt`, `AuthRepositoryImpl.kt`, `SessionAccess.kt` | Un único almacenamiento cifrado y coordinador de renovación; Profile no guarda tokens |
| Presentación | `LoginViewModel.kt`, `RegistrationViewModel.kt`, sus `UiState` | ViewModels con `MutableStateFlow`, estados inmutables y `viewModelScope` |
| Formularios | `AuthComponents.kt`, `JamComponents.kt` | Reutilizar campos, acciones, scroll, feedback accesible y manejo del teclado |
| Inyección | `AppContainer.kt`, `ViewModelFactories.kt` | Extender las dependencias manuales existentes |
| Navegación | `AppNavHost.kt`, `Routes.kt`, `MainScaffold.kt`, `ClusterJamApp.kt` | Integrar rutas en el único NavHost y conservar el límite de sesión/usuario |

Versiones declaradas: Kotlin 2.2.10, AGP 9.4.1, Gradle 9.6.0, JDK del daemon 25,
Compose BOM 2026.02.01, Retrofit/Gson 2.11.0, OkHttp 3.14.9, Navigation Compose
2.8.4 y ViewModel Compose 2.8.7. No se añaden bibliotecas ni se actualizan versiones.
La aplicación conserva su tema claro azul tinta, `JamSpacing`, tipografía y
componentes Material existentes.

## Fuentes de requisitos

Se consultaron los documentos originales en `ProyectoJ/Proyecto`:

- `Proyecto-Cluster-JAM.docx`: RF-04, responsabilidades de Profile y cliente Kotlin/Compose.
- `Decisiones-diseño-Cluster-JAM.docx`: alta asíncrona, perfil mínimo, onboarding
  posterior a autenticación y separación de correo de acceso/contacto.
- `Estandar-codificacion.docx`: organización Kotlin, validación de JWT, privacidad
  de logs y convenciones de formato.
- `BD-Cluster-JAM.pdf`, página 2: campos/longitudes de perfil y dirección, estados
  PENDING/COMPLETED, 0..N direcciones y como máximo una predeterminada.

El OpenAPI versionado y los controladores/DTO de `services/profileService` son
la autoridad para las rutas, cuerpos, cabeceras y errores HTTP. No se modifican
ni el backend ni los documentos originales durante esta tarea.

## Contrato real

Todas las operaciones de negocio requieren `Authorization: Bearer` de la sesión
existente. Profile obtiene al propietario de `sub`; no se envía otro userId.

| Método y ruta | Respuesta | Precondición |
| --- | --- | --- |
| GET `/api/v1/profiles/me` | 200 perfil y ETag; 304 solo con lectura condicional | JWT |
| PATCH `/api/v1/profiles/me` | 200 perfil y nuevo ETag | If-Match del perfil |
| GET `/api/v1/profiles/me/addresses` | 200 `{items}` y ETag de colección | JWT |
| POST `/api/v1/profiles/me/addresses` | 201 dirección, Location y ETag de ítem | If-Match de colección |
| GET `/api/v1/profiles/me/addresses/{addressId}` | 200 dirección y ETag de ítem | JWT |
| PATCH `/api/v1/profiles/me/addresses/{addressId}` | 200 dirección y ETag actual | If-Match de ítem |
| DELETE `/api/v1/profiles/me/addresses/{addressId}` | 204 sin cuerpo | If-Match de ítem |

El perfil devuelve `userId`, `fullName`, `phone`, `contactEmail`,
`onboardingStatus`, `createdAt` y `updatedAt`. Nombre/teléfono pueden ser null;
el correo solo es null en registros históricos. PATCH permite exclusivamente
`fullName` (120 caracteres), `phone` (30, con 7–15 dígitos y puntuación admitida)
y `contactEmail` (254, correo válido). Se omiten campos no editados; no se envían
null, cadenas vacías, identidad ni onboarding. La validación local anticipa
errores básicos y la validación del servidor sigue siendo definitiva.

Dirección: label 40, recipient/neighborhood/municipality/state 120, street 160,
exteriorNumber/interiorNumber 20, postalCode 5 como texto, country ISO alpha-2
de dos caracteres e isDefault booleano. Solo interiorNumber puede borrarse con
null. La selección predeterminada es explícita; ninguna dirección es obligatoria
para completar onboarding. Cualquier cambio invalida revisiones anteriores del
directorio: se vuelven a leer antes de otra mutación.

Los errores son Problem Details con `code`/`traceId`: 401/403 para sesión/permiso,
404 `PROFILE_NOT_READY` para aprovisionamiento pendiente, 404 `ADDRESS_NOT_FOUND`
para dirección ausente o ajena, 412 `PRECONDITION_FAILED`, 428
`PRECONDITION_REQUIRED`, 422 de validación y 503 de dependencia no disponible.
También se contemplan errores de red, timeout, 400, 413, 415 y 500. No se
suponen endpoints 202/409 ni estructuras de error de campo no documentadas.

## Pantallas y navegación prevista

La pantalla Perfil actual solo muestra la identidad de Auth y acciones de
seguridad. Se ampliará para mostrar datos reales de contacto y onboarding,
manteniendo separado el correo de acceso.

El flujo será: autenticación existente -> consulta de Profile -> espera acotada
si `PROFILE_NOT_READY` -> formulario si PENDING -> catálogo si COMPLETED. Tras
guardar se utiliza exclusivamente el estado devuelto por el servidor. La entrada
a la aplicación y la restauración de sesión vuelven a consultar el backend;
no se guarda una bandera local de onboarding completo.

Se incorporarán consulta de perfil, formulario compartido de onboarding/edición,
lista de direcciones y formulario de alta/edición. La eliminación tendrá diálogo
de confirmación. Todos usarán el NavHost actual; los formularios conservarán
valores ante errores y evitarán envíos simultáneos. Los reintentos automáticos
de aprovisionamiento serán acotados, únicamente para lecturas; después habrá
reintento explícito. Los conflictos de revisión exigirán refrescar/reconciliar
sin repetir automáticamente una escritura.

## División incremental

| Orden y rama | Alcance y referencias antes de implementar |
| --- | --- |
| 1 `feat/profile/add-api-integration` | NetworkModule, AuthApiService, SessionAccess, repositorios/mapeadores Auth y AppContainer: URL, GET/PATCH, modelos, revisión, validación y fallos |
| 2 `feat/profile/add-profile-screen` | LoginViewModel/UiState, ProfileScreen y componentes comunes: consulta real, carga/error y aprovisionamiento con espera limitada |
| 3 `feat/profile/add-profile-forms` | RegistrationViewModel/SavedStateHandle, AuthForm y AppNavHost: onboarding/edición con formulario compartido, guardado y navegación según backend |
| 4 `feat/profile/add-address-management` | API/repositorio Profile ya integrado y mismos patrones de formulario: DTO/CRUD de direcciones, predeterminada, eliminación y rutas |

Cada rama incluye documentación, compilación, commit convencional y PR hacia
main. La siguiente rama dependiente solo se crea tras autorización y squash
merge de la anterior, actualizando main. No se desarrollará toda la función en
una única rama ni se simulará una integración pendiente.

## Dependencias y compatibilidad

La URL de Profile debe poder diferir de Auth. Se incorpora `profileBaseUrl`
mediante la misma configuración Gradle/BuildConfig; por omisión reutiliza la URL
de Auth para el gateway compartido. La sesión existente solo permitía llamadas
al origen de Auth: se extiende de manera explícita al origen/ruta de Profile,
sin crear otro almacenamiento ni otro renovador. El Retrofit protegido usa
`Call` porque ese es el contrato ya existente de `SessionAccess.execute`.

El backend exige ETag/If-Match, aunque la solicitud original solo enumera los
verbos REST. Se conserva la cabecera exacta y no se inventan revisiones.
Los valores legacy null se respetan en la lectura. La documentación presenta
202 como ejemplo temporal; el contrato implementado devuelve 404
`PROFILE_NOT_READY`. Se usa este último, sin cambiar el backend.

El CI existente incluye pruebas JVM. Para respetar la instrucción explícita,
las ramas `/profile/` y sus squash commits con scope `(profile):` ejecutarán
solo compilación debug/release; la tarea previa queda para el resto de cambios.
No se crean, modifican ni ejecutan pruebas durante esta implementación.
Compilar no acredita conectividad ni verificación funcional en dispositivo.

Los cambios locales preexistentes de `.idea/inspectionProfiles/Project_Default.xml`
y `app/src/debug/res/xml/network_security_config.xml` se conservan y quedan fuera
de los commits. Para otro host HTTP de desarrollo deberá existir la excepción
debug correspondiente; release conserva HTTPS y no debilita la política TLS.
