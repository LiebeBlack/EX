package com.apex.files.data.fs

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.apex.files.data.model.FileNode
import java.io.File
import java.io.IOException

/**
 * Read-only analyzer for SQLite databases. Uses the platform SQLite engine
 * (no extra dependencies): SAF-backed files are first copied into the app
 * cache, then opened with OPEN_READONLY. Everything is converted to display
 * strings — BLOBs become hex prefixes — so screens stay dumb.
 */
class SqliteRepository(private val context: Context, private val fs: FsRepository) {

    val SQLITE_EXTS: Set<String> = setOf("db", "sqlite", "sqlite3", "db3", "s3db")

    /** A table plus its schema and a bounded row preview. */
    data class TableInfo(
        val name: String,
        val kind: String, // table | view
        val columns: List<Column>,
        val rowCount: Long?,
        val preview: QueryResult?,
    )

    data class Column(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    /** Result of a query or preview: column names + bounded rows as text. */
    data class QueryResult(
        val columns: List<String>,
        val rows: List<List<String?>>,
        val limited: Boolean = false,
        val error: String? = null,
    ) {
        val isEmpty: Boolean get() = rows.isEmpty() && error == null
    }

    class Handle internal constructor(
        val db: SQLiteDatabase?,
        val tempFile: File?,
        val error: String?,
    ) {
        val isOpen: Boolean get() = db != null

        fun close() {
            runCatching { db?.close() }
            tempFile?.delete()
        }
    }

    private val PREVIEW_ROWS = 120
    private val QUERY_ROWS = 300

    /** Opens [node] read-only, staging SAF copies into the cache dir. */
    fun open(node: FileNode): Handle {
        val temp = if (node.uri != null) {
            val target = File(context.cacheDir, "apex_db_${node.name.hashCode()}_${System.currentTimeMillis()}.db")
            try {
                val input = fs.openInputStream(node) ?: throw IOException("No se pudo leer la base de datos")
                input.use { ins -> target.outputStream().use { outs -> ins.copyTo(outs) } }
                target
            } catch (e: Exception) {
                return Handle(null, target, "No se pudo copiar la base de datos: ${e.message ?: "error"}")
            }
        } else {
            File(node.path)
        }
        if (!temp.exists() || !temp.isFile) {
            return Handle(null, temp, "El archivo no existe")
        }
        return try {
            val db = SQLiteDatabase.openDatabase(
                temp.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            Handle(db, temp, null)
        } catch (e: Exception) {
            Handle(null, temp, "No es una base de datos SQLite válida: ${e.message ?: "error"}")
        }
    }

    fun listObjects(db: SQLiteDatabase): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        db.rawQuery(
            "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val type = c.getString(1) ?: "table"
                out.add(name to type)
            }
        }
        return out
    }

    fun tableInfo(db: SQLiteDatabase, table: String, kind: String): TableInfo {
        val columns = ArrayList<Column>()
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            while (c.moveToNext()) {
                columns.add(
                    Column(
                        name = c.getString(1) ?: "?",
                        type = c.getString(2) ?: "",
                        notNull = c.getInt(3) != 0,
                        pk = c.getInt(5),
                    )
                )
            }
        }
        val count = if (kind == "table") {
            runCatching { db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { if (it.moveToFirst()) it.getLong(0) else null } }
                .getOrNull()
        } else null
        val preview = preview(db, table)
        return TableInfo(table, kind, columns, count, preview)
    }

    fun preview(db: SQLiteDatabase, table: String): QueryResult =
        runQuery(db, "SELECT * FROM `$table`", limitedAt = PREVIEW_ROWS)

    /** Executes an arbitrary read query with a hard row cap. */
    fun runQuery(db: SQLiteDatabase, sql: String, limitedAt: Int = QUERY_ROWS): QueryResult {
        val clean = sql.trim().trimEnd(';')
        if (clean.isEmpty()) return QueryResult(emptyList(), emptyList(), error = "Consulta vacía")
        if (!looksReadOnly(clean)) {
            return QueryResult(emptyList(), emptyList(), error = "Solo se permiten consultas de lectura (SELECT/PRAGMA/EXPLAIN)")
        }
        return try {
            // PRAGMA statements reject a trailing LIMIT clause; only SELECT/
            // WITH/VALUES get one (row counting is capped in the loop anyway).
            val limitedSql = if (needsLimit(clean)) "$clean LIMIT ${limitedAt + 1}" else clean
            db.rawQuery(limitedSql, null).use { cursor ->
                if (cursor.columnCount == 0) return@use QueryResult(emptyList(), emptyList())
                val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                val rows = ArrayList<List<String?>>()
                var limited = false
                while (cursor.moveToNext()) {
                    if (rows.size >= limitedAt) {
                        limited = true
                        break
                    }
                    rows.add((0 until cursor.columnCount).map { cellText(cursor, it) })
                }
                QueryResult(columns, rows, limited = limited)
            }
        } catch (e: Exception) {
            QueryResult(emptyList(), emptyList(), error = e.message ?: "Error en la consulta")
        }
    }

    /** True when the statement cannot mutate the database. */
    private fun looksReadOnly(sql: String): Boolean {
        val head = headOf(sql)
        return head == "select" || head == "pragma" || head == "explain" ||
            head == "with" || head == "values"
    }

    private fun needsLimit(sql: String): Boolean {
        val head = headOf(sql)
        return head == "select" || head == "with" || head == "values" || head == "explain"
    }

    private fun headOf(sql: String): String =
        sql.lowercase().trimStart('(', ' ', '\n', '\r').substringBefore(' ')

    private fun cellText(cursor: Cursor, index: Int): String? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> {
            val blob = cursor.getBlob(index)
            if (blob == null) null
            else {
                val head = blob.take(32)
                val hex = head.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                if (blob.size > 32) "0x$hex… (+${blob.size - 32} B)" else "0x$hex"
            }
        }
        else -> cursor.getString(index)
    }
}
