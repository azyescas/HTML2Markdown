import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTML2Markdown {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java HTML2Markdown <url> [archivo_salida.md]");
            return;
        }

        String url = args[0];
        String rutaSalida = (args.length > 1) ? args[1] : obtenerNombrePorDefecto(url);

        try {
            String html = descargarHtml(url);
            String markdown = convertirHtmlAMarkdown(html);
            guardarArchivo(rutaSalida, markdown);
            System.out.println("Archivo guardado en: " + Paths.get(rutaSalida).toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error al procesar: " + e.getMessage());
        }
    }

    private static String obtenerNombrePorDefecto(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isBlank()) {
                String nombre = Paths.get(path).getFileName().toString();
                int punto = nombre.lastIndexOf('.');
                if (punto > 0) {
                    nombre = nombre.substring(0, punto);
                }
                return nombre + ".md";
            }
        } catch (Exception ignored) {
        }
        return "output/articulo.md";
    }

    private static String descargarHtml(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " al consultar " + url);
        }
        return response.body();
    }

    private static String convertirHtmlAMarkdown(String html) {
        String md = html;

        // Encabezados
        md = md.replaceAll("(?is)<h1[^>]*>(.*?)</h1>", "# $1\n\n");
        md = md.replaceAll("(?is)<h2[^>]*>(.*?)</h2>", "## $1\n\n");
        md = md.replaceAll("(?is)<h3[^>]*>(.*?)</h3>", "### $1\n\n");

        // Negritas
        md = md.replaceAll("(?is)<(?:strong|b)[^>]*>(.*?)</(?:strong|b)>", "**$1**");

        // Enlaces <a href="...">texto</a>
        Matcher linkMatcher = Pattern.compile("(?is)<a\\s+[^>]*href=[\"']([^\"']*)[\"'][^>]*>(.*?)</a>").matcher(md);
        StringBuffer sbLinks = new StringBuffer();
        while (linkMatcher.find()) {
            String href = linkMatcher.group(1).trim();
            String texto = linkMatcher.group(2).replaceAll("(?s)<[^>]+>", "").trim();
            linkMatcher.appendReplacement(sbLinks, Matcher.quoteReplacement("[" + texto + "](" + href + ")"));
        }
        linkMatcher.appendTail(sbLinks);
        md = sbLinks.toString();

        // Elementos de lista <li>
        md = md.replaceAll("(?is)<li[^>]*>(.*?)</li>", "- $1\n");
        md = md.replaceAll("(?is)</?ul[^>]*>", "\n");

        // Párrafos
        md = md.replaceAll("(?is)<p[^>]*>(.*?)</p>", "$1\n\n");

        // Limpiar etiquetas HTML restantes y normalizar espacios/saltos
        md = md.replaceAll("(?s)<[^>]+>", "");
        md = md.replace("&nbsp;", " ");
        md = md.replaceAll("[ \\t]+", " ");
        md = md.replaceAll(" ?\n ?", "\n");
        md = md.replaceAll("\n{3,}", "\n\n").trim() + "\n";

        return md;
    }

    private static void guardarArchivo(String rutaSalida, String contenido) throws IOException {
        Path destino = Paths.get(rutaSalida);
        Path carpetaPadre = destino.getParent();
        if (carpetaPadre != null) {
            Files.createDirectories(carpetaPadre);
        }
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
    }
}