# Guion de demostración — segundo corte

Este recorrido usa únicamente datos ficticios. Antes de exponer, confirme que el emulador tiene internet, que Firebase continúa en Spark y que la app abre sin cambios pendientes.

## Preparación

1. Compile con `gradlew.bat :app:assembleDebug`.
2. Para una demostración local completa, inicie los emuladores de Authentication y Firestore y ejecute `scripts/seed-workflow-emulator.mjs --apply` con `DEMO_PASSWORD` definido.
3. La siembra pública opcional `scripts/seed-demo.mjs` tiene vista previa por defecto, crea solo IDs ausentes y nunca se ejecuta desde la app.
4. El código público de Luna creado por esa siembra es `ZPQ-LUNA0000`. Puede escribirlo en la consulta manual si el emulador no incluye Google Play Services o no tiene cámara.

## Recorrido ciudadano

1. Abra la app y muestre el icono y la pantalla de inicio.
2. Antes de iniciar sesión, abra **Consultar mascota por QR** y consulte manualmente `ZPQ-LUNA0000` si la ficha demo fue sembrada.
3. Inicie sesión como ciudadano.
4. En **Mascotas**, busque por nombre, especie, raza, comuna o código. Muestre que el filtrado no genera lecturas adicionales.
5. Registre una mascota ficticia y explique la conservación del borrador y la confirmación del servidor.
6. En **Reportes**, cree un reporte y pruebe los filtros Todos, Abiertos y Resueltos.
7. En **Territorio**, mueva el mapa, vuelva a centrarlo y muestre eventos aprobados.
8. En **Perfil**, abra Accesibilidad e Información de la aplicación.

## Recorrido profesional opcional

1. Entre con una cuenta veterinaria aprobada y proponga un evento futuro ficticio.
2. Entre con la cuenta administradora, revise el evento y apruébelo.
3. Regrese como ciudadano y confirme que aparece en Territorio.
4. Muestre el flujo de revisión, asignación, atención y cierre de un reporte usando únicamente datos ficticios.

## Contingencias

- Sin cámara o Google Play Services: use el campo manual de QR.
- Sin red: explique los estados de caché y use **Reintentar** al recuperar conexión.
- Sin datos remotos: muestre las pantallas vacías y ejecute la siembra únicamente en un entorno de demostración autorizado.
- No despliegue reglas, índices ni datos a Firebase durante la exposición sin verificar antes el proyecto seleccionado.
