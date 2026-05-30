FROM eclipse-temurin:26-jdk-noble AS build
WORKDIR /build
COPY . .
RUN ./mvnw package -Pprod -DskipTests

FROM eclipse-temurin:26-jdk-noble
WORKDIR /app
COPY --from=build /build/deepresearch-app/target/deepresearch-app-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /build/secrets-common.properties secrets-common.properties
COPY --from=build /build/secrets-prod.properties secrets-prod.properties
EXPOSE 8081
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-Xmx1g", "-Xms512m", \
  "-jar", "app.jar"]
