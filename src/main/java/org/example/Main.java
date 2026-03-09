package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.Scanner;

public class Main {

    private static final String BASE_URL = "https://gutendex.com/books/";
    private static final Gson gson = new Gson();

    // Configuración PostgreSQL
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/gutendex";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "shijima";

    public static void main(String[] args) {
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
                        listarAutoresVivosEnAnio(client, Integer.parseInt(scanner.nextLine().trim()));
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
    private static void buscarYGuardarLibro(HttpClient client, String titulo) {
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

    // ==================== VERIFICAR EXISTENCIA DEL LIBRO ====================
    private static boolean libroExiste(String titulo) {
        String sql = "SELECT 1 FROM libros WHERE title = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, titulo);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("❌ Error de base de datos al verificar libro: " + e.getMessage());
            return false;
        }
    }

    // ==================== GUARDAR LIBRO + AUTORES ====================
    private static boolean guardarLibroEnDB(String titulo, JsonArray autoresJson, String idioma, int descargas) {
        if (libroExiste(titulo)) return false;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            int libroId = -1;
            String insertLibroSQL = "INSERT INTO libros (title, language, download_count) VALUES (?, ?, ?) RETURNING id";
            try (PreparedStatement stmt = conn.prepareStatement(insertLibroSQL)) {
                stmt.setString(1, titulo);
                stmt.setString(2, idioma);
                stmt.setInt(3, descargas);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) libroId = rs.getInt(1);
            }

            for (int i = 0; i < autoresJson.size(); i++) {
                JsonObject autorObj = autoresJson.get(i).getAsJsonObject();
                String nombreAutor = autorObj.get("name").getAsString();
                Integer nacimiento = autorObj.has("birth_year") && !autorObj.get("birth_year").isJsonNull()
                        ? autorObj.get("birth_year").getAsInt() : null;
                Integer muerte = autorObj.has("death_year") && !autorObj.get("death_year").isJsonNull()
                        ? autorObj.get("death_year").getAsInt() : null;

                int autorId = -1;
                String selectAutorSQL = "SELECT id FROM autores WHERE name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(selectAutorSQL)) {
                    stmt.setString(1, nombreAutor);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) autorId = rs.getInt("id");
                }

