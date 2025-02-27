# Use a base image with Java 17
FROM openjdk:17-jdk-slim

# Argument to specify the name of the JAR to copy
ARG JAR_FILE=target/BackEnd-0.0.1-SNAPSHOT.jar

# Set the working directory
WORKDIR /app

# Copy the JAR into the image
COPY ${JAR_FILE} app.jar

# Expose the port on which your application listens (here 8080)
EXPOSE 8080

# Set environment variables if needed
# ENV SOME_ENV_VAR=some_value

# Add a health check (optional)
# HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
#   CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
