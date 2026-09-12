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
FROM nginx:1.27-alpine

RUN rm -rf /usr/share/nginx/html/* \
 && sed -i 's/user  nginx;//' /etc/nginx/nginx.conf \
 && sed -i 's#/var/run/nginx.pid#/tmp/nginx.pid#' /etc/nginx/nginx.conf

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Jamais root, et un système de fichiers racine que l'on peut monter en
# lecture seule : nginx n'écrit que dans /tmp.
USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/ || exit 1