                if (autorId == -1) {
                    String insertAutorSQL = "INSERT INTO autores (name, birth_year, death_year) VALUES (?, ?, ?) RETURNING id";
                    try (PreparedStatement stmt = conn.prepareStatement(insertAutorSQL)) {
                        stmt.setString(1, nombreAutor);
                        if (nacimiento != null) stmt.setInt(2, nacimiento); else stmt.setNull(2, Types.INTEGER);
                        if (muerte != null) stmt.setInt(3, muerte); else stmt.setNull(3, Types.INTEGER);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) autorId = rs.getInt(1);
                    }
                }

                if (libroId != -1 && autorId != -1) {
                    String insertLA = "INSERT INTO libros_autores (libro_id, autor_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
                    try (PreparedStatement stmt = conn.prepareStatement(insertLA)) {
                        stmt.setInt(1, libroId);
                        stmt.setInt(2, autorId);
                        stmt.executeUpdate();
                    }
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            System.out.println("❌ Error de base de datos: " + e.getMessage());
            return false;
        }
    }

    // ==================== OPCIÓN 2: LISTAR LIBROS ====================
    private static void listarLibros() {
        String sql = "SELECT l.title, l.language, l.download_count, STRING_AGG(a.name, ', ') AS autores " +
                "FROM libros l " +
                "LEFT JOIN libros_autores la ON la.libro_id = l.id " +
                "LEFT JOIN autores a ON a.id = la.autor_id " +
                "GROUP BY l.id, l.title, l.language, l.download_count";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean hayLibros = false;
            while (rs.next()) {
                hayLibros = true;
                System.out.println("- Título: " + rs.getString("title"));
                System.out.println("  Autor(es): (" + rs.getString("autores") + ")");
                System.out.println("  Idioma: " + rs.getString("language"));
                System.out.println("  Descargas: " + rs.getInt("download_count"));
            }
            if (!hayLibros) System.out.println("No hay libros registrados en la base de datos.");

        } catch (SQLException e) {
            System.out.println("❌ Error al listar libros: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 3: LISTAR AUTORES ====================
    private static void listarAutores() {
        String sql = "SELECT a.name, a.birth_year, a.death_year, STRING_AGG(l.title, ', ') AS libros " +
                "FROM autores a " +
                "LEFT JOIN libros_autores la ON la.autor_id = a.id " +
                "LEFT JOIN libros l ON l.id = la.libro_id " +
                "GROUP BY a.id, a.name, a.birth_year, a.death_year";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean hayAutores = false;
            while (rs.next()) {
                hayAutores = true;
                System.out.println("- Nombre: " + rs.getString("name"));
                System.out.println("  Año de nacimiento: " + (rs.getObject("birth_year") != null ? rs.getInt("birth_year") : "Desconocido"));
                System.out.println("  Año de muerte: " + (rs.getObject("death_year") != null ? rs.getInt("death_year") : "Desconocido"));
                System.out.println("  Libros: " + (rs.getString("libros") != null ? rs.getString("libros") : "Ninguno"));
            }
            if (!hayAutores) System.out.println("No hay autores registrados en la base de datos.");

        } catch (SQLException e) {
            System.out.println("❌ Error al listar autores: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 4: AUTORES VIVOS EN UN AÑO ====================
    private static void listarAutoresVivosEnAnio(HttpClient client, int anio) {
        String sql = "SELECT a.name, a.birth_year, a.death_year, STRING_AGG(l.title, ', ') AS libros " +
                "FROM autores a " +
                "LEFT JOIN libros_autores la ON la.autor_id = a.id " +
                "LEFT JOIN libros l ON l.id = la.libro_id " +
                "WHERE (a.birth_year IS NULL OR a.birth_year <= ?) AND (a.death_year IS NULL OR a.death_year >= ?) " +
                "GROUP BY a.id, a.name, a.birth_year, a.death_year";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, anio);
            stmt.setInt(2, anio);
            ResultSet rs = stmt.executeQuery();

            boolean hayVivos = false;
            while (rs.next()) {
                hayVivos = true;
                System.out.println("- Nombre: " + rs.getString("name"));
                System.out.println("  Año de nacimiento: " + (rs.getObject("birth_year") != null ? rs.getInt("birth_year") : "Desconocido"));
                System.out.println("  Año de muerte: " + (rs.getObject("death_year") != null ? rs.getInt("death_year") : "Desconocido"));
                System.out.println("  Libros: " + (rs.getString("libros") != null ? rs.getString("libros") : "Ninguno"));
            }
            if (!hayVivos) System.out.println("No hay autores vivos registrados en ese año.");

        } catch (SQLException e) {
            System.out.println("❌ Error al listar autores por año: " + e.getMessage());
        }
    }

    // ==================== OPCIÓN 5: LISTAR LIBROS POR IDIOMA ====================
    private static void listarLibrosPorIdioma(String idioma) {
        String sql = "SELECT l.title, l.language, l.download_count, STRING_AGG(a.name, ', ') AS autores " +
                "FROM libros l " +
                "LEFT JOIN libros_autores la ON la.libro_id = l.id " +
                "LEFT JOIN autores a ON a.id = la.autor_id " +
                "WHERE l.language = ? " +
                "GROUP BY l.id, l.title, l.language, l.download_count";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, idioma.toUpperCase());
            ResultSet rs = stmt.executeQuery();

            boolean hayLibros = false;
            while (rs.next()) {
                hayLibros = true;
                System.out.println("- Título: " + rs.getString("title"));
                System.out.println("  Autor(es): (" + rs.getString("autores") + ")");
                System.out.println("  Idioma: " + rs.getString("language"));
                System.out.println("  Descargas: " + rs.getInt("download_count"));
            }
            if (!hayLibros) System.out.println("No hay libros registrados en ese idioma.");

        } catch (SQLException e) {
            System.out.println("❌ Error al listar libros por idioma: " + e.getMessage());
        }
    }

    // ==================== AUXILIARES ====================
    private static String formatAutores(JsonArray autoresJson) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < autoresJson.size(); i++) {
            JsonObject autorObj = autoresJson.get(i).getAsJsonObject();
            sb.append(autorObj.get("name").getAsString());
            if (i < autoresJson.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}
