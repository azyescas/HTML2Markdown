import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

public class MarkdownConverter {

    private static class ListaContexto {
        final boolean ordenada;
        int contador = 0;

        ListaContexto(boolean ordenada) {
            this.ordenada = ordenada;
        }
    }

    public String convertir(String html, URL urlBase) throws IOException {
        StringBuilder out = new StringBuilder();
        ParserDelegator delegator = new ParserDelegator();

        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            private final Deque<ListaContexto> listas = new ArrayDeque<>();
            private boolean enCodigo = false;
            private boolean enPre = false;
            private boolean enEnlace = false;
            private String hrefActual = "";
            private final StringBuilder textoEnlace = new StringBuilder();
            private final StringBuilder bloquePre = new StringBuilder();

            @Override
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.H1) {
                    asegurarSaltoBloque(out);
                    out.append("# ");
                } else if (t == HTML.Tag.H2) {
                    asegurarSaltoBloque(out);
                    out.append("## ");
                } else if (t == HTML.Tag.H3) {
                    asegurarSaltoBloque(out);
                    out.append("### ");
                } else if (t == HTML.Tag.STRONG || t == HTML.Tag.B) {
                    destinoActual().append("**");
                } else if (t == HTML.Tag.EM || t == HTML.Tag.I) {
                    destinoActual().append("*");
                } else if (t == HTML.Tag.CODE) {
                    enCodigo = true;
                    if (!enPre) {
                        destinoActual().append("`");
                    }
                } else if (t == HTML.Tag.PRE) {
                    enPre = true;
                    bloquePre.setLength(0);
                    asegurarSaltoBloque(out);
                } else if (t == HTML.Tag.UL) {
                    asegurarSaltoBloque(out);
                    listas.push(new ListaContexto(false));
                } else if (t == HTML.Tag.OL) {
                    asegurarSaltoBloque(out);
                    listas.push(new ListaContexto(true));
                } else if (t == HTML.Tag.LI) {
                    asegurarSaltoLinea(out);
                    int nivel = Math.max(0, listas.size() - 1);
                    out.append("  ".repeat(nivel));
                    ListaContexto actual = listas.peek();
                    if (actual != null && actual.ordenada) {
                        actual.contador++;
                        out.append(actual.contador).append(". ");
                    } else {
                        out.append("- ");
                    }
                } else if (t == HTML.Tag.A) {
                    enEnlace = true;
                    textoEnlace.setLength(0);
                    Object href = a.getAttribute(HTML.Attribute.HREF);
                    hrefActual = resolverReferencia(urlBase, href != null ? href.toString().trim() : "");
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.BR) {
                    destinoActual().append("  \n");
                } else if (t == HTML.Tag.HR) {
                    asegurarSaltoBloque(out);
                    out.append("---\n\n");
                } else if (t == HTML.Tag.IMG) {
                    Object src = a.getAttribute(HTML.Attribute.SRC);
                    if (src != null && !src.toString().isBlank()) {
                        Object alt = a.getAttribute(HTML.Attribute.ALT);
                        String textoAlt = (alt != null) ? escaparMarkdown(alt.toString().trim()) : "imagen";
                        String urlResuelta = resolverReferencia(urlBase, src.toString().trim());
                        destinoActual().append("![").append(textoAlt).append("](").append(urlResuelta).append(")");
                    }
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                String texto = new String(data);
                if (enPre) {
                    bloquePre.append(texto);
                    return;
                }

                String limpio = texto.replace('\u00A0', ' ').replaceAll("[ \\t\\r\\n]+", " ");
                if (limpio.trim().isEmpty() && !limpio.equals(" ")) {
                    return;
                }

                StringBuilder dest = destinoActual();
                if (limpio.equals(" ") && (dest.length() == 0
                        || Character.isWhitespace(dest.charAt(dest.length() - 1)))) {
                    return;
                }

                String procesado = enCodigo ? limpio : escaparMarkdown(limpio);
                dest.append(procesado);
            }

            @Override
            public void handleEndTag(HTML.Tag t, int pos) {
                if (t == HTML.Tag.H1 || t == HTML.Tag.H2 || t == HTML.Tag.H3 || t == HTML.Tag.P) {
                    asegurarSaltoBloque(out);
                } else if (t == HTML.Tag.STRONG || t == HTML.Tag.B) {
                    destinoActual().append("**");
                } else if (t == HTML.Tag.EM || t == HTML.Tag.I) {
                    destinoActual().append("*");
                } else if (t == HTML.Tag.CODE) {
                    if (!enPre) {
                        destinoActual().append("`");
                    }
                    enCodigo = false;
                } else if (t == HTML.Tag.PRE) {
                    enPre = false;
                    out.append("```\n").append(bloquePre.toString().stripTrailing()).append("\n```\n\n");
                    bloquePre.setLength(0);
                } else if (t == HTML.Tag.LI) {
                    asegurarSaltoLinea(out);
                } else if (t == HTML.Tag.UL || t == HTML.Tag.OL) {
                    if (!listas.isEmpty()) {
                        listas.pop();
                    }
                    asegurarSaltoBloque(out);
                } else if (t == HTML.Tag.A) {
                    enEnlace = false;
                    String etiqueta = textoEnlace.toString().trim();
                    if (etiqueta.isEmpty()) {
                        etiqueta = hrefActual;
                    }
                    out.append("[").append(etiqueta).append("](").append(hrefActual).append(")");
                    hrefActual = "";
                    textoEnlace.setLength(0);
                }
            }

            private StringBuilder destinoActual() {
                return enEnlace ? textoEnlace : out;
            }
        };

        try (Reader reader = new StringReader(html)) {
            delegator.parse(reader, callback, true);
        }

        return out.toString()
                .replaceAll(" *\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim() + "\n";
    }

    private static String resolverReferencia(URL urlBase, String referencia) {
        if (referencia == null || referencia.isEmpty()) {
            return "";
        }
        if (urlBase == null) {
            return referencia;
        }
        try {
            URI baseUri = urlBase.toURI();
            return baseUri.resolve(referencia).toString();
        } catch (Exception e) {
            return referencia;
        }
    }

    private static String escaparMarkdown(String texto) {
        return texto.replaceAll("([\\\\`*_{}\\[\\]()#+\\-.!])", "\\\\$1");
    }

    private static void asegurarSaltoLinea(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    private static void asegurarSaltoBloque(StringBuilder sb) {
        if (sb.length() == 0) {
            return;
        }
        int len = sb.length();
        if (len >= 2 && sb.charAt(len - 1) == '\n' && sb.charAt(len - 2) == '\n') {
            return;
        }
        if (sb.charAt(len - 1) == '\n') {
            sb.append('\n');
        } else {
            sb.append("\n\n");
        }
    }
}