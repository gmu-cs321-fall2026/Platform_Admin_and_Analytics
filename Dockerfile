FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy Java backend
COPY backend/src ./src

# Copy frontend
COPY frontend/src ./frontend/src

# Compile backend and tests
RUN mkdir out && \
    javac -d out src/*.java src/tests/UserTest.java

EXPOSE 9000

CMD ["java", "-cp", "out", "Main"]