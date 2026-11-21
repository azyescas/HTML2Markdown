import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Locale;

public class HTML2Markdown {

    public static void main(String[] args) {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("Error: Debe proporcionar una URL.");
            System.err.println("Uso: java HTML2Markdown <url> [archivo_salida.md]");
            return;
        }

        String urlTexto = args[0].trim();

        System.out.println("HTML2Markdown");
        System.out.println("--------------------------------");
        System.out.println("URL: " + urlTexto);
        System.out.println();

        URL url;
        try {
            url = new URL(urlTexto);
            String protocolo = url.getProtocol();
            if (protocolo == null || (!protocolo.equalsIgnoreCase("http") && !protocolo.equalsIgnoreCase("https"))) {
                System.err.println("Error: URL invalida. Solo se admiten protocolos http o https.");
                return;
            }
            if (url.getHost() == null || url.getHost().isBlank()) {
                System.err.println("Error: URL invalida. Falta el nombre del servidor/host.");
                return;
            }
        } catch (MalformedURLException e) {
            System.err.println("Error: URL invalida -> " + e.getMessage());
            return;
        }

        System.out.println("Conectando con el servidor...");
        int codigoHttp = obtenerCodigoEstadoHttp(url);
        if (codigoHttp > 0) {
            System.out.println("Respuesta HTTP: " + codigoHttp);
        }

        WebClient client = new WebClient();
        MarkdownConverter converter = new MarkdownConverter();
        MarkdownWriter writer = new MarkdownWriter();

        String html;
        try {
            html = client.descargarHTML(url);
        } catch (UnknownHostException e) {
            System.err.println("Error: Servidor inaccesible. No se pudo resolver el host '" + url.getHost() + "'.");
            return;
        } catch (SocketTimeoutException e) {
            System.err.println("Error: Tiempo de espera agotado al conectar o leer desde el servidor.");
            return;
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("no es html") || msg.toLowerCase().contains("content-type")) {
                System.err.println("Error: Contenido no HTML -> " + msg);
            } else if (msg.toLowerCase().contains("respuesta http")) {
                System.err.println("Error: Respuesta HTTP no exitosa -> " + msg);
            } else {
                System.err.println("Error de conexion o descarga -> " + msg);
            }
            return;
        }

        System.out.println();
        System.out.println("Analizando HTML...");
        System.out.println("Convirtiendo a Markdown...");

        String markdown;
        try {
            markdown = converter.convertir(html, url);
        } catch (Exception e) {
            System.err.println("Error al analizar o convertir el HTML a Markdown: " + e.getMessage());
            return;
        }

        Path destino = (args.length > 1 && !args[1].isBlank())
                ? Paths.get(args[1].trim())
                : writer.resolverDestinoPredeterminado(url);

        try {
            writer.guardar(destino, markdown);
            long tamanoBytes = Files.size(destino);
            String tamanoFormateado = NumberFormat.getIntegerInstance(Locale.US).format(tamanoBytes);

            System.out.println();
            System.out.println("Archivo generado: " + destino);
            System.out.println("Tamano: " + tamanoFormateado + " bytes");
            System.out.println();
            System.out.println("Conversion finalizada.");
        } catch (AccessDeniedException e) {
            System.err.println("Error de escritura: Permiso denegado al escribir en '" + destino + "'.");
        } catch (IOException e) {
            System.err.println("Error de escritura: No se pudo guardar el archivo en '" + destino + "' -> " + e.getMessage());
        }
    }

    private static int obtenerCodigoEstadoHttp(URL url) {
        HttpURLConnection conn = null;
        try {
            URLConnection rawConn = url.openConnection();
            if (rawConn instanceof HttpURLConnection) {
                conn = (HttpURLConnection) rawConn;
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestMethod("GET");
                return conn.getResponseCode();
            }
        } catch (IOException ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return -1;
    }
}