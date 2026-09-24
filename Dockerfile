FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy Java source files
COPY backend/src ./src

# Compile backend and tests
RUN mkdir out && \
    javac -d out src/*.java src/tests/UserTest.java

# Run the backend
EXPOSE 9000

CMD ["java", "-cp", "out", "Main"]