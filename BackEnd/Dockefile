# Utiliser une image de base avec Java 17
FROM openjdk:17-jdk-slim

# Argument pour spécifier le nom du JAR à copier
ARG JAR_FILE=target/BackEnd-0.0.1-SNAPSHOT.jar

# Copier le JAR dans l'image
COPY ${JAR_FILE} app.jar

# Exposer le port sur lequel votre application écoute (ici 8080)
EXPOSE 8080

# Lancer l’application
ENTRYPOINT ["java", "-jar", "app.jar"]
