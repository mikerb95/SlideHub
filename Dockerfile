# Root Dockerfile fallback for Render Docker deploys
# Builds and runs the monolith module located in slidehub-service/
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

RUN ./mvnw clean package -pl slidehub-service -am -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/slidehub-service/target/slidehub-service-*.jar app.jar

LABEL service="slidehub-service" version="1.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms16m", "-Xmx96m", \
  "-Xss256k", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=256m", \
  "-XX:ReservedCodeCacheSize=32m", \
  "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", \
  "-XX:SoftRefLRUPolicyMSPerMB=0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-Dspring.main.lazy-initialization=true", \
  "-jar", "app.jar"]