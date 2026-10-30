# Entrega del módulo Profile en Android

Informe preparado el 30 de octubre de 2026. La implementación acordada está
incluida en las cuatro ramas; queda pendiente revisar y autorizar el squash merge
del último PR de direcciones. No se declara integrado un PR todavía abierto.

## Funcionalidad

El cliente consulta el perfil real y distingue carga, error, aprovisionamiento
404 PROFILE_NOT_READY, PENDING y COMPLETED. La espera de aprovisionamiento admite
como máximo tres reintentos tras 1, 2 y 4 segundos y luego requiere acción manual.
No crea perfiles ni inventa datos cuando el backend no responde.

Tras autenticación o restauración se consulta Profile antes de continuar al
catálogo. PENDING abre onboarding; COMPLETED habilita catálogo y edición. Las
rutas restauradas se reconcilian con el estado actual. Un borrador pendiente se
conserva como edición si el servidor ya indica que el perfil está completo.

Onboarding/edición comparten formulario con nombre, teléfono y correo de contacto
precargados desde Profile, validación, progreso, conservación del borrador,
confirmación real y control de envíos duplicados. El correo de acceso continúa
en Auth. No son editables userId ni onboardingStatus. Solo una respuesta real
permite afirmar que se guardó y que el onboarding está completado.

Mis direcciones permite consultar, agregar, editar, eliminar con confirmación y
elegir una predeterminada. El código postal es texto y conserva ceros iniciales.
El número interior es opcional y puede borrarse enviando null. No se selecciona
automáticamente la primera dirección ni un reemplazo al borrar la predeterminada.
Las direcciones son opcionales para completar onboarding y funcionan con PENDING.

Los conflictos 412/428 y resultados inciertos requieren lecturas y revisión
explícita antes de otro guardado. Un alta incierta presenta la lista actual para
revisar si ya se creó, sin repetir POST automáticamente. Se refresca el directorio
completo tras una mutación confirmada y no se actualizan predeterminadas de forma
optimista. La eliminación confirma el ítem leído con su revisión vigente.

## Arquitectura conservada

Se extiende `features/profile/{data,domain,presentation}` y el único módulo `app`.
Se reutilizan Retrofit/Gson, repositorio/implementación con Result y Dispatchers.IO,
mapeadores, ViewModels con StateFlow, AuthForm/AuthField y la inyección manual de
AppContainer/ViewModelFactories. Las rutas están en el NavHost original y usan el
tema, tipografía, componentes y espacios existentes. No se introducen bibliotecas,
una aplicación distinta, otro almacenamiento de tokens ni otro sistema de sesión.

SessionAccess conserva la renovación/invalidación del repositorio Auth compartido.
Los tokens solo salen al origen/rutas protegidas configurados. POST/PATCH y DELETE
con If-Match no se reenvían automáticamente; Auth conserva su DELETE sin condición.
Los errores usan los códigos de Problem Details y mensajes locales, sin imprimir
credenciales, contactos, direcciones, cuerpos ni excepciones internas.

Los borradores usan SavedStateHandle, siguiendo el registro existente. Conservan
campos, propietario y etapa, no tokens, contraseñas, ETags ni una bandera local de
onboarding completado. Cambiar de cuenta, cerrar sesión o descartar los limpia.
El listado/revisiones no se guardan en preferencias o base local. La confirmación
pendiente de eliminación tampoco se restaura ni se ejecuta automáticamente.

## Historial de integración

