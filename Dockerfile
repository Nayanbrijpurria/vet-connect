
FROM gradle:8.14-jdk21 AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/install/ /app/install/

RUN find /app/install -type f -path "*/bin/*" ! -name "*.bat" -exec chmod +x {} \;

CMD ["sh", "-c", "APP=$(find /app/install -type f -path '*/bin/*' ! -name '*.bat' | head -n 1); exec \"$APP\" -host=0.0.0.0 -port=\"${PORT:-8080}\""]

