import { test, before, beforeEach, after } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { collection, doc, deleteDoc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where, serverTimestamp, writeBatch } from "firebase/firestore";

const projectId = process.env.FIREBASE_PROJECT_ID;
if (!projectId) throw new Error("Defina FIREBASE_PROJECT_ID con el proyecto real antes de probar.");
const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
if (!emulatorHost || !["127.0.0.1", "localhost", "[::1]"].includes(new URL(`http://${emulatorHost}`).hostname)) {
  throw new Error("Estas pruebas limpian datos: FIRESTORE_EMULATOR_HOST debe apuntar al emulador local.");
}
let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") }
  });
});
beforeEach(async () => {
  await env.clearFirestore(); // Solo la base local del emulador; cada prueba empieza aislada.
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, "users/alice"), { email: "alice@example.invalid", role: "citizen" });
    await setDoc(doc(db, "users/bob"), { email: "bob@example.invalid", role: "citizen" });
    await setDoc(doc(db, "users/staff"), { email: "staff@example.invalid", role: "staff" });
    for (const [uid, role, departmentId, active] of [
      ["staff", "admin", "zipaquira-bienestar-animal", true],
      ["vet", "vet", "zipaquira-bienestar-animal", true],
      ["vet2", "vet", "zipaquira-bienestar-animal", true],
      ["revoked", "vet", "zipaquira-bienestar-animal", false],
      ["outside", "admin", "other-department", true],
      ["outsidevet", "vet", "other-department", true]])
      await setDoc(doc(db, `access/${uid}`), {role, departmentId, active, displayName: "Cuenta ficticia"});
    await setDoc(doc(db, "reports/owned"), {
      ownerId: "alice", petId: "", petName: "Toby", type: "Pérdida", species: "Perro",
      territoryId: "comuna-1", description: "Perro ficticio", status: "Abierto",
      createdAt: new Date(), updatedAt: new Date()
    });
    await setDoc(doc(db, "pets/public"), {
      ownerId: "alice", name: "Luna", species: "Perro", breed: "Criolla", sex: "Hembra",
      age: "3 años", color: "Café", territoryId: "comuna-1", status: "Con responsable",
      photoKey: "", qrCode: "ZPQ-1234ABCD", createdAt: new Date(), updatedAt: new Date()
    });
  });
});
after(async () => { if (env) await env.cleanup(); });
const db = uid => uid ? env.authenticatedContext(uid, {email: `${uid}@example.invalid`}).firestore() : env.unauthenticatedContext().firestore();

