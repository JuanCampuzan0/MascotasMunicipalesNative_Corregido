import { getApps, initializeApp, applicationDefault } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

const projectId = process.env.FIREBASE_PROJECT_ID;
const apply = process.argv.includes("--apply");
if (!projectId) throw new Error("Defina FIREBASE_PROJECT_ID con el proyecto real.");
if (!apply) {
  console.log("Vista previa: crearía solo documentos ausentes demo-luna, demo-michi, demo-rocco, demo-nina y cuatro comunas. Ejecute con --apply para escribir.");
  process.exit(0);
}
if (!getApps().length) initializeApp({credential: applicationDefault(), projectId});
const db = getFirestore();
const now = Timestamp.now();
const territories = [
  ["comuna-1", "Comuna 1 · Centro Histórico"],
  ["comuna-2", "Comuna 2 · Nororiental"],
  ["comuna-3", "Comuna 3 · Suroriental"],
  ["comuna-4", "Comuna 4 · Occidental"]
];
const pets = [
  ["demo-luna", "Luna", "Perro", "Criolla", "Hembra", "3 años", "Café", "comuna-1", "luna"],
  ["demo-michi", "Michi", "Gato", "Doméstico pelo corto", "Macho", "2 años", "Gris atigrado", "comuna-2", "michi"],
  ["demo-rocco", "Rocco", "Perro", "Mestizo", "Macho", "5 años", "Negro y café", "comuna-3", "rocco"],
  ["demo-nina", "Nina", "Perro", "Criolla pequeña", "Hembra", "8 meses", "Crema", "comuna-4", "nina"]
];
for (const [id, label] of territories) {
  const ref = db.collection("territories").doc(id);
  if (!(await ref.get()).exists) { await ref.create({label, createdAt: now}); console.log("Creado", ref.path); }
}
for (const [id, name, species, breed, sex, age, color, territoryId, photoKey] of pets) {
  const ref = db.collection("pets").doc(id);
  if (!(await ref.get()).exists) {
    await ref.create({
      ownerId: "demo", name, species, breed, sex, age, color, territoryId,
      status: "Con responsable", photoKey, qrCode: "ZPQ-" + id.replace("demo-", "").toUpperCase().padEnd(8, "0"),
      createdAt: now, updatedAt: now
    });
    console.log("Creado", ref.path);
  }
}
