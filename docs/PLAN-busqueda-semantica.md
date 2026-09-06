# Plan — Módulo opcional: Búsqueda Semántica, OCR, Auto-Categorización y Limpieza Inteligente

**Estado:** aprobado e implementado.
El módulo es **opcional y activable/desactivable**: un interruptor maestro en Ajustes, **OFF por defecto**,
para que el APK se mantenga ligero y nada se indexe hasta que el usuario lo active.

## Restricciones del proyecto (invariantes)
- **100% offline**: no se añade el permiso `INTERNET`; toda la inferencia es on-device.
- Arquitectura existente: DI manual (`AppContainer`), ajustes con `SharedPreferences` + `StateFlow`,
  núcleo JVM puro testeable, persistencia por archivos (sin Room/WorkManager).
- Con el interruptor OFF el comportamiento es exactamente el actual (búsqueda por nombre, sin OCR,
  sin indexado extra).

## 1. Dependencias
- `com.google.mlkit:text-recognition` — modelo latino incluido en el APK (cubre español), funciona sin
  Google Play Services, OCR on-device para imágenes y páginas escaneadas. APK ≈ +9–15 MB.
- `com.tom-roush:pdfbox-android` — extrae la capa de texto nativa de los PDF. APK ≈ +8–12 MB.
- Reglas ProGuard para pdfbox-android (keep) y avisos suprimidos.

## 2. Ajustes (interruptores)
- `semanticSearchEnabled` (**maestro**, OFF por defecto) — activa OCR, búsqueda semántica,
  carpetas inteligentes y limpieza inteligente.
- `ocrEnabled` (ON por defecto, visible cuando el maestro está activo) — indexa imágenes y PDFs escaneados.
- `smartGroupsEnabled` (ON por defecto) — fila "Carpetas inteligentes" en Inicio.
- Pantalla de Ajustes con la sección nueva y explicación de costes (APK/RAM/batería).

## 3. Núcleo JVM puro (`data/search/`, unit-testable)
- `SpanishNormalizer` — minúsculas, sin acentos, eliminación de stopwords en español.
- `SynonymExpander` — tablas de sinónimos español/inglés (factura↔recibo↔boleta↔invoice…).
- `RelativeDateParser` — "mes pasado / semana pasada / ayer / este año" → `SearchFilters.DateRange`;
  también tamaños ("grandes" → `SizeBand.GIANT`).
- `Bm25Ranker` — ranking BM25 sobre nombre + extensión + etiqueta de categoría + texto OCR.
- `SmartClassifier` — grupos semánticos: TRABAJO, COMPROBANTE, CODIGO, TEMPORAL, APK, MULTIMEDIA
  (extensión + nombre + texto), con prioridad de reglas.
- `SemanticSearch` — orquesta: filtros duros (tamaño/fecha/ext/categoría/grupo) → pre-filtro
  barato por `contains` (nombre + texto normalizado) → BM25 sobre los candidatos → fallback
  a la búsqueda clásica si no hay coincidencias.
- Tests unitarios: normalizador, sinónimos, fechas relativas, BM25, clasificador.

## 4. Índice de contenido (`ContentIndex`)
- Persistencia TSV en app-private (`content_index_v2.txt`, escritura atómica igual que `IndexStore`):
  `path<TAB>lastModified<TAB>texto-normalizado`.
- Límites: 150 000 entradas, 4 KB de texto por archivo, presupuesto total de texto acotado.
- Indexado incremental por bloques (500 archivos/ejecución) desde Inicio cuando el maestro está ON;
  solo re-procesa archivos cuyo `lastModified` cambió. Texto plano primero, luego imágenes, luego PDFs.

## 5. OCR y PDF
- `OcrEngine` — ML Kit `TextRecognizer` (latino); reduce imágenes >2048 px antes de reconocer;
  procesa secuencialmente (el recognizer no es thread-safe); nunca rompe si falla (texto vacío).
- `PdfTextExtractor` — pdfbox-android para la capa de texto; si el PDF es escaneado y el OCR está ON,
  renderiza las primeras ~10 páginas con el renderer de pdfbox y aplica OCR.

## 6. Búsqueda semántica en la UI
- `SearchViewModel`/`SearchScreen`: cuando el maestro está ON, la consulta pasa por `SemanticSearch`;
  fila nueva de chips de grupo (Comprobantes, Trabajo, Código, Temporales, APKs, Multimedia);
  la línea de estado muestra el tamaño del índice. Con el interruptor OFF, UI idéntica a hoy.
