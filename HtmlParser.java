import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

public class HtmlParser {

    public String convertir(String html) throws IOException {
        StringBuilder salida = new StringBuilder();
        ParserDelegator delegator = new ParserDelegator();

        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            private boolean enLista = false;
            private boolean enLink = false;
            private String hrefActual = "";
            private StringBuilder textoLink = new StringBuilder();

            @Override
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.H1) {
                    salida.append("\n# ");
                } else if (t == HTML.Tag.H2) {
                    salida.append("\n## ");
                } else if (t == HTML.Tag.H3) {
                    salida.append("\n### ");
                } else if (t == HTML.Tag.STRONG || t == HTML.Tag.B) {
                    salida.append("**");
                } else if (t == HTML.Tag.UL) {
                    enLista = true;
                    salida.append("\n");
                } else if (t == HTML.Tag.LI) {
                    salida.append("- ");
                } else if (t == HTML.Tag.A) {
                    enLink = true;
                    textoLink.setLength(0);
                    Object href = a.getAttribute(HTML.Attribute.HREF);
                    hrefActual = (href != null) ? href.toString().trim() : "";
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                // Etiquetas vacías (ej. <br>, <hr>, <img src="..." alt="...">)
                if (t == HTML.Tag.BR) {
                    salida.append("  \n");
                } else if (t == HTML.Tag.HR) {
                    salida.append("\n---\n");
                } else if (t == HTML.Tag.IMG) {
                    Object src = a.getAttribute(HTML.Attribute.SRC);
                    Object alt = a.getAttribute(HTML.Attribute.ALT);
                    String textoAlt = (alt != null) ? alt.toString() : "imagen";
                    String rutaSrc = (src != null) ? src.toString() : "";
                    if (!rutaSrc.isEmpty()) {
                        salida.append("![").append(textoAlt).append("](").append(rutaSrc).append(")");
                    }
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                String texto = new String(data);
                if (texto.trim().isEmpty() && !texto.contains(" ")) {
                    return;
                }
                if (enLink) {
                    textoLink.append(texto);
                } else {
                    salida.append(texto);
                }
            }

            @Override
            public void handleEndTag(HTML.Tag t, int pos) {
                if (t == HTML.Tag.H1 || t == HTML.Tag.H2 || t == HTML.Tag.H3) {
                    salida.append("\n\n");
                } else if (t == HTML.Tag.STRONG || t == HTML.Tag.B) {
                    salida.append("**");
                } else if (t == HTML.Tag.LI) {
                    salida.append("\n");
                } else if (t == HTML.Tag.UL) {
                    enLista = false;
                    salida.append("\n");
                } else if (t == HTML.Tag.P && !enLista) {
                    salida.append("\n\n");
                } else if (t == HTML.Tag.A) {
                    enLink = false;
                    salida.append("[").append(textoLink.toString().trim()).append("](").append(hrefActual).append(")");
                    hrefActual = "";
                    textoLink.setLength(0);
                }
            }
        };

        try (Reader reader = new StringReader(html)) {
            delegator.parse(reader, callback, true);
        }

        return salida.toString()
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim() + "\n";
    }
}