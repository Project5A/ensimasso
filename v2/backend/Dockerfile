# syntax=docker/dockerfile:1.7
#
# Corrige les constats OPS-03 de l'audit de la v1 : cette image-là copiait un
# jar déjà construit (donc build manuel obligatoire), tournait en root, embarquait
# un JDK complet, et avait son HEALTHCHECK en commentaire.

# ---------- étape de construction ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Couche de dépendances séparée : le cache survit aux modifications de code.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/*.jar /build/app.jar

# ---------- étape d'exécution ----------
# JRE, pas JDK : moins de surface d'attaque et une image bien plus petite.
FROM eclipse-temurin:21-jre-alpine

# `apk upgrade` avant tout le reste, et ce n'est pas une précaution de style.
# Les images de base sont reconstruites moins souvent que les correctifs
# Alpine ne sortent : au moment du scan, celle-ci traînait cinq failles HIGH
# corrigées en amont (openssl 3.5.7-r0, libexpat 2.8.3-r0) simplement parce
# qu'elle datait de quelques semaines. Aucune n'était dans notre code — le jar
# lui-même est scanné à zéro — et aucune ne pouvait être corrigée par une
# dépendance Maven.
#
# Le prix est assumé : deux constructions du même Dockerfile à deux dates ne
# donnent plus exactement la même image. C'est le bon sens du compromis pour
# une couche système, et les manifestes déploient de toute façon par empreinte,
# pas par étiquette.
RUN apk upgrade --no-cache \
 && addgroup -S app && adduser -S -G app app \
 && apk add --no-cache curl

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar

# Jamais root.
USER app

EXPOSE 8080

# Réellement actif, contrairement à la v1 où il était commenté.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# MaxRAMPercentage : la JVM respecte la limite mémoire du conteneur au lieu de
# déduire la taille du tas de la machine hôte.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
