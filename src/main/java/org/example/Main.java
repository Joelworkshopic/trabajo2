package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;
import java.util.Set;

@SpringBootApplication
public class Main implements CommandLineRunner {

    private static final String BASE_URL = "https://gutendex.com/books/";
    private static final Gson gson = new Gson();

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        HttpClient client = HttpClient.newHttpClient();
        boolean running = true;

        while (running) {
            System.out.println("\n=== Menú Gutendex ===");
            System.out.println("1. Buscar un libro por título (y guardar)");
            System.out.println("2. Listar libros registrados");
            System.out.println("3. Listar autores registrados");
            System.out.println("4. Listar autores vivos en un año");
            System.out.println("5. Listar libros por idioma");
            System.out.println("0. Salir");
            System.out.print("Ingrese su opción: ");

            String input = scanner.nextLine().trim();
            int choice = -1;
            try { choice = Integer.parseInt(input); } catch (NumberFormatException ignored) {}

            switch (choice) {
                case 1 -> {
                    System.out.print("Ingrese el título a buscar: ");
                    buscarYGuardarLibro(client, scanner.nextLine().trim());
                }
                case 2 -> listarLibros();
                case 3 -> listarAutores();
                case 4 -> {
                    System.out.print("Ingrese un año: ");
                    try {
                        listarAutoresVivosEnAnio(Integer.parseInt(scanner.nextLine().trim()));
                    } catch (NumberFormatException e) {
                        System.out.println("Año ingresado inválido.");
                    }
                }
                case 5 -> {
                    System.out.print("Ingrese el código de idioma (ES, EN, FR, PT): ");
                    listarLibrosPorIdioma(scanner.nextLine().trim());
                }
                case 0 -> {
                    System.out.println("Saliendo del programa...");
                    running = false;
                }
                default -> System.out.println("Opción inválida. Intente nuevamente.");
            }
        }

