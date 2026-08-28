FROM eclipse-temurin:17-jre

WORKDIR /app
COPY delta-admin/target/delta-admin.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