const departmentId = 'zipaquira-bienestar-animal';
const caseData = () => ({reportId:'owned',ownerId:'alice',petId:'',petName:'Toby',species:'Perro',territoryId:'comuna-1',
  departmentId,vetId:'',status:'Revisado',outcome:'',lastRecordId:'',version:1,updatedBy:'staff',
  createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
async function review(uid='staff', overrides={}) {
  const f=db(uid), b=writeBatch(f), data={...caseData(),...overrides};
  b.set(doc(f,'cases/owned'),data);
  b.set(doc(f,'cases/owned/history/1'),{actorId:uid,status:data.status,version:1,vetId:data.vetId,createdAt:serverTimestamp()});
  return b.commit();
}
async function change(uid, version, status, extras={}, note=null) {
  const f=db(uid), b=writeBatch(f);
  const previous = await getDoc(doc(db('staff'),'cases/owned'));
  b.update(doc(f,'cases/owned'),{status,version,updatedBy:uid,updatedAt:serverTimestamp(),...extras});
  b.set(doc(f,`cases/owned/history/${version}`),{actorId:uid,status,version,vetId:extras.vetId ?? previous.data().vetId,createdAt:serverTimestamp()});
  if(note) b.set(doc(f,`cases/owned/clinicalRecords/${extras.lastRecordId}`),{
    authorId:uid,examination:'Valoración ficticia',care:'Atención ficticia',followUp:'Revisión ficticia',createdAt:serverTimestamp(),...note});
  if(status==='Cerrado') b.update(doc(f,'reports/owned'),{status,updatedAt:serverTimestamp()});
  return b.commit();
}
async function assigned() { await review(); await change('staff',2,'Asignado',{vetId:'vet'}); }

test('flujo completo: revisión, asignación, atención inmutable, corrección y cierre', async()=>{
  await assertSucceeds(review());
  await assertSucceeds(change('staff',2,'Asignado',{vetId:'vet'}));
  await assertSucceeds(change('vet',3,'Atendido',{lastRecordId:'note1'},{}));
  await assertSucceeds(change('vet',4,'Atendido',{lastRecordId:'note2'},{care:'Corrección de la entrada note1'}));
  await assertSucceeds(change('staff',5,'Cerrado',{outcome:'Caso ficticio atendido'}));
  const result=await assertSucceeds(getDoc(doc(db('alice'),'cases/owned')));
  if(result.data().outcome!=='Caso ficticio atendido') throw Error('Resultado no persistido');
  await assertFails(change('vet',6,'Atendido',{lastRecordId:'late'},{}));
  await assertFails(updateDoc(doc(db('vet'),'cases/owned/clinicalRecords/note1'),{care:'Sobrescribir'}));
  await assertFails(deleteDoc(doc(db('staff'),'cases/owned/history/1')));
});
test('caso sin atención puede cerrarse; no se cierra asignado sin atención', async()=>{
  await review(); await assertSucceeds(change('staff',2,'Cerrado',{outcome:'Sin intervención veterinaria necesaria'}));
});
test('aislamiento clínico: ni ciudadano, administrador, otro vet o anónimo', async()=>{
  await assigned(); await change('vet',3,'Atendido',{lastRecordId:'note1'},{});
  for(const uid of [null,'alice','bob','staff','vet2','outsidevet']) {
    await assertFails(getDoc(doc(db(uid),'cases/owned/clinicalRecords/note1')));
    await assertFails(getDocs(query(collection(db(uid),'cases/owned/clinicalRecords'),limit(20))));
  }
  await assertSucceeds(getDoc(doc(db('vet'),'cases/owned/clinicalRecords/note1')));
  await assertSucceeds(getDocs(query(collection(db('vet'),'cases/owned/clinicalRecords'),orderBy('createdAt','desc'),limit(20))));
  await assertFails(getDoc(doc(db('staff'),'users/alice')));
  await assertFails(getDoc(doc(db('vet'),'reports/owned')));
});
test('se rechazan roles autoasignados, rol legacy y acceso de otra dependencia', async()=>{
  for(const uid of ['alice','vet','staff']) {
    await assertFails(setDoc(doc(db(uid),`access/${uid}`),{role:'admin',departmentId,active:true}));
    await assertFails(updateDoc(doc(db(uid),'access/vet2'),{active:true}));
  }
  for(const uid of ['alice','vet','outside']) await assertFails(review(uid));
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'users/legacy'),{role:'staff'}));
  await assertFails(getDoc(doc(db('legacy'),'reports/owned')));
  await assertFails(getDoc(doc(db('outside'),'reports/owned')));
});
test('asignación exige vet activo de la misma dependencia y evita transiciones inválidas', async()=>{
  await review();
  for(const vetId of ['alice','staff','revoked','outsidevet','missing'])
    await assertFails(change('staff',2,'Asignado',{vetId}));
  await assertSucceeds(change('staff',2,'Asignado',{vetId:'vet'}));
  await assertFails(change('staff',3,'Cerrado',{outcome:'Cerrar antes de atender'}));
  await assertFails(change('vet2',3,'Atendido',{lastRecordId:'fake'},{}));
  await assertFails(change('staff',3,'Atendido',{lastRecordId:'fake'},{}));
  await assertFails(change('alice',3,'Cerrado',{outcome:'Falso'}));
});
test('revocación bloquea lecturas y escrituras; reasignación retira acceso anterior', async()=>{
  await assigned();
  await assertSucceeds(getDoc(doc(db('vet'),'cases/owned')));
  await change('staff',3,'Asignado',{vetId:'vet2'});
  await assertFails(getDoc(doc(db('vet'),'cases/owned')));
  await assertFails(change('vet',4,'Atendido',{lastRecordId:'old'},{}));
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'access/vet2'),{active:false}));
  await assertFails(getDoc(doc(db('vet2'),'cases/owned')));
  await assertFails(change('vet2',4,'Atendido',{lastRecordId:'revoked'},{}));
});
test('propiedad, integridad, historia y concurrencia del caso', async()=>{
  await assertFails(review('staff',{ownerId:'bob'}));
  await assertFails(review('staff',{petId:'public'}));
  await assertFails(review('staff',{extra:'dato'}));
  await assertFails(setDoc(doc(db('staff'),'cases/owned'),caseData()));
  await assigned();
  await assertFails(change('staff',2,'Asignado',{vetId:'vet2'}));
  await assertFails(change('vet',3,'Atendido',{lastRecordId:'missing'}));
  await assertFails(change('vet',3,'Atendido',{lastRecordId:'extra'},{contact:'dato privado extra'}));
  await assertFails(change('vet',3,'Atendido',{lastRecordId:'empty'},{care:''}));
  await assertFails(change('vet',3,'Atendido',{lastRecordId:'long'},{followUp:'x'.repeat(1001)}));
  await assertFails(getDoc(doc(db('bob'),'cases/owned')));
  await assertFails(getDoc(doc(db('outside'),'cases/owned')));
  await assertSucceeds(getDoc(doc(db('alice'),'cases/owned')));
});
test('consultas profesionales limitadas y filtradas por asignación/dependencia', async()=>{
  await assigned();
  await assertSucceeds(getDocs(query(collection(db('staff'),'cases'),where('departmentId','==',departmentId),orderBy('updatedAt','desc'),limit(20))));
  await assertSucceeds(getDocs(query(collection(db('vet'),'cases'),where('departmentId','==',departmentId),where('vetId','==','vet'),orderBy('updatedAt','desc'),limit(20))));
  await assertFails(getDocs(query(collection(db('vet'),'cases'),where('departmentId','==',departmentId),limit(20))));
  await assertFails(getDocs(query(collection(db('staff'),'cases'),where('departmentId','==',departmentId),limit(21))));
  await assertSucceeds(getDocs(query(collection(db('staff'),'access'),where('departmentId','==',departmentId),where('role','==','vet'),orderBy('__name__'),limit(20))));
  await assertFails(getDocs(query(collection(db('vet'),'access'),limit(20))));
  await assertFails(getDocs(query(collection(db('staff'),'access'),limit(20))));
});
test('un reporte no puede vincular una mascota ajena', async()=>{
  await assertFails(setDoc(doc(db('bob'),'reports/forged-pet'),{...validReport('bob'),petId:'public'}));
  await assertSucceeds(setDoc(doc(db('alice'),'reports/own-pet'),{...validReport(),petId:'public'}));
});
test('administrador municipal tampoco lee un caso de otra dependencia', async()=>{
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'cases/foreign'),{
    ...caseData(), reportId:'foreign', ownerId:'bob', departmentId:'other-department'
  }));
  await assertFails(getDoc(doc(db('staff'),'cases/foreign')));
  await assertFails(getDoc(doc(db('vet'),'cases/foreign')));
  await assertSucceeds(getDoc(doc(db('alice'),'cases/owned'))); // Todavía no existe; permite consultar seguimiento propio.
});

