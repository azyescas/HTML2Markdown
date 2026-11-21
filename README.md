Nombre: Azkahira Ziret Yescas Diaz
Expediente: 222213158

Preguntas de reflexión:

1. ¿Qué papel desempeña la aplicación dentro de una arquitectura cliente-servidor?
R: Actúa como cliente HTTP que solicita y consume recursos web para transformarlos
localmente a Markdown.

2. ¿Qué responsabilidades corresponden al servidor y cuáles al cliente?
R: El servidor aloja, valida y entrega el recurso HTML con sus encabezados y estado
HTTP; el cliente conecta, valida respuesta/tipo/tamaño, analiza el HTML, convierte a
Markdown y guarda el archivo.

3. ¿Qué diferencia existe entre recuperar HTML e interpretar su estructura?
R: Recuperar es descargar el texto crudo mediante la conexión HTTP; interpetar es
reconocer etiquetas, atributos, texto y jeraquía del documento.

4. ¿Por qué no basta reemplazar etiquetas HTML con expresiones regulares?
R: No entienden bien el anidamiento ni el contexto de las etiquetas HTML.

5. ¿Qué dificultades presentan los elementos anidados?
R: Exigen rastrear qué etiquetas están abiertas/cerradas para mantener el orden,
formato, listas y escapar caracteres sin romper la sintaxis

6. ¿Qué información puede perderse al convertir HTML a Markdown?
R: Estilos CSS, clases/IDs, scripts, atributos complejos y formatos HTML sin
equivalente directo en Markdown.

7. ¿Por qué deben resolverse las URL relativas?
R: Para que enlaces e imágenes sigan apuntando al recurso correcto respecto a la URL
base aunque el .md se guarde en otra carpeta.

8. ¿Qué ventajas ofrece separar descarga, análisis y conversión en clases?
R: Facilita leer, probar, depurar y mantener cada responsabilidad por separado.

9. ¿Qué limitaciones presenta ParserDelegator con HTML moderno?
R: Está pensado para HTML heredado y puede no interpretar bien etiquetas o estructuras
de HTML5 actual.

10. ¿Qué modificaciones serian necesarias para convertir tablas HTML a Markdown?
R: Detectar <table>, <tr>, <th> y <td>, recolectar filas/celdas y formatearlas con
pipes (|) y fila separadora ( | --- | ) en Markdown.
