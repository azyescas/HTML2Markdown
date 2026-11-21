import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

public class MarkdownConverter {

    private static final Map<String, String> ENTIDADES_HTML = new HashMap<>();

    static {
        ENTIDADES_HTML.put("&amp;", "&");
        ENTIDADES_HTML.put("&lt;", "<");
        ENTIDADES_HTML.put("&gt;", ">");
        ENTIDADES_HTML.put("&quot;", "\"");
        ENTIDADES_HTML.put("&#39;", "'");
        ENTIDADES_HTML.put("&apos;", "'");
        ENTIDADES_HTML.put("&nbsp;", " ");
        ENTIDADES_HTML.put("&aacute;", "á");
        ENTIDADES_HTML.put("&eacute;", "é");
        ENTIDADES_HTML.put("&iacute;", "í");
        ENTIDADES_HTML.put("&oacute;", "ó");
        ENTIDADES_HTML.put("&uacute;", "ú");
        ENTIDADES_HTML.put("&Aacute;", "Á");
        ENTIDADES_HTML.put("&Eacute;", "É");
        ENTIDADES_HTML.put("&Iacute;", "Í");
        ENTIDADES_HTML.put("&Oacute;", "Ó");
        ENTIDADES_HTML.put("&Uacute;", "Ú");
        ENTIDADES_HTML.put("&ntilde;", "ñ");
        ENTIDADES_HTML.put("&Ntilde;", "Ñ");
    }

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
            private boolean enScript = false;
            private boolean enStyle = false;
            private boolean enCodigo = false;
            private boolean enPre = false;
            private boolean enEnlace = false;
            private String hrefActual = "";
            private final StringBuilder textoEnlace = new StringBuilder();
            private final StringBuilder bloquePre = new StringBuilder();

            @Override
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.SCRIPT) {
                    enScript = true;
                    return;
                }
                if (t == HTML.Tag.STYLE) {
                    enStyle = true;
                    return;
                }
                if (enScript || enStyle) {
                    return;
                }

                String nivelEncabezado = prefijoEncabezado(t);
                if (nivelEncabezado != null) {
                    asegurarSaltoBloque(out);
                    out.append(nivelEncabezado).append(" ");
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
                if (enScript || enStyle) {
                    return;
                }
                if (t == HTML.Tag.BR) {
                    destinoActual().append("  \n");
                } else if (t == HTML.Tag.HR) {
                    asegurarSaltoBloque(out);
                    out.append("---\n\n");
                } else if (t == HTML.Tag.IMG) {
                    Object src = a.getAttribute(HTML.Attribute.SRC);
                    if (src != null && !src.toString().isBlank()) {
                        Object alt = a.getAttribute(HTML.Attribute.ALT);
                        String altLimpio = (alt != null) ? decodificarEntidades(alt.toString().trim()) : "";
                        String textoAlt = escaparMarkdown(altLimpio);
                        String urlResuelta = resolverReferencia(urlBase, src.toString().trim());
                        destinoActual().append("![").append(textoAlt).append("](").append(urlResuelta).append(")");
                    }
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                if (enScript || enStyle) {
                    return;
                }

                String texto = decodificarEntidades(new String(data));
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
                if (t == HTML.Tag.SCRIPT) {
                    enScript = false;
                    return;
                }
                if (t == HTML.Tag.STYLE) {
                    enStyle = false;
                    return;
                }
                if (enScript || enStyle) {
                    return;
                }

                if (prefijoEncabezado(t) != null || t == HTML.Tag.P) {
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

        String resultado = out.toString()
                .replaceAll(" *\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        return resultado.isEmpty() ? "" : resultado + "\n";
    }

    private static String prefijoEncabezado(HTML.Tag t) {
        if (t == HTML.Tag.H1) return "#";
        if (t == HTML.Tag.H2) return "##";
        if (t == HTML.Tag.H3) return "###";
        if (t == HTML.Tag.H4) return "####";
        if (t == HTML.Tag.H5) return "#####";
        if (t == HTML.Tag.H6) return "######";
        return null;
    }

    private static String decodificarEntidades(String texto) {
        String res = texto;
        for (Map.Entry<String, String> entry : ENTIDADES_HTML.entrySet()) {
            res = res.replace(entry.getKey(), entry.getValue());
        }
        return res;
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
        // Escapa caracteres especiales de Markdown en texto normal sin romper delimitadores generados
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