# syntax=docker/dockerfile:1.7
#
# Le portail public est un ensemble de fichiers statiques. Il n'a besoin ni de
# Node, ni de rendu serveur : un nginx de 40 Mo sert le même contenu qu'un
# processus Node de 200 Mo, et ne peut pas exécuter de code si on lui dépose
# quelque chose d'inattendu.

# ---------- construction ----------
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Le typage fait partie de la construction : une route qui n'existe pas doit
# casser ici, pas produire un 405 en production comme dans la v1.
RUN npm run typecheck && npm run build

# ---------- exécution ----------
# 1.30 et non 1.27 : cette étiquette-là était restée sur Alpine 3.21, et le
# scan y relevait 36 failles corrigeables — dont la seule CRITICAL des deux
# images, un dépassement de tampon OpenSSL sur les grands certificats X.509.
# Toutes venaient de la couche système : l'image finale ne contient que des
# fichiers statiques, aucun node_modules, donc rien à corriger côté npm.
FROM nginx:1.30-alpine

# Et un `apk upgrade` par-dessus, pour la même raison que dans l'image du
# cœur : une étiquette de base, même récente, est toujours en retard sur les
# correctifs publiés depuis sa construction.
# Trois choses, et chacune est VÉRIFIÉE après coup.
#
# Un `sed -i` qui ne trouve pas son motif ne dit rien : il sort à zéro, la
# construction réussit, et le fichier est inchangé. C'est exactement ce qui
# s'était produit ici — le motif visait `/var/run/nginx.pid` alors que cette
# image écrit `pid /run/nginx.pid;`. La substitution ne faisait rien, nginx
# tentait d'écrire son PID dans /run, que l'utilisateur « nginx » ne peut pas
# écrire, et mourait au démarrage :
#
#     [emerg] open() "/run/nginx.pid" failed (13: Permission denied)
#
# Une modification d'image de base non vérifiée est une hypothèse sur une image
# qu'on ne contrôle pas. On la vérifie donc, et la construction échoue si le
# résultat n'est pas celui qu'on croit.
#
# Le `chown` sur conf.d n'est pas décoratif non plus : le point d'entrée doit
# pouvoir y écrire la directive `resolver`, qui n'est connue qu'au démarrage.
RUN apk upgrade --no-cache \
 && rm -rf /usr/share/nginx/html/* \
 && sed -i -E 's#^\s*user\s+nginx\s*;##' /etc/nginx/nginx.conf \
 && sed -i -E 's#^(\s*)pid\s+\S+;#\1pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
 && chown -R nginx:nginx /etc/nginx/conf.d \
 && grep -q 'pid /tmp/nginx.pid;' /etc/nginx/nginx.conf \
 && ! grep -qE '^\s*user\s+nginx\s*;' /etc/nginx/nginx.conf \
 && ! grep -qE 'pid\s+/(var/)?run/' /etc/nginx/nginx.conf

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Lancé par le point d'entrée officiel de l'image nginx, avant le démarrage.
COPY --chown=nginx:nginx resolveur.sh /docker-entrypoint.d/05-resolveur.sh

# Jamais root, et un système de fichiers racine que l'on peut monter en
# lecture seule : nginx n'écrit que dans /tmp.
USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/ || exit 1
