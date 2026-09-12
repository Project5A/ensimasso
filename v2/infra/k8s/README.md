# Déploiement

> **Ce qui a été vérifié et ce qui ne l'a pas été.** Ces manifestes sont
> validés contre les schémas Kubernetes (`kubeconform --strict`) et contrôlés
> pour leurs renvois internes (`verifier-manifests.py`). Ils n'ont **jamais été
> appliqués à un cluster** : aucun n'était disponible au moment de les écrire.
> Traitez-les comme une proposition sérieuse, pas comme une configuration
> éprouvée — le premier `kubectl apply` révélera des choses.

## Ce qu'il y a ici

| Fichier | Contenu |
|---|---|
| `base/00-namespace.yaml` | Namespace, avec les règles de sécurité des pods en `restricted` |
| `base/10-configmap.yaml` | Configuration non secrète |
| `base/20-core.yaml` | Le cœur : écriture, tableau de bord, webhook |
| `base/30-delivery.yaml` | La lecture publique, son HPA et son budget d'interruption |
| `base/40-portail.yaml` | Le portail statique et l'Ingress |
| `base/50-reseau.yaml` | Politiques réseau, en refus par défaut |
| `base/60-sauvegarde.yaml` | Sauvegarde quotidienne et exercice de restauration hebdomadaire |
| `argocd/application.yaml` | La déclaration ArgoCD |

## Les décisions qui ne vont pas de soi

**Une seule image pour deux déploiements.** `core` et `delivery` partagent le
même conteneur ; seuls changent le profil Spring, le rôle PostgreSQL et le jeu
de secrets. C'est ce qui rend la séparation honnête : il n'y a pas deux bases de
code à maintenir en parallèle, donc pas de dérive possible entre ce que le
public voit et ce que le bureau publie.

**`core` ne passe pas à l'échelle, et c'est voulu.** Son trafic est celui d'une
dizaine de bureaux qui éditent des pages. Une réplique unique, avec une
stratégie `Recreate`, rend le raisonnement sur les migrations Flyway beaucoup
plus simple : deux versions du schéma en vol pendant une bascule est la façon la
plus sûre de perdre des données un mardi soir.

**Le composant le plus exposé détient le moins.** `delivery` n'a ni clé Stripe,
ni administration Keycloak, ni droit d'écriture en base — et sa politique réseau
lui interdit même le chemin pour les joindre. Si le rendu public tombait, il n'y
aurait rien à prendre.

**Refus par défaut sur le réseau.** La v1 avait une liste de motifs d'URL en
« autoriser », et 27 routes sur 32 se sont retrouvées publiques. Une politique
qui énumère ce qui est interdit finit au même endroit ; ici, ce qui n'est pas
écrit ne passe pas.

## Ce qui n'est PAS ici, et pourquoi

**PostgreSQL, MinIO, Valkey et Keycloak.** Faire tourner une base de données sur
Kubernetes demande un opérateur, une compréhension des volumes persistants et
une procédure de mise à jour que personne ne relira dans trois ans. Pour une
association étudiante, un PostgreSQL installé par le gestionnaire de paquets sur
une machine sauvegardée est plus fiable, plus facile à restaurer, et surtout
plus facile à expliquer au bureau suivant. Les manifestes pointent vers ces
services par leur nom ; où ils tournent est une décision séparée.

**Les Secrets.** Aucun `secretGenerator`, aucun fichier chiffré dans le dépôt.
C'est la correction du constat le plus grave de l'audit de la v1 — mot de passe
Azure SQL, clé de compte de stockage et clé secrète Stripe committés en clair
depuis février 2025. `verifier-manifests.py` échoue si un Secret réapparaît ici.

Quatre Secrets sont attendus :

```bash
kubectl -n ensimasso create secret generic ensimasso-secrets \
  --from-literal=DB_USER=ensimasso_app \
  --from-literal=DB_PASSWORD=… \
  --from-literal=STRIPE_CLE_SECRETE=… \
  --from-literal=STRIPE_SECRET_WEBHOOK=… \
  --from-literal=MINIO_USER=… --from-literal=MINIO_PASSWORD=…

# Le plus pauvre des quatre : lecture seule, aucun secret de paiement.
kubectl -n ensimasso create secret generic ensimasso-secrets-lecture \
  --from-literal=DB_RO_USER=ensimasso_ro \
  --from-literal=DB_RO_PASSWORD=…

kubectl -n ensimasso create secret generic ensimasso-secrets-sauvegarde \
  --from-literal=PGUSER=… --from-literal=PGPASSWORD=…

# La clé PRIVÉE age n'apparaît QUE dans celui-ci, utilisé par le seul exercice
# de restauration. Si elle est perdue, les sauvegardes sont illisibles.
kubectl -n ensimasso create secret generic ensimasso-secrets-restauration \
  --from-file=AGE_IDENTITE=identite.txt \
  --from-literal=PGUSER=… --from-literal=PGPASSWORD=…
```

## Vérifier avant d'appliquer

```bash
kubeconform -strict -summary base/*.yaml
python3 verifier-manifests.py
```

Le second contrôle ce que le premier ne peut pas voir : qu'une clé de ConfigMap
référencée existe réellement, qu'aucun conteneur ne tourne sans limite de
ressources ni sonde de disponibilité, et qu'aucun Secret n'a été versionné.
L'erreur de renvoi a été commise en écrivant ces fichiers — elle ne se serait
manifestée qu'au démarrage du pod, en production.
