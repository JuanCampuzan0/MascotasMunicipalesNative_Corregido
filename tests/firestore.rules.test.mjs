import { test, before, after } from "node:test";
import { readFileSync } from "node:fs";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where, serverTimestamp } from "firebase/firestore";

const projectId = process.env.FIREBASE_PROJECT_ID;
if (!projectId) throw new Error("Defina FIREBASE_PROJECT_ID con el proyecto real antes de probar.");
let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") }
  });
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, "users/alice"), { email: "alice@example.invalid", role: "citizen" });
    await setDoc(doc(db, "users/bob"), { email: "bob@example.invalid", role: "citizen" });
    await setDoc(doc(db, "users/staff"), { email: "staff@example.invalid", role: "staff" });
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

test("lectura pública de mascota, datos privados aislados", async () => {
  await assertSucceeds(getDoc(doc(db(), "pets/public")));
  await assertFails(getDoc(doc(db(), "users/alice")));
  await assertFails(getDoc(doc(db("bob"), "users/alice")));
  await assertSucceeds(getDoc(doc(db("alice"), "users/alice")));
});
test("el anónimo no crea mascotas ni reportes", async () => {
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
  await assertFails(setDoc(doc(db("alice"), "users/fake"), {
    email: "alice@example.invalid", role: "staff", createdAt: serverTimestamp(), updatedAt: serverTimestamp()
  }));
});
test("funcionario autorizado puede leer reporte ajeno", async () => {
  await assertSucceeds(getDoc(doc(db("staff"), "reports/owned")));
});
