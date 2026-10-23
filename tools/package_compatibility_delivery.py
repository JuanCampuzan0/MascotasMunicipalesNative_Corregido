"""Empaqueta fuentes y evidencia final sin cachés, datos locales ni credenciales."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'documentos/Entrega_Compatibilidad_Verificada_2026-10-22.zip'
EVIDENCE = ROOT / 'tests/evidencias-compatibilidad/funcionales-2026-10-22'
files = {}
def include(path, target):
    assert path.is_file(), path
    files[str(target).replace('\\', '/')] = path

normal = ROOT / 'apk/MascotasMunicipales-compatibilidad-2026-10-22.apk'
qa = ROOT / 'apk/MascotasMunicipales-QA-local-2026-10-22.apk'
doc = ROOT / 'documentos/Taller_Compatibilidad_Android_Resultados_2026-10-22.docx'
include(normal, normal.name)
include(doc, doc.name)
include(qa, 'pruebas-locales/' + qa.name)
include(ROOT / 'app/build/outputs/apk/androidTest/compatibility/app-compatibility-androidTest.apk', 'pruebas-locales/app-compatibility-androidTest.apk')
for name in ['.gitignore', 'README.md', 'ROLES.md', 'build.gradle.kts', 'settings.gradle.kts',
             'gradle.properties', 'gradlew', 'gradlew.bat', 'firebase.json', 'firestore.rules',
             'firestore.indexes.json', 'package.json', 'pnpm-lock.yaml', 'app/build.gradle.kts',
             'app/google-services.json', 'app/.gitignore']:
    include(ROOT / name, 'proyecto/' + name)
for folder in ['app/src', 'gradle', 'scripts', 'tools']:
    for path in (ROOT / folder).rglob('*'):
        if path.is_file() and '__pycache__' not in path.parts:
            include(path, 'proyecto/' + path.relative_to(ROOT).as_posix())
for path in (ROOT / 'tests').iterdir():
    if path.is_file() and path.suffix in ('.md', '.mjs'):
        include(path, 'proyecto/' + path.relative_to(ROOT).as_posix())
for path in EVIDENCE.rglob('*'):
    if not path.is_file(): continue
    relative = path.relative_to(EVIDENCE)
    if relative.parts[0] not in ['taller22d', 'normal-api24', 'normal-api35'] and len(relative.parts) != 1:
        continue
    include(path, 'proyecto/' + path.relative_to(ROOT).as_posix())

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()
for api in (24,35):
    env = json.loads((EVIDENCE / f'taller22d/api{api}/entorno.json').read_text(encoding='utf-8-sig'))
    assert env['apkSha256'] == sha(qa)
    env = json.loads((EVIDENCE / f'normal-api{api}/entorno.json').read_text(encoding='utf-8-sig'))
    assert env['apkSha256'] == sha(normal)

readme = '''TALLER DE COMPATIBILIDAD ANDROID - MASCOTAS MUNICIPALES
Actualización del 22 de octubre de 2026

Contenido
- El APK de la raíz es el instalador normal DEBUG de demostración. Conserva la conexión Firebase del proyecto. Se instaló, abrió y pasó la prueba de fotos en Android 7 y 15.
- El Word contiene matriz, seis respuestas técnicas, decisiones, incidencias, resultados y capturas. Conserva la información del documento aportado por el grupo.
- proyecto contiene las fuentes Kotlin reales modificadas, Gradle, reglas e índices, scripts y protocolos. Abrir esa carpeta en Android Studio y configurar la ruta local de su SDK. Las dependencias requieren caché o descarga para compilar. No incluye cachés, node_modules, local.properties, historial Git ni credenciales administrativas.
- proyecto/tests/evidencias-compatibilidad/funcionales-2026-10-22 contiene la ejecución final taller22d de ambas APIs, prueba del APK normal, compilación, lint y 33 pruebas de reglas.
- pruebas-locales contiene el APK QA y su instrumentación. Este APK comparte el código funcional principal, pero usa paquete separado y Authentication/Firestore en 10.0.2.2. REQUIERE iniciar los emuladores Firebase en el equipo: no usarlo como instalador normal independiente. El protocolo está en proyecto/tests/android-functional-compatibility.md.

Resultados
Los tres recorridos completos pasaron en Android 7 API 24 y Android 15 API 35 con el mismo APK QA: sesión, registro/consulta de mascota, creación/consulta de reporte. Se comprobó un pendiente ausente del servidor sin red, conservación del ID tras detener el proceso y confirmación sin duplicados al reconectar. Son pruebas automatizadas con cuentas ficticias y Firebase LOCAL; no prueban el backend remoto. No se sembraron cuentas ni registros remotos ni se habilitaron servicios pagos.

Antes de la entrega
Faltan las cuatro reflexiones individuales de 100 a 150 palabras. Cada integrante debe escribir la suya, revisar la evidencia y preparar la sustentación. Incorporarlas al Word y volver a guardar el documento antes de enviarlo. Este ZIP no debe presentarse como entrega completa de las reflexiones.
El taller Offline First de GUARDAR local y SINCRONIZAR manual hacia RTDB sigue pendiente y es otro requerimiento.
'''

with ZipFile(OUT, 'w', ZIP_DEFLATED, compresslevel=6) as z:
    z.writestr('LEER_PRIMERO.txt', readme.encode('utf-8-sig'))
    for target, path in sorted(files.items()):
        z.write(path, target)
    z.writestr('SHA256SUMS.txt', '\n'.join(f'{sha(path)}  {target}' for target,path in sorted(files.items()) if not target.startswith('proyecto/')) + '\n')
with ZipFile(OUT) as z:
    assert z.testzip() is None
    assert not any('/build/' in n or '/node_modules/' in n or '/.git/' in n for n in z.namelist())
    assert hashlib.sha256(z.read(normal.name)).hexdigest().upper() == sha(normal)
    print(f'{OUT}\n{len(z.namelist())} archivos; {OUT.stat().st_size / 1048576:.1f} MiB; integridad ZIP correcta')
