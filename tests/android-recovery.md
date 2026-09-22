# Regresión Android: sesión, borradores y envíos

Use un emulador Android y una copia de prueba que conecte Authentication y Firestore a los emuladores locales, como explica README. Use cuentas y animales ficticios. Ejecute las pruebas de reglas antes o después de esta sesión: esas pruebas limpian su base local entre casos.

1. Registre una cuenta ficticia A. Debe aparecer Inicio después de preparar su perfil. Durante la autenticación, los botones de ingreso y registro deben estar desactivados.
2. Abra Nuevo reporte y escriba nombre y descripción. Cambie tipo, especie y comuna. Abra Perfil y pulse Volver: debe volver al mismo formulario con sus valores.
3. Cambie temporalmente la escala de texto de Android y restáurela para provocar recreación de la Activity. Verifique pantalla, textos y selecciones. Pase la app a segundo plano, detenga su proceso y vuelva a abrirla: verifique el mismo borrador.
4. Quite la conectividad del emulador Android. Envíe el reporte. Debe quedar pendiente, con el formulario bloqueado. En la base local del servidor todavía no debe existir ese reporte. Reinicie la app sin reconectar: el reporte debe seguir pendiente.
5. Reconecte. Espere **Sincronizado · confirmado por el servidor**. Compruebe que existe exactamente un reporte y que conserva el ID guardado antes del reinicio. Reinicie otra vez: la confirmación debe mantenerse. El botón de enviar debe seguir desactivado; crear otro reporte exige pulsar la acción explícita.
6. Repita la conservación del borrador con una mascota y selecciones distintas de las iniciales. Guárdela y compruebe sus campos en el servidor local.
7. Cierre sesión, registre una cuenta B y abra los formularios: no deben contener los borradores de A. Vuelva a A: sus borradores y estado deben recuperarse.
8. Solo en las reglas del emulador local, niegue temporalmente la creación de `users/{uid}`. Registre una cuenta ficticia nueva: debe aparecer un error visible con **Reintentar perfil**, sin entrar a Inicio. Restaure las reglas locales y pulse reintentar: debe entrar sin pedir crear otra cuenta.
9. Solo en las reglas locales, niegue temporalmente la creación de reportes. Envíe uno válido: debe quedar sin confirmar y ofrecer **Reintentar mismo envío**. Reinicie, restaure las reglas y reintente. Debe confirmarse exactamente un documento con el ID original.
10. Restaure reglas, conectividad y escala de texto, cierre sesión y reinstale el APK normal. No copie la configuración temporal de emuladores al APK entregado.

La prueba 8 valida un fallo entre Authentication y Firestore: crear la cuenta no garantiza que se haya creado su perfil. La prueba 4–5 comprueba una confirmación recuperada, sin depender del callback de una Activity o proceso anterior. No se ha comprobado este protocolo con dos dispositivos ni con la base remota.
