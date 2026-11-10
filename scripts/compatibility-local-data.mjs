import { initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { readFileSync, writeFileSync } from 'node:fs';

// Este auxiliar nunca admite un proyecto remoto ni hosts de red externos.
const projectId = process.env.FIREBASE_PROJECT_ID;
if (projectId !== 'demo-mascotas-compat') throw Error('Solo se permite demo-mascotas-compat.');
for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST']) {
  const value = process.env[key];
  if (!value || !['127.0.0.1', 'localhost', '[::1]'].includes(new URL(`http://${value}`).hostname)) {
    throw Error(`${key} debe apuntar a loopback.`);
  }
}
initializeApp({ projectId });
const db = getFirestore();
const action = process.argv[2];
if (action === 'seed') {
  for (const [key, name, species] of [['luna', 'Luna', 'Perro'], ['michi', 'Michi', 'Gato'], ['rocco', 'Rocco', 'Perro'], ['nina', 'Nina', 'Perro']]) {
    const ref = db.doc(`pets/compat-fixture-${key}`);
    if (!(await ref.get()).exists) await ref.create({
      ownerId: 'compat-fixture-owner', name, species, breed: 'Criolla ficticia', sex: 'Hembra',
      age: '2 años', color: 'Gris', territoryId: 'comuna-1', status: 'Con responsable',
      photoKey: key, qrCode: `ZPQ-${key.toUpperCase().padEnd(8, '0')}`,
      createdAt: Timestamp.now(), updatedAt: Timestamp.now()
    });
  }
  console.log('Cuatro fichas ficticias locales preparadas; no se sobrescribieron documentos existentes.');
} else if (['pending', 'final'].includes(action)) {
  const run = JSON.parse(readFileSync(process.argv[3], 'utf8').replace(/^\uFEFF/, ''));
  if (run.result !== 'APROBADO' || run.project !== projectId) throw Error('La instrumentación no terminó correctamente.');
  const pet = await db.doc(`pets/${run.petId}`).get();
  const report = await db.doc(`reports/${run.reportId}`).get();
  const pending = await db.doc(`reports/${run.pendingId}`).get();
  const user = await getAuth().getUser(run.uid);
  if (!user.email?.endsWith('@example.test')) throw Error('La cuenta no es ficticia.');
  if (pet.get('ownerId') !== run.uid || pet.get('name') !== run.petName || report.get('ownerId') !== run.uid) {
    throw Error('El servidor no coincide con los registros del recorrido.');
  }
  if (action === 'pending' && pending.exists) throw Error('El reporte offline llegó al servidor antes de reconectar.');
  const ownedPets = await db.collection('pets').where('ownerId', '==', run.uid).get();
  const ownedReports = await db.collection('reports').where('ownerId', '==', run.uid).get();
  const expectedReports = action === 'pending' ? 1 : 2;
  if (ownedPets.size !== 1 || ownedReports.size !== expectedReports) throw Error('Cantidad inesperada de documentos; revisar duplicación.');
  if (action === 'final' && (!pending.exists || pending.get('ownerId') !== run.uid)) throw Error('Falta el reporte recuperado.');
  const result = {
    phase: action, result: 'APROBADO', projectId, api: run.api, uid: run.uid,
    petId: run.petId, reportId: run.reportId, pendingId: run.pendingId,
    pendingExistsOnServer: pending.exists, ownerPetCount: ownedPets.size, ownerReportCount: ownedReports.size,
    reportIds: ownedReports.docs.map(d => d.id), checkedAtUtc: new Date().toISOString()
  };
  writeFileSync(process.argv[4], JSON.stringify(result, null, 2));
  console.log(`API ${run.api}: servidor local verificado (${action}), ${ownedPets.size} mascota y ${ownedReports.size} reporte(s).`);
} else {
  throw Error('Acción admitida: seed, pending o final.');
}
