import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';

// Este comando nunca puede apuntar a Firebase remoto.
for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST']) {
  const host = process.env[key];
  if (!host || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(`http://${host}`).hostname))
    throw Error(`${key} debe apuntar al emulador local.`);
}
if (!process.env.FIREBASE_PROJECT_ID) throw Error('Defina FIREBASE_PROJECT_ID.');
if (!process.argv.includes('--apply')) {
  console.log('Vista previa: tres cuentas ficticias, dos permisos profesionales y un reporte. Solo crea documentos ausentes. Use --apply.');
  process.exit(0);
}
const password = process.env.DEMO_PASSWORD;
if (!password || password.length < 6) throw Error('Defina DEMO_PASSWORD (solo para cuentas locales ficticias).');
initializeApp({ projectId: process.env.FIREBASE_PROJECT_ID });
const auth = getAuth(), db = getFirestore();
async function createMissing(path, data) {
  try { await db.doc(path).create(data); }
  catch(e) { if(e.code !== 6 && e.code !== 'already-exists') throw e; }
}
for (const role of ['citizen', 'admin', 'vet']) {
  const uid = `demo-${role}`, email = `${role}@example.test`;
  try { await auth.getUser(uid); }
  catch(e) {
    if (e.code !== 'auth/user-not-found') throw e;
    await auth.createUser({uid, email, password});
  }
  await createMissing(`users/${uid}`, {email, role:'citizen', createdAt:FieldValue.serverTimestamp(), updatedAt:FieldValue.serverTimestamp()});
  if(role !== 'citizen') await createMissing(`access/${uid}`, {
    role, departmentId:'zipaquira-bienestar-animal', active:true,
    displayName:`${role === 'admin' ? 'Administrador' : 'Veterinario'} ficticio`,
    approvedBy:'local-emulator-seed', approvedAt:FieldValue.serverTimestamp()
  });
}
await createMissing('reports/demo-workflow', {ownerId:'demo-citizen', petId:'', petName:'Pelusa ficticia',
  type:'Encontrado', species:'Gato', territoryId:'comuna-1', description:'Reporte ficticio para probar el flujo municipal.',
  status:'Abierto', createdAt:FieldValue.serverTimestamp(), updatedAt:FieldValue.serverTimestamp()});
console.log('Datos locales disponibles. No se alteraron documentos existentes ni se reinició el estado del caso.');