        scanner.close();
    }

    // ==================== OPCIÓN 1: BUSCAR Y GUARDAR ====================
    private void buscarYGuardarLibro(HttpClient client, String titulo) {
        String url = BASE_URL + "?search=" + titulo.replace(" ", "%20");

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (body.trim().startsWith("<!doctype html>") || body.trim().startsWith("<html")) {
                System.out.println("Error: La API devolvió HTML en lugar de JSON. Intente un término más simple.");
                return;
            }

            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray results = json.getAsJsonArray("results");

            if (results.size() == 0) {
                System.out.println("No se encontraron libros.");
                return;
            }

            JsonObject libro = results.get(0).getAsJsonObject();
            String tituloLibro = libro.get("title").getAsString();
            JsonArray autoresJson = libro.getAsJsonArray("authors");
            JsonArray idiomas = libro.getAsJsonArray("languages");
            String idioma = idiomas.size() > 0 ? idiomas.get(0).getAsString().toUpperCase() : "EN";
            int descargas = libro.get("download_count").getAsInt();

            boolean nuevo = guardarLibroEnDB(tituloLibro, autoresJson, idioma, descargas);

            if (nuevo) {
                System.out.println("\n--- Información del libro ---");
                System.out.println("Título: " + tituloLibro);
                System.out.println("Autor(es): (" + formatAutores(autoresJson) + ")");
                System.out.println("Idioma: " + idioma);
                System.out.println("Descargas: " + descargas);
            } else {
                System.out.println("⚠️ El libro '" + tituloLibro + "' ya existía en la base de datos. Use la opción 2 para verlo.");
            }

        } catch (Exception e) {
            System.out.println("❌ Error durante la búsqueda: " + e.getMessage());
        }
    }

    // ==================== GUARDAR LIBRO + AUTORES ====================
    private boolean guardarLibroEnDB(String titulo, JsonArray autoresJson, String idioma, int descargas) {
        if (bookRepository.findByTitle(titulo).isPresent()) {
            return false;
        }

        try {
            Book libro = new Book(titulo, idioma, descargas);

            for (int i = 0; i < autoresJson.size(); i++) {
                JsonObject autorObj = autoresJson.get(i).getAsJsonObject();
                String nombreAutor = autorObj.get("name").getAsString();
                Integer nacimiento = autorObj.has("birth_year") && !autorObj.get("birth_year").isJsonNull()
                        ? autorObj.get("birth_year").getAsInt() : null;
                Integer muerte = autorObj.has("death_year") && !autorObj.get("death_year").isJsonNull()
                        ? autorObj.get("death_year").getAsInt() : null;

                Author autor = authorRepository.findByName(nombreAutor)
                        .orElseGet(() -> authorRepository.save(new Author(nombreAutor, nacimiento, muerte)));

                libro.addAuthor(autor);
            }

            bookRepository.save(libro);
            return true;

        } catch (Exception e) {
            System.out.println("❌ Error de base de datos: " + e.getMessage());
            return false;
        }
    }

    // ==================== OPCIÓN 2: LISTAR LIBROS ====================
    @Transactional
    private void listarLibros() {
        try {
            var libros = bookRepository.findAll();

            if (libros.isEmpty()) {
                System.out.println("No hay libros registrados en la base de datos.");
                return;
            }

            for (Book libro : libros) {
                System.out.println("- Título: " + libro.getTitle());
                System.out.println("  Autor(es): (" + formatAutoresFromSet(libro.getAuthors()) + ")");
                System.out.println("  Idioma: " + libro.getLanguage());
                System.out.println("  Descargas: " + libro.getDownloadCount());
            }

        } catch (Exception e) {
            System.out.println("❌ Error al listar libros: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 3: LISTAR AUTORES ====================
    @Transactional
    private void listarAutores() {
        try {
            var autores = authorRepository.findAll();

            if (autores.isEmpty()) {
                System.out.println("No hay autores registrados en la base de datos.");
                return;
            }

            for (Author autor : autores) {
                System.out.println("- Nombre: " + autor.getName());
                System.out.println("  Año de nacimiento: " + (autor.getBirthYear() != null ? autor.getBirthYear() : "Desconocido"));
                System.out.println("  Año de muerte: " + (autor.getDeathYear() != null ? autor.getDeathYear() : "Desconocido"));
                System.out.println("  Libros: " + (autor.getBooks().isEmpty() ? "Ninguno" : formatBooksFromSet(autor.getBooks())));
            }

        } catch (Exception e) {
            System.out.println("❌ Error al listar autores: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 4: AUTORES VIVOS EN UN AÑO ====================
    @Transactional
    private void listarAutoresVivosEnAnio(int anio) {
        try {
            var autores = authorRepository.findByBirthYearLessThanEqualAndDeathYearGreaterThanEqual(anio, anio);

            if (autores.isEmpty()) {
                System.out.println("No hay autores vivos registrados en ese año.");
                return;
            }

            for (Author autor : autores) {
                System.out.println("- Nombre: " + autor.getName());
                System.out.println("  Año de nacimiento: " + (autor.getBirthYear() != null ? autor.getBirthYear() : "Desconocido"));
                System.out.println("  Año de muerte: " + (autor.getDeathYear() != null ? autor.getDeathYear() : "Desconocido"));
                System.out.println("  Libros: " + (autor.getBooks().isEmpty() ? "Ninguno" : formatBooksFromSet(autor.getBooks())));
            }

        } catch (Exception e) {
            System.out.println("❌ Error al listar autores por año: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 5: LISTAR LIBROS POR IDIOMA ====================
    @Transactional
    private void listarLibrosPorIdioma(String idioma) {
        try {
            var libros = bookRepository.findAll().stream()
                    .filter(l -> l.getLanguage().equalsIgnoreCase(idioma))
                    .toList();

            if (libros.isEmpty()) {
                System.out.println("No hay libros registrados en ese idioma.");
                return;
            }

            for (Book libro : libros) {
                System.out.println("- Título: " + libro.getTitle());
                System.out.println("  Autor(es): (" + formatAutoresFromSet(libro.getAuthors()) + ")");
                System.out.println("  Idioma: " + libro.getLanguage());
                System.out.println("  Descargas: " + libro.getDownloadCount());
            }

        } catch (Exception e) {
            System.out.println("❌ Error al listar libros por idioma: " + e.getMessage());
        }
    }

    // ==================== AUXILIARES ====================
    private String formatAutores(JsonArray autoresJson) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < autoresJson.size(); i++) {
            JsonObject autorObj = autoresJson.get(i).getAsJsonObject();
            sb.append(autorObj.get("name").getAsString());
            if (i < autoresJson.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String formatAutoresFromSet(Set<?> collection) {
        if (collection.isEmpty()) return "Ninguno";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Object obj : collection) {
            if (obj instanceof Author) {
                sb.append(((Author) obj).getName());
            } else if (obj instanceof Book) {
                sb.append(((Book) obj).getTitle());
            }
            count++;
            if (count < collection.size()) sb.append(", ");
        }
        return sb.toString();
    }

    private String formatBooksFromSet(Set<Book> books) {
        if (books.isEmpty()) return "Ninguno";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Book book : books) {
            sb.append(book.getTitle());
            count++;
            if (count < books.size()) sb.append(", ");
        }
        return sb.toString();
    }
}
