#!/usr/bin/env python3
"""Cohérence des manifestes Kubernetes.

kubeconform valide chaque ressource contre son schéma ; il ne sait pas qu'une
clé de ConfigMap référencée n'existe nulle part. C'est pourtant l'erreur la
plus facile à commettre — elle a été commise en écrivant ces fichiers — et elle
ne se manifeste qu'au démarrage du pod, en production.

Ce script vérifie les renvois entre ressources, et surtout : qu'AUCUN Secret
n'est défini dans le dépôt. C'est le constat le plus grave de l'audit de la v1
(mot de passe Azure SQL, clé de compte de stockage et clé secrète Stripe
committés depuis février 2025), et la seule façon de garantir qu'il ne revient
pas est de le vérifier à chaque fois plutôt que d'y penser à chaque fois.
"""
import pathlib
import sys

import yaml

RACINE = pathlib.Path(__file__).parent
problemes: list[str] = []
avertissements: list[str] = []


def documents():
    for fichier in sorted(RACINE.rglob("*.yaml")):
        for doc in yaml.safe_load_all(fichier.read_text()):
            if isinstance(doc, dict) and doc.get("kind"):
                yield fichier.relative_to(RACINE), doc


ressources = list(documents())

# --- 1. aucun Secret dans le dépôt ---------------------------------------
for fichier, doc in ressources:
    if doc["kind"] == "Secret":
        problemes.append(
            f"{fichier} définit un Secret « {doc['metadata']['name']} ». "
            "Les secrets ne sont JAMAIS versionnés : c'est le constat le plus "
            "grave de l'audit de la v1."
        )

# --- 2. les ConfigMap référencées existent, clés comprises ----------------
configmaps = {
    doc["metadata"]["name"]: set(doc.get("data", {}))
    for _, doc in ressources
    if doc["kind"] == "ConfigMap"
}
secrets_attendus: set[str] = set()


def parcourir(noeud, fichier, chemin="spec"):
    if isinstance(noeud, dict):
        for cle, valeur in noeud.items():
            if cle == "configMapKeyRef":
                nom, clef = valeur.get("name"), valeur.get("key")
                if nom not in configmaps:
                    problemes.append(f"{fichier} renvoie à la ConfigMap inconnue « {nom} »")
                elif clef not in configmaps[nom]:
                    problemes.append(
                        f"{fichier} renvoie à « {nom}.{clef} », qui n'existe pas "
                        f"(clés disponibles : {', '.join(sorted(configmaps[nom]))})"
                    )
            elif cle == "configMapRef":
                if valeur.get("name") not in configmaps:
                    problemes.append(
                        f"{fichier} renvoie à la ConfigMap inconnue « {valeur.get('name')} »")
            elif cle in ("secretRef", "secretKeyRef"):
                secrets_attendus.add(valeur.get("name"))
            parcourir(valeur, fichier, f"{chemin}.{cle}")
    elif isinstance(noeud, list):
        for element in noeud:
            parcourir(element, fichier, chemin)


for fichier, doc in ressources:
    parcourir(doc, fichier)

# --- 3. tout conteneur a des limites et une sonde ------------------------
def conteneurs(doc):
    gabarit = doc.get("spec", {})
    for chemin in (("template",), ("jobTemplate", "spec", "template")):
        noeud = gabarit
        for morceau in chemin:
            noeud = (noeud or {}).get(morceau, {})
        if noeud:
            for c in noeud.get("spec", {}).get("containers", []):
                yield c


for fichier, doc in ressources:
    if doc["kind"] not in ("Deployment", "CronJob", "StatefulSet", "DaemonSet"):
        continue
    for c in conteneurs(doc):
        if not c.get("resources", {}).get("limits"):
            problemes.append(
                f"{fichier} : le conteneur « {c['name']} » n'a pas de limite de "
                "ressources — un pod sans plafond peut évincer tous les autres")
        if doc["kind"] == "Deployment" and not c.get("readinessProbe"):
            problemes.append(
                f"{fichier} : le conteneur « {c['name']} » n'a pas de sonde de "
                "disponibilité — le service recevra du trafic avant d'être prêt")
        securite = c.get("securityContext", {})
        if securite.get("allowPrivilegeEscalation") is not False:
            problemes.append(f"{fichier} : « {c['name'] } » autorise l'élévation de privilèges")
        if "latest" in str(c.get("image", "")):
            avertissements.append(
                f"{fichier} : « {c['name']} » utilise une étiquette « latest ». "
                "Acceptable si ArgoCD épingle l'empreinte, à proscrire sinon.")

# --- rapport -------------------------------------------------------------
print(f"{len(ressources)} ressources lues, {len(configmaps)} ConfigMap")
print("Secrets attendus, à créer HORS du dépôt :")
for nom in sorted(s for s in secrets_attendus if s):
    print(f"  - {nom}")

for a in avertissements:
    print(f"\033[33m  avertissement : {a}\033[0m")

if problemes:
    print("\n\033[31mProblèmes :\033[0m")
    for p in problemes:
        print(f"  ✗ {p}")
    sys.exit(1)

print("\n\033[32mManifestes cohérents.\033[0m")