test("lectura pública de mascota, datos privados aislados", async () => {
  await assertSucceeds(getDoc(doc(db(), "pets/public")));
  await assertFails(getDoc(doc(db(), "users/alice")));
  await assertFails(getDoc(doc(db("bob"), "users/alice")));
  await assertSucceeds(getDoc(doc(db("alice"), "users/alice")));
});
test("el anónimo no crea reportes", async () => {
  await assertFails(setDoc(doc(db(), "reports/new"), {
    ownerId: "alice", petId: "", petName: "", type: "Pérdida", species: "Perro",
    territoryId: "comuna-1", description: "Ficticio", status: "Abierto",
    createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
});
test("ciudadano crea sus propios registros válidos", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "pets/created"), {
    ownerId: "alice", name: "Pelusa", species: "Gato", breed: "Criolla", sex: "Hembra",
    age: "1 año", color: "Blanco", territoryId: "comuna-2", status: "Con responsable",
    photoKey: "", qrCode: "ZPQ-ABCDE123",
    createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(db("alice"), "reports/created"), {
    ownerId: "alice", petId: "", petName: "Pelusa", type: "Pérdida", species: "Gato",
    territoryId: "comuna-2", description: "Reporte ficticio", status: "Abierto",
    createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
});
test("consultas acotadas requieren propiedad o rol funcionario", async () => {
  await assertSucceeds(getDocs(query(collection(db("alice"), "reports"),
    where("ownerId", "==", "alice"), orderBy("createdAt", "desc"), limit(30))));
  await assertFails(getDocs(query(collection(db("bob"), "reports"), orderBy("createdAt", "desc"), limit(30))));
  await assertSucceeds(getDocs(query(collection(db("staff"), "reports"), orderBy("createdAt", "desc"), limit(30))));
});
test("propiedad de reportes y cambio de estado", async () => {
  await assertFails(getDoc(doc(db("bob"), "reports/owned")));
  await assertSucceeds(getDoc(doc(db("alice"), "reports/owned")));
  await assertFails(updateDoc(doc(db("bob"), "reports/owned"), {status: "Resuelto", updatedAt: serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db("alice"), "reports/owned"), {status: "Resuelto", updatedAt: serverTimestamp()}));
});
test("otra cuenta no modifica mascota ajena ni falsifica propietario", async () => {
  await assertFails(updateDoc(doc(db("bob"), "pets/public"), {name: "Otro", updatedAt: serverTimestamp()}));
  await assertFails(setDoc(doc(db("bob"), "pets/forged"), {
    ownerId: "alice", name: "Ficticia", species: "Gato", breed: "Criolla", sex: "Hembra",
    age: "1 año", color: "Blanco", territoryId: "comuna-2", status: "Con responsable",
    photoKey: "", qrCode: "ZPQ-ABCDE123",
    createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
});
test("el cliente no se asigna rol privilegiado", async () => {
  await assertFails(updateDoc(doc(db("alice"), "users/alice"), {role: "staff"}));
  await assertFails(setDoc(doc(db("mallory"), "users/mallory"), {
    email: "mallory@example.invalid", role: "staff", createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
});
test("funcionario autorizado puede leer reporte ajeno", async () => {
  await assertSucceeds(getDoc(doc(db("staff"), "reports/owned")));
});

const validPet = (ownerId = "alice") => ({
  ownerId, name: "Prueba", species: "Perro", breed: "Criolla", sex: "Hembra",
  age: "2 años", color: "Café", territoryId: "comuna-1", status: "Con responsable",
  photoKey: "", qrCode: "ZPQ-TEST1234", createdAt: serverTimestamp(), updatedAt: serverTimestamp()
});
const validReport = (ownerId = "alice") => ({
  ownerId, petId: "", petName: "Prueba", type: "Pérdida", species: "Perro",
  territoryId: "comuna-1", description: "Demostración ficticia", status: "Abierto",
  createdAt: serverTimestamp(), updatedAt: serverTimestamp()
});

test("el anónimo no crea mascotas válidas", async () => {
  await assertFails(setDoc(doc(db(), "pets/anonymous"), validPet()));
});

test("perfil propio válido; correo ajeno y campos extra rechazados", async () => {
  const profile = {email: "carol@example.invalid", role: "citizen", createdAt: serverTimestamp(), updatedAt: serverTimestamp()};
  await assertFails(setDoc(doc(db("carol"), "users/carol"), {...profile, email: "other@example.invalid"}));
  await assertFails(setDoc(doc(db("carol"), "users/carol"), {...profile, password: "no-se-almacena"}));
  await assertSucceeds(setDoc(doc(db("carol"), "users/carol"), profile));
  await assertFails(updateDoc(doc(db("carol"), "users/carol"), {email: "other@example.invalid"}));
});

test("ni dueño ni funcionario pueden transferir propiedad", async () => {
  for (const uid of ["alice", "staff"]) {
    await assertFails(updateDoc(doc(db(uid), "pets/public"), {ownerId: "bob", updatedAt: serverTimestamp()}));
    await assertFails(updateDoc(doc(db(uid), "reports/owned"), {ownerId: "bob", updatedAt: serverTimestamp()}));
  }
});

test("no se publican contactos, contraseñas o imágenes en fichas", async () => {
  for (const extra of [{email: "alice@example.invalid"}, {phone: "000000"}, {password: "ficticia"}, {imageBase64: "AAAA"}]) {
    await assertFails(setDoc(doc(db("alice"), "pets/extra"), {...validPet(), ...extra}));
    await assertFails(updateDoc(doc(db("alice"), "pets/public"), {...extra, updatedAt: serverTimestamp()}));
  }
});

test("tipos, límites y enumeraciones validados al crear", async () => {
  for (const changes of [{name: ""}, {name: "x".repeat(81)}, {age: 5}, {species: "Otro"}, {territoryId: "comuna-99"}, {photoKey: "luna"}, {status: "En custodia"}]) {
    await assertFails(setDoc(doc(db("alice"), "pets/invalid"), {...validPet(), ...changes}));
  }
  for (const changes of [{description: ""}, {description: "x".repeat(2001)}, {species: "Otro"}, {status: "Resuelto"}, {type: "Otro"}, {ownerId: "bob"}, {extra: true}]) {
    await assertFails(setDoc(doc(db("alice"), "reports/invalid"), {...validReport(), ...changes}));
  }
});

test("fechas de creación y actualización requieren hora del servidor", async () => {
  const old = new Date("2020-01-01T00:00:00Z");
  for (const collectionName of ["pets", "reports"]) {
    const valid = collectionName === "pets" ? validPet() : validReport();
    await assertFails(setDoc(doc(db("alice"), `${collectionName}/old`), {...valid, createdAt: old}));
    await assertFails(setDoc(doc(db("alice"), `${collectionName}/old`), {...valid, updatedAt: old}));
  }
  await assertFails(updateDoc(doc(db("alice"), "pets/public"), {name: "Prueba", updatedAt: old}));
  await assertFails(updateDoc(doc(db("alice"), "reports/owned"), {status: "Resuelto", updatedAt: old}));
  await assertFails(updateDoc(doc(db("alice"), "pets/public"), {createdAt: old, updatedAt: serverTimestamp()}));
});

test("funcionario modifica datos permitidos pero no crea otros privilegios", async () => {
  await assertSucceeds(updateDoc(doc(db("staff"), "pets/public"), {status: "En custodia", updatedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(db("staff"), "reports/owned"), {status: "Cerrado", updatedAt: serverTimestamp()})); // Cierre exige caso e historial atómicos.
  await assertFails(updateDoc(doc(db("staff"), "users/bob"), {role: "staff"}));
  await assertFails(updateDoc(doc(db("staff"), "reports/owned"), {description: "Cambio", updatedAt: serverTimestamp()}));
});

test("ciudadano no cierra reportes ni altera sus campos inmutables", async () => {
  await assertFails(updateDoc(doc(db("alice"), "reports/owned"), {status: "Cerrado", updatedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(db("alice"), "reports/owned"), {description: "Cambio", updatedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(db("alice"), "pets/public"), {qrCode: "ZPQ-OTHER123", updatedAt: serverTimestamp()}));
});

test("consultas sin límite o superiores a lo autorizado fallan", async () => {
  await assertFails(getDocs(collection(db(), "pets")));
  await assertFails(getDocs(query(collection(db(), "pets"), limit(1001))));
  await assertFails(getDocs(query(collection(db("alice"), "reports"), where("ownerId", "==", "alice"))));
  await assertFails(getDocs(query(collection(db("alice"), "reports"), where("ownerId", "==", "alice"), limit(1001))));
  await assertFails(getDocs(query(collection(db("staff"), "users"), limit(31))));
  await assertFails(getDocs(query(collection(db("alice"), "users"), limit(1))));
  await assertFails(getDocs(query(collection(db("staff"), "users"), limit(30))));
});

test("borrados y rutas administrativas denegados por defecto", async () => {
  for (const uid of ["alice", "staff"]) {
    for (const path of ["pets/public", "reports/owned", "users/alice"]) {
      await assertFails(deleteDoc(doc(db(uid), path)));
    }
    await assertFails(setDoc(doc(db(uid), "territories/new"), {label: "Ficticia"}));
    await assertFails(setDoc(doc(db(uid), "private/anything"), {value: true}));
    await assertFails(getDoc(doc(db(uid), "private/anything")));
  }
});
