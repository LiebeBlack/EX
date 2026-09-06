package com.apex.files.ui

import com.apex.files.Screen

/** One help entry: a title plus short, actionable tips for a screen. */
data class HelpTips(val title: String, val tips: List<String>)

/**
 * Registry of in-app help for every screen (rendered by the "?" button in
 * [com.apex.files.ui.components.ApexTopBar]). Each entry doubles as the
 * manual / maintenance note for that screen.
 */
fun helpTips(screen: Screen): HelpTips? = when (screen) {
    is Screen.Home -> HelpTips(
        "Inicio",
        listOf(
            "Toca el panel de almacenamiento para abrir las estadísticas.",
            "«Sugerencias» muestra los archivos más grandes para liberar espacio.",
            "«Recientes» y «Favoritos» guardan accesos rápidos a tus carpetas.",
            "Mantén pulsado un elemento sugerido para abrirlo.",
        ),
    )
    is Screen.Explorer -> HelpTips(
        "Explorador",
        listOf(
            "Mantén pulsado un elemento para entrar en modo selección.",
            "Toca «Copiar/Mover» y navega al destino para pegar allí.",
            "El filtro (embudo) busca dentro de la carpeta al instante.",
            "«Ordenar» cambia el criterio: nombre, tamaño o fecha.",
        ),
    )
    is Screen.Search -> HelpTips(
        "Búsqueda",
        listOf(
            "Escribe para buscar en todo el almacenamiento al instante.",
            "Filtra por tamaño, fecha y extensión con los chips.",
            "Los resultados se actualizan solos mientras escribes.",
        ),
    )
    is Screen.Category -> HelpTips(
        "Categoría",
        listOf(
            "Mantén pulsado un elemento para seleccionar varios.",
            "Abre un elemento para verlo o reproducirlo en APEX.",
        ),
    )
    is Screen.Drives -> HelpTips(
        "Unidades",
        listOf(
            "Gestiona el almacenamiento interno y las unidades USB-OTG.",
            "Concede acceso SAF para conectar unidades extraíbles.",
        ),
    )
    is Screen.Settings -> HelpTips(
        "Ajustes",
        listOf(
            "Cambia el acento neón y el orden por defecto.",
            "La papelera guarda los borrados por volumen (restaurables).",
            "«Restablecer ajustes» devuelve todo a los valores por defecto.",
        ),
    )
    is Screen.Cleaner -> HelpTips(
        "Limpiador",
        listOf(
            "Detecta carpetas vacías (incluidas las que solo tienen archivos de 0 bytes).",
            "Los borrados son permanentes: revisa la lista antes de confirmar.",
        ),
    )
    is Screen.Duplicates -> HelpTips(
        "Duplicados",
        listOf(
            "Agrupa archivos idénticos por tamaño y SHA-256.",
            "«Seleccionar para eliminar» conserva la copia más reciente.",
        ),
    )
    is Screen.Apk -> HelpTips(
        "Filtro APK",
        listOf(
            "Lista los instaladores APK de cada volumen.",
            "«No instalados» resalta los APK sin equivalente instalado.",
            "El análisis profundo decodifica el manifest y el icono.",
        ),
    )
    is Screen.Stats -> HelpTips(
        "Estadísticas",
        listOf(
            "Distribución por tipo de archivo y tamaños.",
            "Los datos se recalculan sobre el índice local.",
        ),
    )
    is Screen.SpaceAnalyzer -> HelpTips(
        "Espacio",
        listOf(
            "El treemap muestra el tamaño relativo de cada carpeta y archivo.",
            "Toca una carpeta para entrar; toca un archivo (imagen, documento…) para abrirlo.",
            "El análisis está limitado en profundidad para ser rápido.",
        ),
    )
    is Screen.Benchmark -> HelpTips(
        "Benchmark",
        listOf(
            "Mide la velocidad real de lectura y escritura.",
            "Escribe y borra archivos temporales en el almacenamiento.",
        ),
    )
    is Screen.ImageViewer -> HelpTips(
        "Visor de imágenes",
        listOf(
            "Desliza a izquierda/derecha para cambiar de imagen.",
            "Pellizca para hacer zoom.",
            "Abre la hoja de información para ver los datos EXIF.",
        ),
    )
    is Screen.TextViewer -> HelpTips(
        "Visor de texto",
        listOf(
            "«Cargar más» avanza el búfer en archivos muy grandes.",
            "Busca dentro del documento con el icono de lupa.",
            "Los archivos XML/JSON se pueden formatear y editar.",
        ),
    )
    is Screen.PdfViewer -> HelpTips(
        "Visor PDF",
        listOf(
            "Desliza o usa las flechas para pasar páginas.",
            "Los PDFs se abren en modo lectura local.",
        ),
    )
    is Screen.ArchiveViewer -> HelpTips(
        "Archivo",
        listOf(
            "Explora el contenido del ZIP o TAR sin extraerlo.",
            "«Extraer aquí» descomprime en la carpeta actual.",
        ),
    )
    is Screen.SqliteViewer -> HelpTips(
        "Base de datos",
        listOf(
            "Explora tablas y filas de bases de datos SQLite en solo lectura.",
            "Las columnas se formatean según su tipo.",
        ),
    )
    is Screen.AudioPlayer -> HelpTips(
        "Reproductor",
        listOf(
            "Reproduce la lista de pistas de la carpeta o categoría.",
            "La reproducción se detiene al salir de la app.",
        ),
    )
    is Screen.Wifi -> HelpTips(
        "Redes Wi-Fi",
        listOf(
            "Estado de conexión, velocidad real y velocidad teórica.",
            "«Dispositivos en la red» usa la tabla ARP local (sin Internet).",
            "Toca actualizar o espera a la renovación automática.",
        ),
    )
    is Screen.About -> HelpTips(
        "Acerca de",
        listOf(
            "Versión, garantías y estado de permisos de la app.",
            "APEX es 100 % local: no usa Internet.",
        ),
    )
    is Screen.Trash -> HelpTips(
        "Papelera",
        listOf(
            "Restaura elementos a su ubicación original.",
            "«Vaciar papelera» elimina todo de forma permanente.",
            "Los borrados se guardan por volumen en .apex_trash.",
        ),
    )
    is Screen.BatchRename -> HelpTips(
        "Renombrado por lotes",
        listOf(
            "Aplica plantillas de prefijo, sufijo y numeración.",
            "Previsualiza el resultado antes de confirmar.",
        ),
    )
    is Screen.HexViewer -> HelpTips(
        "Visor hexadecimal",
        listOf(
            "Navega por páginas de 16 bytes con el búfer.",
            "La cabecera muestra los primeros bytes del archivo.",
        ),
    )
}