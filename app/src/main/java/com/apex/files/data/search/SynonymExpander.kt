package com.apex.files.data.search

/**
 * Small curated Spanish/English synonym clusters. Expanding a query with
 * these groups lets "facturas" find files containing "recibo", "invoice",
 * "boleta", etc., without shipping a neural model. Pure JVM.
 */
object SynonymExpander {

    private val CLUSTERS: List<Set<String>> = listOf(
        // Receipts / invoices
        setOf(
            "factura", "facturas", "facturacion", "recibo", "recibos", "boleta", "boletas",
            "comprobante", "comprobantes", "invoice", "invoices", "receipt", "receipts",
            "ticket", "tickets", "abono", "abonos", "nif", "cif", "iva",
        ),
        setOf("pago", "pagos", "payment", "payments", "cobro", "cobros", "transferencia", "transferencias"),
        // Photos / images
        setOf(
            "foto", "fotos", "fotografia", "fotografias", "imagen", "imagenes", "photo",
            "photos", "picture", "pictures", "img", "image", "images", "captura", "capturas",
            "pantallazo", "pantallazos", "screenshot", "screenshots",
        ),
        // Videos
        setOf("video", "videos", "clip", "clips", "filmacion", "filmaciones", "grabacion", "grabaciones", "videoclip"),
        // Cats / memes
        setOf("gato", "gatos", "cat", "cats", "gatico", "gaticos", "minino", "mininos"),
        setOf("meme", "memes"),
        // Work / office
        setOf("trabajo", "trabajos", "work", "empleo", "oficina", "oficinas", "job", "jobs", "laboral"),
        setOf("cv", "curriculum", "curriculo", "resume", "resumes", "hojadevida", "hojasdevida", "hoja de vida"),
        setOf("informe", "informes", "report", "reports", "reporte", "reportes", "memoria", "memorias"),
        setOf("contrato", "contratos", "contract", "contracts", "acuerdo", "acuerdos"),
        setOf("presupuesto", "presupuestos", "budget", "budgets", "cotizacion", "cotizaciones", "quote", "quotes"),
        setOf("proyecto", "proyectos", "project", "projects", "propuesta", "propuestas"),
        setOf("firmado", "firmada", "firmados", "firmadas", "firma", "firmas", "firmar", "signed", "signature", "signatures"),
        // Documents / code
        setOf("documento", "documentos", "document", "documents", "doc", "docs"),
        setOf("codigo", "codigos", "code", "source", "fuente", "script", "scripts"),
        // Downloads / installers
        setOf("descarga", "descargas", "download", "downloads", "descargado", "descargada", "descargados"),
        setOf("instalador", "instaladores", "installer", "installers", "apk", "apks"),
        // Banking / statements
        setOf(
            "estado", "estados", "statement", "statements", "extracto", "extractos",
            "cuenta", "cuentas", "account", "accounts", "banco", "bancos", "bank", "banks",
        ),
        setOf("fiscal", "fiscales", "tax", "impuesto", "impuestos", "declaracion", "declaraciones", "renta"),
        // Time
        setOf("mensual", "mensuales", "monthly", "mes", "meses", "month", "months"),
        setOf("semanal", "semanales", "weekly", "semana", "semanas", "week", "weeks"),
        // Scans
        setOf(
            "escaneado", "escaneada", "escaneados", "escaneadas", "scan", "scans",
            "escaneo", "escaner", "escaneos", "scanner", "scanning",
        ),
        // Engineering
        setOf("ingenieria", "engineering", "plano", "planos", "blueprint", "blueprints", "diseno", "disenos"),
    )

    private val memberToCluster: Map<String, Set<String>> = buildMap {
        for (cluster in CLUSTERS) {
            for (member in cluster) put(member, cluster)
        }
    }

    /**
     * Expands [tokens] into a term -> weight map. Original tokens weigh 1.0,
     * synonym-cluster members 0.6.
     */
    fun expand(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (token in tokens) {
            out[token] = 1.0
            val cluster = memberToCluster[token] ?: memberToCluster[token.singular()] ?: continue
            for (member in cluster) {
                if (member != token && member !in out) out[member] = 0.6
            }
        }
        return out
    }

    private fun String.singular(): String =
        if (length > 3 && endsWith("s") && !endsWith("ss")) dropLast(1) else this
}