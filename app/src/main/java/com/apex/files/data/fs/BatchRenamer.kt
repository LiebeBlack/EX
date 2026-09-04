package com.apex.files.data.fs

/**
 * Pure batch-rename planner (zero Android dependencies, fully unit-testable).
 *
 * Given the current names and a set of transformations it computes the new
 * names without touching the filesystem, reporting validation errors instead
 * of guessing. The caller (a ViewModel) executes the resulting plan through
 * [com.apex.files.data.fs.FsRepository.rename].
 *
 * Transformations are applied in order:
 *  1. find/replace on the stem (the part before the extension),
 *  2. prefix + suffix around the stem,
 *  3. optional renumbering (before the extension, `name_01.ext`).
 */
object BatchRenamer {

    /** One planned rename. [from] equals [to] for entries left unchanged. */
    data class PlanItem(val from: String, val to: String) {
        val changed: Boolean get() = from != to
    }

    data class Plan(
        val items: List<PlanItem>,
        val errors: List<String>,
    ) {
        val changes: Int get() = items.count { it.changed }
        val ok: Boolean get() = errors.isEmpty()
    }

    data class Options(
        val find: String = "",
        val replace: String = "",
        val prefix: String = "",
        val suffix: String = "",
        /** When true, names are renumbered `stem_<n><ext>` starting at [start]. */
        val renumber: Boolean = false,
        val start: Int = 1,
        /** Zero-padding width of the counter ("1" → `_1`, "2" → `_01`). */
        val digits: Int = 2,
        val separator: String = "_",
    )

    private val INVALID_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun plan(names: List<String>, options: Options): Plan {
        val items = ArrayList<PlanItem>(names.size)
        val errors = ArrayList<String>()
        val seen = HashSet<String>()

        var counter = options.start
        for (name in names) {
            if (name.isEmpty()) {
                errors.add("Hay un elemento sin nombre")
                continue
            }
            val (stem0, ext) = split(name)
            var stem = stem0

            if (options.find.isNotEmpty()) {
                stem = stem.replace(options.find, options.replace)
            }
            stem = options.prefix + stem + options.suffix

            var candidate = if (options.renumber) {
                val number = counter
                counter++
                val padded = number.toString().padStart(options.digits.coerceAtLeast(1), '0')
                "$stem${options.separator}$padded"
            } else {
                stem
            }
            candidate += ext

            val problem = when {
                candidate.isEmpty() -> "«$name» → nombre vacío tras aplicar las reglas"
                candidate.length > 255 -> "«$name» → el nombre resultante es demasiado largo"
                candidate.any { it in INVALID_CHARS } -> "«$name» → contiene caracteres no válidos"
                seen.contains(candidate) -> "«$name» → choca con otro nombre del lote ($candidate)"
                else -> null
            }
            if (problem != null) {
                errors.add(problem)
                // Keep the item (unchanged) so the caller can still act on the
                // rest of the batch; the invalid one is simply not renamed.
                items.add(PlanItem(name, name))
            } else {
                seen.add(candidate)
                items.add(PlanItem(name, candidate))
            }
        }
        return Plan(items, errors)
    }

    /** Splits a file name into (stem, extension including the dot). */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        // Hidden files (".gitignore") and dot-prefixed stems keep the whole
        // name as stem; "file." keeps an empty extension.
        return if (dot > 0 && !name.startsWith(".")) {
            name.substring(0, dot) to name.substring(dot)
        } else {
            name to ""
        }
    }
}