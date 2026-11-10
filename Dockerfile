FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# The backend uses plain JDBC, so the driver is needed at runtime.
ADD https://jdbc.postgresql.org/download/postgresql-42.7.14.jar /app/lib/postgresql.jar

COPY backend/src ./src
COPY frontend/src ./frontend/src

RUN mkdir out && \
    javac -encoding UTF-8 -d out src/*.java src/database/*.java src/tests/*.java

EXPOSE 9000

CMD ["java", "-cp", "/app/out:/app/lib/postgresql.jar", "Main"]