| Rama | PR | Squash en main |
| --- | --- | --- |
| `feat/profile/add-api-integration` | [#13](https://github.com/Miguel1281/ClusterJAMClient/pull/13) | `4bdaee9` |
| `feat/profile/add-profile-screen` | [#14](https://github.com/Miguel1281/ClusterJAMClient/pull/14) | `1c608a3` |
| `feat/profile/add-profile-forms` | [#15](https://github.com/Miguel1281/ClusterJAMClient/pull/15) | `a9e0b04` |
| `feat/profile/add-address-management` | Último PR de esta entrega | Pendiente de autorización/integración |

Cada rama parte de main actualizado tras el squash autorizado de la anterior.
El último punto de partida es `a9e0b0442378b43e85706d3c9fde1dc1887669b1`.
Los informes `delivery-01.md` a `delivery-04.md` y `pull-requests/` contienen
comandos/resultados y listas de archivos por etapa; los estados de los informes
previos describen su momento de entrega. El diagnóstico original y las fuentes
normativas están en `implementation-plan.md`.

Se preservaron y excluyeron los cambios locales originales de
`.idea/inspectionProfiles/Project_Default.xml` y
`app/src/debug/res/xml/network_security_config.xml`. El backend y los documentos
originales del proyecto no se modificaron durante esta implementación Android.

## Contrato consumido

Base configurable con `profileBaseUrl`; las rutas ya incluyen `/api/v1`.

| Método y ruta | Uso |
| --- | --- |
| GET `/api/v1/profiles/me` | Perfil y ETag |
| PATCH `/api/v1/profiles/me` | Contacto/onboarding con If-Match |
| GET `/api/v1/profiles/me/addresses` | Lista y ETag de colección |
| POST `/api/v1/profiles/me/addresses` | Alta con If-Match de colección |
| GET `/api/v1/profiles/me/addresses/{addressId}` | Ítem y ETag propio |
| PATCH `/api/v1/profiles/me/addresses/{addressId}` | Edición/predeterminada con If-Match de ítem |
| DELETE `/api/v1/profiles/me/addresses/{addressId}` | Eliminación con If-Match de ítem y 204 sin cuerpo |

El backend de referencia es `cluster-jam` en `41f620b`, su OpenAPI versionado y
DTO/controladores reales. No se añadieron endpoints supuestos ni se cambió el
backend para adaptar el cliente. La integración Orders no forma parte del módulo.

## Configuración y comprobaciones

`profileBaseUrl` genera PROFILE_BASE_URL. Si se omite, hereda authBaseUrl para el
gateway compartido. Ambas se normalizan con el mecanismo Gradle existente; release
requiere HTTPS y la distribución aplica las restricciones de host real.

```powershell
# Gateway compartido alcanzable desde el dispositivo:
.\gradlew.bat :app:assembleDebug '-PauthBaseUrl=https://api.example.com/'

# Servicios locales en puertos diferentes del equipo anfitrión del emulador:
.\gradlew.bat :app:assembleDebug '-PauthBaseUrl=http://10.0.2.2:8080/' '-PprofileBaseUrl=http://10.0.2.2:8081/'
```

Estos son ejemplos de configuración; se deben usar las URL/puertos reales del
entorno. El comando no inicia el backend. Se conserva la política de red/TLS de
la aplicación, incluida la configuración debug preexistente.

Se ejecutó assembleDebug en cada rama y pasó la compilación local. Los PR #13–15
pasaron la compilación debug/release de GitHub; el último PR aplica la misma ruta
de compilación sin pruebas. La evidencia exacta de la última rama está en
`delivery-04.md`. El workflow omite las tareas de pruebas en ramas `/profile/`
y sus squash commits `(profile):`, conservando el flujo anterior para otros cambios.

No se crearon ni ejecutaron pruebas unitarias, de integración o instrumentadas
durante esta tarea. No se realizó validación funcional contra backend en ejecución,
ni revisión visual o pruebas en dispositivo/emulador. Compilación y revisión del
código no demuestran esos resultados. Las pruebas existentes permanecen intactas.

## Pendiente real

Revisar los checks del último PR y autorizar su squash merge, actualizando main
después. No queda otra rama de implementación prevista. El funcionamiento en
el dispositivo con su URL, conectividad y certificados reales debe validarse en
ese entorno antes de considerar el APK verificado funcionalmente.
