# ==========================================
# STAGE 1: Build Environment
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy build descriptor and resolve dependencies to populate cache
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source tree and compile clean package
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# STAGE 2: Secure Production Runtime
# ==========================================
FROM eclipse-temurin:17-jre-alpine AS runner

WORKDIR /app

# Create custom system user to prevent container escape root exploits
RUN addgroup -S clucknet && adduser -S clucknet -G clucknet
USER clucknet

# Copy the built jar from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Standard HTTP Web Service Port
EXPOSE 8080

# Run JVM with optimized container-aware memory tuning
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "app.jar"]
