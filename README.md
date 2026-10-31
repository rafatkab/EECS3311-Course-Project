# EECS3311 Course Project

- [Frontend](frontend/README.md): Vaadin Flow application using Java 17, Spring Boot, and Maven.
- [Backend](backend/README.md): independent Spring Boot REST backend on port 8081.
- `documentation/`: course project reports and diagrams.

Start the frontend:

```powershell
cd frontend
.\mvnw.cmd spring-boot:run
```

Requires JDK 17+ with `JAVA_HOME` set. Open <http://localhost:8080> after startup.

Start the backend in a separate terminal from the repository root:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Verify it at <http://localhost:8081/actuator/health>.