- El botón "Reindexar" además reconstruye el índice semántico.

## 7. Auto-categorización (carpetas virtuales)
- Nueva pantalla `SmartGroupsScreen` (ruta `Screen.SmartGroups`): tarjetas por grupo con recuento y
  tamaño total; al abrir un grupo, cuadrícula de archivos (reutiliza el aspecto de Categorías).
- Acción por grupo: **"Mover a carpeta"** → confirmación → `FsRepository.move` (motor con conflictos)
  a `Descargas/Apex-Organizadas/<Grupo>`. Nunca mueve nada automáticamente.
- Fila "Carpetas inteligentes" en Inicio cuando `smartGroupsEnabled`.

## 8. Limpieza Inteligente (residuos/cachés/temporales)
- `JunkAnalyzer` (`data/tools/`, patrón Flow como `EmptyCleaner`):
  - Huérfanos: `/Android/data/<pkg>` y `/Android/obb/<pkg>` donde el paquete no está instalado
    (comparación con `PackageManager`).
  - Cachés: `/Android/data/*/cache`, `/Android/obb`.
  - Temporales: `*.tmp`, `*.part`, `*.crdownload`, `*.download`.
  - **Nunca toca**: `/system`, `/data`, raíces de volumen, `.apex_trash` ni nada fuera de las reglas.
- Nueva pantalla `CleanupScreen` (ruta `Screen.Cleanup`): agrupada por tipo, selección múltiple,
  "Eliminar (n)" con confirmación; el borrado respeta la Papelera (`trashEnabled`) igual que el explorador.

## 9. Motor de copiado/movido
- **Copia paralela**: en `copyFileTree` los archivos de un directorio se copian con 4 workers
  (semáforo de corrutinas); los subdirectorios se recorren secuencialmente (orden de conflictos
  determinista). `OpAccumulator` y el sink de progreso se hacen thread-safe.
- La ruta mixta File⇄SAF se mantiene secuencial (crear `DocumentFile` concurrentemente es arriesgado).
- **Rescan de MediaStore**: al terminar copiar/mover/comprimir en rutas File, se notifica
  `MediaScannerConnection` para que Galería/Descargas vean los archivos al instante.

## 10. Compatibilidad Scoped Storage
- `Category.DOWNLOADS` + consulta `MediaStore.Downloads` (API 29+) para recuentos/listado rápidos;
  chip "Descargas" en Inicio en API 29+.
- Matriz de permisos (ya presente y verificada):
  - API 26–28: `READ/WRITE_EXTERNAL_STORAGE`.
  - API 29–30: `requestLegacyExternalStorage="true"` + All Files.
  - API 31+: `MANAGE_EXTERNAL_STORAGE` (All Files Access) como backend principal.
  - Fallback SAF/DocumentFile (pantalla de permisos) + MediaStore para colecciones multimedia.

## 11. Documentación
- Este documento, actualizado con el resultado de la implementación; `docs/index.html` refleja
  las nuevas capacidades.

## Orden de implementación
1. `docs/PLAN-busqueda-semantica.md`.
2. Dependencias + interruptores de Ajustes (+ UI).
3. Núcleo JVM: normalizador/sinónimos/fechas/BM25 + `SmartClassifier` + tests.
4. `ContentIndex` + indexado incremental.
5. `OcrEngine` (ML Kit) + `PdfTextExtractor` (pdfbox + fallback OCR).
6. Integración de búsqueda semántica (ViewModel + Screen).
7. `SmartGroupsScreen` + entrada en Inicio.
8. `JunkAnalyzer` + `CleanupScreen`.
9. Copia paralela + rescan MediaStore.
10. `MediaStore.Downloads` + verificación Scoped Storage.
11. `./gradlew test` + `./gradlew assembleDebug`.

## Riesgos
- El APK crece ≈ +20–30 MB (modelo ML Kit + pdfbox) — mitigado por el interruptor OFF por defecto
  y los splits por ABI ya configurados.
- El OCR consume batería/RAM — mitigado por bloques, salto de archivos sin cambios e interruptores.
- El borrado de residuos es solo con confirmación del usuario; exclusiones fijas y testeables.