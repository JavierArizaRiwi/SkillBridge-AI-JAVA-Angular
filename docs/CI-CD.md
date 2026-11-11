# CI/CD Pipeline and Branch Protection

Este documento describe la arquitectura y configuración del pipeline de CI (Integración Continua) y las políticas de protección de ramas para el proyecto SkillBridge-AI.

## Arquitectura del Pipeline

El pipeline está implementado utilizando **GitHub Actions**. Se dispara automáticamente con cualquier Push o Pull Request hacia las ramas protegidas (main y develop).

### Jobs

El pipeline consta de tres trabajos (jobs) principales:

1. **Backend**:
   - Configura el entorno Java (versión 21, distribución Temurin).
   - Configura caché para Maven (.m2).
   - Ejecuta validaciones y pruebas automatizadas usando Maven: mvn -B clean verify.

2. **Frontend**:
   - Configura el entorno Node.js (versión 22) y el caché de 
pm basado en package-lock.json.
   - Realiza una instalación limpia y reproducible: 
pm ci.
   - Ejecuta pruebas del frontend en modo no interactivo: 
pm run test -- --watch=false --browsers=ChromeHeadless.
   - Compila la aplicación para validación: 
pm run build.

3. **Docker**:
   - Depende de la ejecución exitosa de los jobs ackend y rontend.
   - Valida la construcción de las imágenes Docker (frontend y backend) sin realizar un push (Dry-run build).

### Seguridad y Entorno

El pipeline no requiere secrets o variables de entorno confidenciales en su validación básica. Utiliza contextos estándar y repositorios para la verificación de integridad de las imágenes.

## Políticas de Branch Protection

Para garantizar la estabilidad, las ramas develop y main están estrictamente protegidas mediante las siguientes reglas, configuradas desde **Settings > Branches** en GitHub:

- **Require a pull request before merging**: No se permiten commits directos (*Restringir direct pushes*).
- **Require approvals**: Todo PR exige al menos una revisión aprobada por otro miembro del equipo.
- **Require status checks to pass before merging**: Se seleccionan los checks estabilizados (por ejemplo, ackend, rontend, docker) como obligatorios. Esto bloquea cualquier fusión si el CI falla (está en rojo).

## Casos de Prueba (QA)

| ID | Escenario | Comportamiento Esperado |
|----|-----------|-------------------------|
| QA-01 | PR válido hacia develop con tests pasando | Pipeline en verde, permite hacer merge tras aprobación. |
| QA-02 | PR con test de backend fallido | Fallo en Maven, status check en rojo, merge bloqueado. |
| QA-03 | PR con fallo en frontend (compilación o tests) | Fallo en paso de Node, status check en rojo, merge bloqueado. |
| QA-04 | PR con error en Dockerfile | Fallo en job de Docker, merge bloqueado. |
| QA-05 | Intento de push directo a develop o main | GitHub rechaza el push indicando violación de política. |
| QA-06 | Intento de merge sin revisión aprobada | El botón de Merge en GitHub se mantiene deshabilitado. |
| QA-07 | Ejecución en fork o sin secrets | Completa exitosamente verificaciones básicas. |

## Troubleshooting

- **Caché en Node / Maven**: Si el proceso falla por un problema de dependencias obsoletas, intenta purgar la caché desde la interfaz de GitHub Actions ("Actions" > "Caches" > "Delete").
- **Error en NPM CI**: Verifica que package-lock.json esté subido al repositorio y se encuentre actualizado respecto al package.json.
- **Fallo en Pruebas de Frontend (ChromeHeadless)**: Asegúrate de que tu versión de Karma permita utilizar ChromeHeadless o reemplaza la validación con otro navegador simulado de la CI si es necesario.
