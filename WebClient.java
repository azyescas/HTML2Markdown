import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class WebClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    public String descargarHTML(URL url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("La URL no puede ser nula.");
        }

        URLConnection conexion = url.openConnection();
        conexion.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conexion.setReadTimeout(READ_TIMEOUT_MS);
        conexion.setRequestProperty("User-Agent", "Mozilla/5.0 (HTML2Markdown WebClient)");

        HttpURLConnection httpConn = (conexion instanceof HttpURLConnection)
                ? (HttpURLConnection) conexion
                : null;

        try {
            if (httpConn != null) {
                httpConn.setInstanceFollowRedirects(true);
                httpConn.connect();
                int codigo = httpConn.getResponseCode();
                if (codigo < 200 || codigo >= 300) {
                    throw new IOException("Respuesta HTTP no exitosa: " + codigo + " " + httpConn.getResponseMessage());
                }
            } else {
                conexion.connect();
            }

            String contentType = conexion.getContentType();
            if (contentType == null || !(contentType.toLowerCase().contains("text/html")
                    || contentType.toLowerCase().contains("application/xhtml+xml"))) {
                throw new IOException("El contenido no es HTML. Content-Type: " + contentType);
            }

            Charset charset = extraerCharset(contentType);

            StringBuilder contenido = new StringBuilder();
            long bytesLeidos = 0;

            try (InputStream in = conexion.getInputStream();
                 InputStreamReader isr = new InputStreamReader(in, charset);
                 BufferedReader br = new BufferedReader(isr)) {

                char[] buffer = new char[4096];
                int leidos;
                while ((leidos = br.read(buffer)) != -1) {
                    bytesLeidos += new String(buffer, 0, leidos).getBytes(charset).length;
                    if (bytesLeidos > MAX_BYTES) {
                        throw new IOException("El documento supera el límite máximo permitido de " + MAX_BYTES + " bytes.");
                    }
                    contenido.append(buffer, 0, leidos);
                }
            }

            return contenido.toString();
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    private Charset extraerCharset(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String[] partes = contentType.split(";");
        for (String parte : partes) {
            String limpia = parte.trim();
            if (limpia.toLowerCase().startsWith("charset=")) {
                String nombreCharset = limpia.substring("charset=".length()).trim()
                        .replace("\"", "");
                if (!nombreCharset.isEmpty()) {
                    try {
                        if (nombreCharset.equalsIgnoreCase("utf8") || nombreCharset.equalsIgnoreCase("utf-8")) {
                            return StandardCharsets.UTF_8;
                        }
                        return Charset.forName(nombreCharset);
                    } catch (Exception ignored) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}