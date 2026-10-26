# Recuperación del inicio de Studio y del panel integrado

**Reincidencia el 26 de octubre:** el emulador API 35 volvió a cerrarse con `0xC0000005`. Los resultados de esta página describen las pruebas del día 25 y no constituyen una reparación estable. Se conserva el antecedente; el cambio posterior de renderizador se registra en [Reincidencia del emulador](../reincidencia-emulador-2026-10-26/LEEME.md).

## Problemas observados

Después de usar temporalmente los emuladores en ventanas separadas, se intentó recuperar Running Devices. Android Studio presentó `Start Failed`, con `FileSystemException` al acceder a `C:\Users\jcamp\AppData\Local\Google\AndroidStudio2026.1.4\.port` durante `DirectoryLock.lockOrActivate`. El archivo seguía inaccesible después de cerrar las instancias fallidas. No se estableció la causa original de ese bloqueo.

## Configuración aplicada

Se conservó la carpeta de sistema anterior y se creó `idea.properties` en `C:\Users\jcamp\AppData\Roaming\Google\AndroidStudio2026.1.4` (antes no existía), con:

```properties
idea.system.path=D:/AndroidAVD/studio/system-2026.1.4-recuperado
idea.log.path=D:/AndroidAVD/studio/system-2026.1.4-recuperado/log
```

Así Studio utiliza una carpeta de sistema nueva para cachés, índices y archivos de coordinación. La configuración y los plugins de Roaming se conservaron. La nueva carpeta requirió indexar nuevamente el proyecto. No se pulsó Reset Settings & Plugins ni se borró la carpeta anterior o el historial local que contiene.

Se volvió a habilitar `launchInToolWindow=true` en `options/emulator.xml`. En los dos AVD dedicados a compatibilidad, `hw.gpu.mode` cambió de `swiftshader` a `swangle`. Se conserva la anulación anterior `Vulkan = off` en `C:\Users\jcamp\.android\advancedFeatures.ini`. El registro de esta ejecución sigue informando `gles_mode_selected:swiftshader`; por ello no se atribuye la recuperación exclusivamente al cambio de GPU. Se cambiaron varios ajustes del entorno y no se aisló una única causa del problema integrado.

Los archivos `emulator-antes.xml`, `api24-config-antes.ini` y `api35-config-antes.ini` conservan la configuración anterior. `idea-properties-antes.txt` registra que las propiedades no existían y `idea-properties-aplicadas.txt` contiene las nuevas rutas.

## Prueba real del panel

Se pulsó el botón Run de Android Studio para cada AVD, cerrando uno antes de abrir el otro. No se sustituyó el lanzamiento del IDE por `adb am start`.

| Comprobación | Android 7 / API 24 | Android 15 / API 35 |
| --- | --- | --- |
| Emulador visible dentro de Running Devices | Sí | Sí |
| `sys.boot_completed` y `dev.bootcomplete` | Ambos 1 | Ambos 1 |
| Instalación y lanzamiento por el IDE | Correctos | Correctos |
| Aplicación visible | Bienvenido | Tu cuenta, preparando perfil existente |

Los JSON y capturas de esta carpeta registran arranque y aplicación. `studio-arranque-panel.txt` conserva extractos del lanzamiento. API 35 se abrió alrededor de las 08:22 y API 24 alrededor de las 08:24 del 25 de octubre de 2026, hora de Colombia. El estado del perfil de API 35 no se considera una prueba de autenticación o conectividad Firebase.

No cambió código Android, recursos, Firebase, reglas ni dependencias. No se borraron datos de los AVD ni se usó Wipe Data. Esta prueba cubre instalación y apertura, no repite los recorridos funcionales completos del taller. Las compilaciones realizadas por Run terminaron correctamente; los resultados previos de assembleDebug y lintDebug permanecen en las evidencias anteriores del mismo día.

Después se cerró Studio normalmente, confirmando el diálogo Exit. El log registra `IDE SHUTDOWN` a las 08:26:30. Se abrió una nueva instancia a las 08:26:41, que cargó el proyecto con la nueva carpeta de sistema sin repetir el error `.port`. La preferencia integrada quedó guardada como `true`. Este segundo inicio comprueba la recuperación tras un cierre normal, sin garantizar todos los reinicios futuros del sistema.

Se repitió Run con API 24 después de reabrir Studio: a las 08:29 volvió a abrirse Bienvenido en el panel integrado, con ambas propiedades de arranque en 1 y proceso de la app activo. `api24-tras-reinicio.json` registra esta comprobación. Se dejó Studio abierto con ese emulador; API 35 quedó cerrado. Se detuvo el daemon de Gradle después de compilar para liberar memoria.

## Uso y reversión

Mantener un solo AVD abierto por la memoria disponible. Para volver a las ventanas independientes, desmarcar Settings > Tools > Emulator > Launch in the Running Devices tool window. Los archivos de respaldo permiten restaurar los dos config.ini con los emuladores cerrados.

Para revertir las rutas de sistema habría que cerrar Studio y retirar las dos propiedades agregadas; la carpeta anterior continúa disponible, pero contiene el `.port` que falló. No se recomienda volver a ella sin resolver ese bloqueo. Estos ajustes pertenecen al equipo del usuario y no se distribuyen dentro del APK.
