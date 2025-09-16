# Stage 1: Build JAR (nếu bạn muốn build trong Docker)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run lightweight JRE
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# copy JAR từ stage build
COPY --from=build /app/target/*.jar app.jar

# cấu hình biến môi trường (tùy chọn)
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
