# Reparación del arranque de los emuladores

Fecha: 21 de octubre de 2026.

Android Studio mostraba `Emulator failed to connect within 5 minutes`. ADB confirmó `emulator-5554 offline` con el AVD API 35. El registro mostraba selección de gráficos del host, un aviso al cargar OpenGL por software y actividad de restauración de estado. Windows tenía aproximadamente 650 MiB de memoria libre en la primera consulta. No se observó un error de compilación de Kotlin asociado a este fallo.

Los perfiles de la preparación anterior no tenían guardados los ajustes gráficos y de arranque usados en la prueba por terminal. Se respaldó cada `config.ini` en esta carpeta antes de corregirlo. Los archivos de datos del dispositivo no se borraron ni se usó Wipe Data.

Se guardaron estos ajustes en los AVD `Mascotas_Compat_API24` y `Mascotas_Compat_API35` de `D:/AndroidAVD/avd`:

- Gráficos habilitados con renderizador `swiftshader`.
- Arranque en frío habilitado y Fast Boot deshabilitado.
- Restauración de snapshots de primer arranque deshabilitada.

Se detuvo el emulador atascado y el daemon de Gradle inactivo para liberar memoria. No se cerraron otras aplicaciones del usuario. La prueba se hizo con un emulador a la vez y con los mismos argumentos de inicio usados por Android Studio, sin pasar una anulación de GPU por terminal.

Resultados:

- API 35: ADB pasó a `device`, Android informó arranque completo y el registro del emulador indicó 37.122 ms. La aplicación instalada abrió con `Status: ok`; se comprobó visualmente el formulario de acceso.
- API 24: ADB pasó a `device`, `sys.boot_completed` devolvió `1` y la aplicación abrió con `Status: ok`. Después de la preparación inicial se verificó el formulario mediante la jerarquía de vistas y una nueva captura; no aparecieron errores AndroidRuntime en la consulta realizada.

Los archivos `api24-app-inicio.txt`, `api35-app-inicio.txt`, las capturas y los registros conservan la evidencia. Ambos emuladores se apagaron al finalizar para liberar RAM. Para trabajar, seleccionar uno de ellos en Android Studio y pulsar Run. Evitar iniciar los dos simultáneamente cuando haya poca memoria libre.

No se cambió el código Android, la configuración de Firebase ni el APK. No se repitieron compilación y lint porque la reparación solo afectó a la configuración local de los dispositivos virtuales. Estas pruebas confirman arranque y apertura del formulario; no completan los recorridos funcionales de cuentas, mascotas y reportes.
