# Vaadin Flow frontend

Java 17, Vaadin 24.10.7 (the last free Vaadin 24 release), Spring Boot, and Maven. The UI is implemented in Java under
`src/main/java/com/eecs3311/views`.

From this directory, with JDK 17+ installed and `JAVA_HOME` set:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080. The first run downloads Maven, dependencies, and frontend tooling.
On macOS/Linux, use `sh mvnw spring-boot:run`.

Build a production JAR:

```powershell
.\mvnw.cmd clean package -Pproduction
java -jar target/frontend-0.0.1-SNAPSHOT.jar
```
