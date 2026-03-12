# Gutendex Spring Application

Aplicación de consola en Java que permite buscar y gestionar libros utilizando la API de [Gutendex](https://gutendex.com/), una API no oficial de Project Gutenberg.

## Descripción

Esta aplicación permite a los usuarios buscar libros por título, almacenarlos localmente en una base de datos PostgreSQL, y realizar consultas como listar libros, autores, filtrar por idioma o encontrar autores vivos en un año específico.

La aplicación utiliza Spring Boot con JPA/Hibernate para la gestión de datos y Gson para el procesamiento de respuestas JSON de la API.

## Requisitos

- Java 17 o superior
- Maven 3.8 o superior
- PostgreSQL 12 o superior
- Acceso a internet para conectar con la API de Gutendex

## Configuración de la Base de Datos

La aplicación requiere una base de datos PostgreSQL configurada con los siguientes parámetros (modificables en `src/main/resources/application.properties`):

```
spring.datasource.url=jdbc:postgresql://localhost:5432/gutendex
spring.datasource.username=personalizable
spring.datasource.password=personalizable
```

Asegúrate de crear la base de datos `gutendex` en tu servidor PostgreSQL antes de ejecutar la aplicación.

## Instalación

1. Clona este repositorio:
   ```
   git clone <url-del-repositorio>
   cd gutendex-spring
   ```

2. Compila el proyecto usando Maven:
   ```
   mvn clean install
   ```

## Ejecución

Ejecuta la aplicación con el siguiente comando:

```
mvn spring-boot:run
```

O también puedes ejecutarla directamente desde el archivo JAR generado:

```
java -jar target/gutendex-spring-1.0-SNAPSHOT.jar
```

## Uso

Al iniciar la aplicación, se mostrará un menú interactivo con las siguientes opciones:

1. **Buscar un libro por título (y guardar)**: Busca un libro en la API de Gutendex y lo guarda en la base de datos local.
2. **Listar libros registrados**: Muestra todos los libros almacenados en la base de datos.
3. **Listar autores registrados**: Muestra todos los autores almacenados en la base de datos.
4. **Listar autores vivos en un año**: Filtra y muestra autores que estaban vivos en un año específico.
5. **Listar libros por idioma**: Muestra libros filtrados por un código de idioma específico (ES, EN, FR, PT).
0. **Salir**: Cierra la aplicación.

### Ejemplo de uso

1. Al seleccionar la opción 1, ingresa el título de un libro (por ejemplo, "Don Quijote").
2. La aplicación buscará el libro en Gutendex y mostrará la información si lo encuentra.
3. Si es la primera vez que se busca este libro, se guardará en la base de datos.
4. Puedes usar las otras opciones para consultar la información almacenada.

## Tecnologías Utilizadas

- **Spring Boot**: Framework principal de la aplicación
- **Spring Data JPA**: Para la gestión de datos
- **PostgreSQL**: Base de datos relacional
- **Gson**: Para el procesamiento de JSON
- **Maven**: Gestión de dependencias y construcción del proyecto

## Funcionalidades Principales

- Búsqueda de libros en la API de Gutendex
- Almacenamiento local de libros y autores
- Relación muchos-a-muchos entre libros y autores
- Consultas avanzadas (filtrado por año, idioma, etc.)
- Validación de datos duplicados

## Licencia

Este proyecto está licenciado 
