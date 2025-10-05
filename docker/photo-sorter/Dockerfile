# Multi-stage Dockerfile for photo-sorter
# Build stage: uses Maven to compile and package the photo-sorter module
FROM maven:3.9.6-eclipse-temurin-21 as builder

# Set workdir and copy repository files. NOTE: Build context must be the repository root when building this Dockerfile.
WORKDIR /workspace

# Copy everything (small optimization: you could copy only necessary files, but copying repo is simpler)
COPY . .

# Run maven package for the photo-sorter module (skip tests for faster builds)
RUN mvn -f pom.xml -pl photo-sorter -am -DskipTests package

# Runtime stage: use a slim JRE image
FROM eclipse-temurin:21-jre

# Expose application port
EXPOSE 8080

# Copy the built jar from the builder stage. Adjust the JAR name if you change the module version.
ARG JAR_FILE=/workspace/photo-sorter/target/photo-sorter-0.0.1-SNAPSHOT.jar
COPY --from=builder ${JAR_FILE} /app/photo-sorter.jar


# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/photo-sorter.jar"]

# Example to build (from repository root):
# docker build -f docker/photo-sorter/Dockerfile -t photo-sorter:local .
