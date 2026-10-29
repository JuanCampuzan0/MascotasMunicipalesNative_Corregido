# Reparación de arranque del 25 de octubre de 2026

**Actualización posterior:** el usuario volvió a obtener el fallo desde Android Studio después de estas pruebas por terminal. Se corrigió adicionalmente la preferencia del emulador integrado y se probaron ambos AVD con el botón Run real. El resultado vigente y la forma de usarlo están en [la reparación del IDE](../reparacion-studio-2026-10-25/LEEME.md). Las pruebas de esta carpeta se conservan como antecedente, no como demostración suficiente del Run real.

Android Studio mostraba `Device did not start` al intentar utilizar los AVD del taller. Se encontró un proceso de API 35 atascado, inicialmente `offline`, y el historial de API 24 registraba un cierre con código `1073741845` antes de ejecutar la aplicación. WHPX pasó sus comprobaciones y había memoria y disco disponibles. No se atribuye ese cierre a un error Kotlin ni se identifica una causa única del cierre anterior de API 24.

## Corrección aplicada

Se detuvieron únicamente los procesos bloqueados del AVD del taller. Los datos, imágenes de disco y sesiones se conservaron; no se usó Wipe Data, no se reinstalaron las imágenes de Android y no se eliminaron snapshots ni carpetas del usuario.

API 35 arrancó en 28–31 segundos al probar sin Vulkan, tanto sin ventana como con los argumentos del modo integrado. Después se guardó la anulación explícita en **`C:\Users\jcamp\.android\advancedFeatures.ini`**:

```ini
Vulkan = off
```

La versión instalada del emulador, 37.1.11, lee allí las preferencias de funciones aunque los AVD estén en `D:\AndroidAVD\avd` y `ANDROID_USER_HOME` apunte a D:. Un primer archivo de prueba en `D:\AndroidAVD` no fue leído; se trasladó a esta carpeta de evidencia y no se dejó como una configuración efectiva. La verificación final registra `loadUserOverrideFeature: Vulkan = off` y `overridden to 'disabled'`.

Este ajuste es **local al usuario del emulador y alcanza sus otros AVD**, no forma parte del APK. Evita ofrecer Vulkan como función del dispositivo emulado; no debe usarse este entorno para afirmar compatibilidad de aplicaciones que requieran Vulkan. Mascotas Municipales conserva su código, versiones, paletas y configuración Firebase. Los AVD mantienen SwiftShader y el arranque en frío configurados en la reparación anterior.

La anulación es reversible: si el archivo no existía, retirar la línea `Vulkan = off`; si había una configuración previa, restaurar el respaldo correspondiente. `advancedFeatures-usuario-antes.txt` registra que no existía en esta reparación. `advancedFeatures-aplicado.ini` conserva lo que se guardó.

## Verificación final

Se ejecutó **un emulador a la vez**, con los mismos argumentos que utilizó Android Studio, agregando únicamente el registro detallado para observar el arranque:

```text
emulator.exe -netdelay none -netspeed full -no-snapstorage -avd Mascotas_Compat_API35 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300 -verbose
emulator.exe -netdelay none -netspeed full -no-snapstorage -avd Mascotas_Compat_API24 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300 -verbose
```

No se añadió `-feature -Vulkan` en estas comprobaciones finales: se verificó que la preferencia guardada se aplicara por sí sola.

| Comprobación | Android 7 API 24 | Android 15 API 35 |
| --- | --- | --- |
| Preferencia Vulkan cargada y desactivada | Confirmada en el registro | Confirmada en el registro |
| ADB y `sys.boot_completed` | `device`, `1` | `device`, `1` |
| Inicio de `com.mascotasmunicipales/.MainActivity` | `Status: ok` | `Status: ok` |
| Aplicación sigue ejecutándose tras abrir | PID guardado y captura | PID guardado y captura |
| Pantalla observada | Bienvenido, formulario de acceso | Nuevo Reporte, restaurado desde la sesión existente |

Se conservaron los datos de ambos dispositivos y no se introdujeron cuentas ni se enviaron mascotas o reportes. La captura de API 35 se tomó después de que terminó de preparar la sesión; una captura transitoria anterior no se interpreta como fallo de arranque. Esta reparación verifica arranque y apertura de la app, no repite los recorridos funcionales completos del 22 de octubre ni demuestra su funcionamiento remoto.

También pasaron `gradlew.bat :app:assembleDebug :app:lintDebug --offline`: **0 errores y 40 advertencias**. El daemon de Gradle y los emuladores de prueba se apagaron al terminar para liberar RAM. No se dejó un dispositivo oculto ejecutándose.

## Para usarlo en Android Studio

1. Abrir o reiniciar Android Studio si seguía abierto durante la reparación.
2. Seleccionar `Mascotas Compat API24` o `Mascotas Compat API35` y pulsar Run.
3. Antes de cambiar al otro AVD, detener el anterior desde Device Manager. Trabajar con uno a la vez en este equipo.

No hace falta volver a descargar Android ni utilizar Wipe Data. Si se repite el bloqueo, conservar el mensaje y los registros actuales antes de alterar datos; las pruebas anteriores no garantizan que nunca aparezca otro problema del host.

Referencia técnica: [diagnóstico oficial de Android Emulator](https://developer.android.com/studio/run/emulator-troubleshooting), que documenta la opción de desactivar Vulkan al diagnosticar fallos gráficos. Los archivos de esta carpeta registran la evidencia local y la configuración utilizada; algunos contienen rutas del equipo y no forman parte del APK.
