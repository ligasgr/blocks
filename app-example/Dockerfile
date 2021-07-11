FROM openjdk:16.0-jdk-oraclelinux7 

EXPOSE 8080

RUN mkdir /app

COPY build/libs/app-example-0.1.0-all.jar /app/app-example-all.jar

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/app-example-all.jar"]
