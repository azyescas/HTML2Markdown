import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarkdownWriter {

    public void guardar(Path destino, String contenido) throws IOException {
        if (destino == null) {
            throw new IllegalArgumentException("La ruta de destino no puede ser nula.");
        }
        Path carpetaPadre = destino.toAbsolutePath().getParent();
        if (carpetaPadre != null) {
            Files.createDirectories(carpetaPadre);
        }
        Files.writeString(destino, contenido != null ? contenido : "", StandardCharsets.UTF_8);
    }

    public Path resolverDestinoPredeterminado(URL url) {
        if (url != null) {
            String ruta = url.getPath();
            if (ruta != null && !ruta.isBlank() && !ruta.endsWith("/")) {
                String nombreArchivo = Paths.get(ruta).getFileName().toString();
                int punto = nombreArchivo.lastIndexOf('.');
                if (punto > 0) {
                    nombreArchivo = nombreArchivo.substring(0, punto);
                }
                if (!nombreArchivo.isBlank()) {
                    return Paths.get(nombreArchivo + ".md");
                }
            }
        }
        return Paths.get("articulo.md");
    }
}