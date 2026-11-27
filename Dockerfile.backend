FROM maven:3.9-eclipse-temurin-21

WORKDIR /app
COPY pom.xml .

# reuses downloaded dependencies
RUN mvn dependency:go-offline

COPY . .

EXPOSE 8080

# builds once (when the image is built) and then runs the jar file
RUN mvn package -DskipTests
CMD ["java", "-jar", "target/dishes-be-crazy-0.0.1-SNAPSHOT.jar"]