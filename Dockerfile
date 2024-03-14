FROM openjdk:13 AS build

WORKDIR /ph-ee-connector-ams-pesacore

COPY . .

RUN ./gradlew bootJar

FROM openjdk:13

WORKDIR /app

COPY --from=build /ph-ee-connector-ams-pesacore/build/libs/ph-ee-connector-ams-pesacore*.jar ph-ee-connector-ams-pesacore.jar

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/ph-ee-connector-ams-pesacore.jar"]