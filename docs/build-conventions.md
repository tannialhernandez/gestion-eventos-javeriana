# Convenciones de Build y Calidad

## Plataforma de Gestión de Eventos Académicos — Pontificia Universidad Javeriana

---

## 1. Estructura del parent pom.xml

El `pom.xml` raíz actúa como BOM (Bill of Materials) del proyecto. Define:

| Sección | Qué contiene |
|---|---|
| `<properties>` | Todas las versiones pinneadas. **Nunca** declares versiones en módulos hijos. |
| `<dependencyManagement>` | Coordenadas + versiones de TODAS las dependencias. Los módulos declaran solo `groupId:artifactId`. |
| `<build><pluginManagement>` | Configuración de plugins compartida. Los módulos activan el plugin con `<plugin>` vacío. |

### Dependencias gestionadas actualmente

| Dependencia | Versión | Justificación |
|---|---|---|
| Spring Boot (parent) | 3.2.4 | BOM principal — gestiona todas las deps de Spring |
| Spring Cloud | 2023.0.1 | OpenFeign, Config Server, Gateway |
| Resilience4j | 2.1.0 | Circuit Breaker (ADR-009) |
| JWT (jjwt) | 0.12.5 | Autenticación OAuth2/OIDC |
| MapStruct | 1.5.5.Final | Mapeo de DTOs |
| TestContainers | 1.19.7 | Tests de integración con infraestructura real |
| iText PDF | 8.0.3 | Generación de certificados |
| AWS SDK v2 S3 | 2.25.10 | Almacenamiento de certificados |
| **ShedLock** | **5.13.0** | **Distributed lock para @Scheduled (ADR-018)** |
| **SpringDoc OpenAPI** | **2.3.0** | **Documentación de APIs — OpenAPI 3.1 (SRS §11.3)** |
| **JaCoCo Maven Plugin** | **0.8.11** | **Cobertura de código por capa** |

---

## 2. Cómo añadir un nuevo módulo

### Paso 1: Crear el módulo
```bash
mkdir {nombre}-service
```

### Paso 2: Declarar en el parent `pom.xml`
```xml
<modules>
    ...
    <module>{nombre}-service</module>
</modules>
```

### Paso 3: `{nombre}-service/pom.xml` mínimo
```xml
<parent>
    <groupId>com.javeriana.eventos</groupId>
    <artifactId>gestion-eventos-javeriana</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>{nombre}-service</artifactId>

<dependencies>
    <!-- Solo groupId:artifactId — la versión viene del parent BOM -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    ...
</dependencies>

<build>
    <plugins>
        <!-- Spring Boot packaging -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <!-- JaCoCo: hereda toda la configuración del parent, sin duplicar nada -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

### Regla de oro
> Si tienes que escribir `<version>` en un módulo hijo, estás rompiendo el DRY. Añade la versión al parent primero.

---

## 3. Cobertura de código con JaCoCo

### Ejecutar
```bash
# Build completo con cobertura
mvn clean verify

# Solo un módulo (más rápido durante desarrollo)
mvn -pl payment-service clean verify

# Excluir E2E tests (requieren Docker):
mvn -pl payment-service clean verify -Dsurefire.excludes="**/*IT.java"
```

### Ver el reporte
```bash
# Abrir en el navegador
open payment-service/target/site/jacoco/index.html
```

### Thresholds por capa (arquitectura hexagonal)

| Capa | Paquete | Líneas mínimas | Ramas mínimas | Falla el build |
|---|---|---|---|---|
| **domain** | `**/domain/**` | 90% | 85% | Sí (cuando se active) |
| **application** | `**/application/**` | 85% | 75% | Sí (cuando se active) |
| **infrastructure** | `**/infrastructure/**` | 60% | — | No (fase de activación posterior) |

> **Estado actual:** `haltOnFailure=false` (modo soft). Los thresholds se reportan pero no fallan el build.
> Se activará (`haltOnFailure=true`) una vez que los tests de integración del OutboxRelayService y E2E estén completos.
> Buscar `TODO[JACOCO-SOFT-MODE]` en `pom.xml` para encontrar el punto de activación.

### Exclusiones globales

Las siguientes clases están excluidas del análisis en **todos** los módulos:

| Patrón | Razón |
|---|---|
| `**/*Application.class` | Clase main de Spring Boot — sin lógica de negocio |
| `**/config/**/*.class` | Configuraciones Spring — no testables sin contexto completo |
| `**/dto/**/*.class` | Clases de transferencia — sin lógica |
| `**/entity/**/*.class` | Entidades JPA — mapeos sin lógica |

---

## 4. Política de actualización de versiones

### Proceso de bump
1. **Verificar compatibilidad** entre la nueva versión y el Spring Boot BOM.
2. **Actualizar solo en `<properties>` del parent** — nunca en módulos hijos.
3. **Ejecutar `mvn clean verify` en todos los módulos afectados** antes del commit.
4. **Mensaje de commit:** `build(deps): bump {dep} from {old} to {new}` con justificación.
5. **Documentar** en esta tabla de versiones.

### No usar rangos de versiones
```xml
<!-- MAL: versión abierta — builds no reproducibles -->
<version>[5.0.0,)</version>

<!-- BIEN: versión pinneada -->
<version>5.13.0</version>
```

---

## 5. Entorno de build

| Herramienta | Versión mínima | Notas |
|---|---|---|
| Maven | 3.8+ | 3.9.x recomendado |
| Java (compilación) | 17 LTS | Target en `<java.version>` del parent |
| Java (Maven runtime) | 17+ | Maven puede correr con JDK 17, 21 o 25 compilando a target 17 |
| Docker | 24+ | Requerido para tests con TestContainers (`*IT.java`) |

> **Nota sobre JDK dual:** Si `mvn -version` muestra un JDK diferente al del PATH (ej. JDK 25 via Homebrew con `JAVA_HOME` apuntando ahí, y JDK 17 en PATH), Maven compila correctamente a target 17 usando `--release 17`. Los tests unitarios siguen funcionando. Los tests con TestContainers también, porque Docker es el que corre los contenedores.
