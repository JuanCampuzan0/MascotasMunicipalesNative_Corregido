# Reincidencia del fallo del emulador: 26 de octubre

## Evidencia nueva

La recuperación del día anterior no resultó estable. El registro del 26 de octubre muestra que API 35 terminó a las 11:12:08 con `-1073741819 (0xC0000005)`. A las 11:17:06 Studio mostró `Device did not start`. El cierre fue del emulador nativo, antes de iniciar la aplicación; no demuestra un fallo Kotlin de la app. `fallo-previo.txt` conserva los extractos pertinentes.

En ese intento el AVD tenía `hw.gpu.mode = swangle`, pero el registro identificó el adaptador ANGLE sobre NVIDIA Direct3D11. No se pudo determinar el módulo exacto que produjo la violación de acceso. No se atribuye una causa definitiva únicamente por ese código.

## Cambio acotado

Con Studio y los emuladores cerrados, se respaldaron ambos config.ini y se cambió únicamente `hw.gpu.mode = host` en los dos AVD de compatibilidad. El nuevo registro confirma `gles_mode_selected:host` y el traductor OpenGL de NVIDIA, sin la ruta ANGLE/Direct3D del intento fallido. Se mantienen Vulkan desactivado, inicio en frío, panel integrado y las demás opciones anteriores. No se reinstaló el SDK ni se cambiaron controladores, ajustes de seguridad o datos de los teléfonos virtuales.

La selección de renderizador está documentada en la [guía oficial de aceleración](https://developer.android.com/studio/run/emulator-acceleration). Es un cambio del entorno de desarrollo de este equipo; no modifica el APK ni Firebase. Para revertirlo, cerrar los AVD y restaurar `api24-antes.ini` y `api35-antes.ini` en sus respectivas rutas de `D:\AndroidAVD\avd`.

## Validación

Se usa Run real de Android Studio y se observa la app dentro de Running Devices. Las propiedades `sys.boot_completed` y `dev.bootcomplete`, el PID y la Activity se consultan después del lanzamiento realizado por el IDE. Las pruebas son de arranque, instalación y apertura; no repiten los recorridos funcionales ni crean datos en Firebase. Un inicio correcto no garantiza estabilidad en todos los reinicios futuros de Windows.

No se cambiaron archivos de `app/src/main`, versiones, reglas o índices. Las compilaciones realizadas por Run se registran junto con los resultados. Los datos de los emuladores se conservan y se prueba uno a la vez.

### Resultados observados

| Prueba desde Run con panel integrado | Resultado |
| --- | --- |
| API 35, primer arranque en frío | App visible en Nuevo Reporte; ambas propiedades en 1; PID 1509 |
| API 35, segundo arranque en frío tras cerrar el AVD | App visible en Nuevo Reporte; ambas propiedades en 1; PID 1478 |
| API 24, arranque en frío | App visible en Bienvenido; ambas propiedades en 1; PID 2574 |

Los JSON y PNG conservan estas comprobaciones. `pruebas-host.txt` contiene los adaptadores gráficos y lanzamientos del IDE, además del cierre correcto de API 35 con código 0. Studio mantiene habilitado el panel integrado. Se deja API 24 abierto y API 35 cerrado. Los daemons de Gradle se detienen tras terminar para liberar memoria.

La ejecución final `gradlew.bat :app:assembleDebug :app:lintDebug` terminó con `BUILD SUCCESSFUL` en 1 min 21 s. La salida completa está en `gradle-verificacion.txt`; los informes de lint están en `app/build/reports`. Se detuvo el daemon después de finalizar. No se modifica el APK exportado anteriormente para la entrega del taller.
