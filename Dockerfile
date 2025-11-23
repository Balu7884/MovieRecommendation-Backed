# =========
# 1) BUILD STAGE
# =========
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies first (cache layer)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy source and build
COPY src ./src
RUN mvn -B clean package -DskipTests

# =========
# 2) RUNTIME STAGE
# =========
FROM eclipse-temurin:17-jre

WORKDIR /app

# Expose app port (same as server.port)
EXPOSE 8080

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Environment variables (you can override at runtime)
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/Balu \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=1234 \
    GEMINI_API_KEY=change_me_in_runtime

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
