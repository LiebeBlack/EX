<div align="center">

# ⚡ APEX File Manager

**Ultra-modern, minimalist, Premium Dark-OLED file manager for Android**

Kotlin + Jetpack Compose · CX Explorer class · Extreme speed · Zero background battery · **Zero network · Zero AI · 100% local**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-%237F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-%234285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-%2300E5FF)]()
[![targetSdk](https://img.shields.io/badge/targetSdk-35-%2300E5FF)]()
[![AGP](https://img.shields.io/badge/AGP-8.7.3-%233DDC84?logo=android&logoColor=white)]()
[![Gradle](https://img.shields.io/badge/Gradle-8.10.2-%2302303A?logo=gradle&logoColor=white)]()
[![Automated Releases](https://img.shields.io/badge/Releases-GitHub%20Actions-%232088FF?logo=githubactions&logoColor=white)](.github/workflows/release.yml)
[![Zero Red](https://img.shields.io/badge/Internet-NINGUNA-%23FF2A6D)]()
[![Zero IA](https://img.shields.io/badge/IA-NINGUNA-%23FF2A6D)]()

```
Cero dependencias · Cero red · 100% local
```

</div>

---

## 📚 Documentación

Web formal y minimalista en [`docs/`](docs/index.html) — descripción, funcionalidades, tecnologías, arquitectura, historial de versiones y guía de descarga: **[Abrir documentación](docs/index.html)**.

---

## 📖 Descripción

APEX es un explorador de archivos tipo **CX File Explorer** con estética **Ultra Moderna, Minimalista y Premium Dark OLED**. Está pensado como una herramienta de alto rendimiento: navegación instantánea, herramientas de limpieza 100% algorítmicas y visores nativos — **sin librerías de terceros, sin servicios en segundo plano, sin Internet y sin Inteligencia Artificial**.

## ✨ Características

### 🏠 Dashboard
- Cabecera premium **"APEX"** con `letterSpacing 3.sp`.
- Tarjeta de **almacenamiento** con lectura real de `StatFs` (Usado / Total, GB).
- Herramientas rápidas: **Limpiador Vacío**, **Buscador de Duplicados**, **Filtro APK**, **Analizador de espacio** y **Consola de sistema** (logcat).
- Categorías físicas con recuento en vivo: Imágenes · Vídeos · Audio (MediaStore) y Documentos · Archivos (índice local).

### 🧭 Explorador de archivos
- **Breadcrumb interactivo** (ruta con segmentos táctiles).
- Vista **Lista ⇄ Cuadrícula** con `LazyColumn` / `LazyVerticalGrid` (sin jank).
- Selección múltiple por toque largo: **Copiar · Mover · Renombrar · Eliminar · Compartir · Comprimir · Propiedades**.
- **Selección por rango**: mantén pulsado sobre otro elemento para marcar el bloque completo.
- **Centro de operaciones** flotante con progreso neón, **MB/s en tiempo real**, `N/Total` y **cancelación instantánea**.
- **Resultados honestos**: cada operación resume lo que ocurrió (omitidos, errores con motivo) en vez de fingir éxito.
- **Conflictos de nombre resueltos uno a uno** (Sobrescribir / Omitir / Conservar ambos / Cancelar) al copiar, mover y extraer.
- Orden **ascendente ⇄ descendente** persistido y acceso rápido a **archivos ocultos** desde la barra superior.
- **Inspector de propiedades**: SHA-256 / MD5 bajo demanda, conteo recursivo, permisos R/W/X, MIME exacto, favorito y **“Abrir con…”**.
- Protección anti-recursión: nunca se copia ni mueve una carpeta **dentro de sí misma**.
- **Transferencias mixtas File ⇄ SAF**: pegar entre ambos mundos con progreso real y sin pérdida de datos.
- Soporte **SAF** (USB-OTG / tarjetas) además del acceso total.

### 🧠 Motores 100% algorítmicos (locales)
- Índice en memoria acotado → **búsqueda instantánea** sin re-escaneos.
- Motor regex de categorización por extensiones (precompilado, O(1) por nombre).
- **Duplicados en 2 fases**: agrupación por tamaño exacto → SHA-256 solo sobre grupos coincidentes.
- I/O asíncrono: todo en `Dispatchers.IO`, estado vía `StateFlow`, progreso vía `Flow`.

### 🖥️ Visores integrados (zero-dependency)
| Visor | Motor |
|---|---|
| Imagen (zoom, pan, **rotación**, doble toque) | `pointerInput` nativo + Coil |
| Texto / logs (streaming, memoria acotada, auto-encoding) | `BufferedReader` |
| Texto — **búsqueda en el archivo** (coincidencias resaltadas, saltos ↑/↓) | ventanas + scan secuencial |
| **Audio** (reproductor OLED: play/pausa, seek, anterior/siguiente) | `android.media.MediaPlayer` (solo primer plano) |
| PDF (páginas perezosas, caché LRU) | `android.graphics.pdf.PdfRenderer` |
| **ZIP / TAR / GZ** (navegación virtual + extracción por entrada o **“Extraer todo”**) | `java.util.zip` + lector TAR propio |
| **SQLite** (.db/.sqlite): tablas y vistas, esquema, conteo, vista previa, **consola SQL de solo lectura** y **exportar CSV** (compartir) | `android.database.sqlite` + copia SAF a caché |
| **XML / JSON** — botón **“Formatear”** (pretty-print validado) dentro del editor | parser propio sin dependencias |

### 📦 Análisis profundo de APK
- **Decodificador nativo de `AndroidManifest.xml` binario** (formato AXML: string pool UTF-8/16, namespaces, atributos tipados) sin `PackageManager`.
- Datos extraídos: paquete, versión, SDK min/target/compile, debuggable, permisos, funciones y componentes (activity/service/receiver/provider) con nombres resueltos.
- Contenedores **.xapk / .apks / .apkm**: re-parseo del APK base y listado de splits (al abrirlos, la app guía al Filtro APK).
- **Icono del lanzador** extraído (PNG/WebP, mejor densidad) y **XML decodificado** navegable en pantalla.
- **Consola de sistema (logcat)**: filtros por nivel y texto, lectura `logcat -d` (requiere READ_LOGS: builds de depuración o ADB).

### 🗺️ Y además
- **Analizador de espacio** tipo *treemap squarified* dibujado en `Canvas` (toca para entrar).
- **Sugerencias inteligentes** en Inicio: los archivos más grandes del índice con apertura directa, y carpetas recientes navegables.
- **Ajustes**: archivos ocultos, orden por defecto, acento neón (presets o **paleta personalizada de 12 colores**) y **benchmark** de almacenamiento real.
- **Pantalla “Acerca de”**: versión, garantías (cero red / cero IA / cero segundo plano) y estado de permisos.
- Herramientas de limpieza con **ubicación de análisis seleccionable** (interno / SD / SAF) por herramienta.
- **Búsqueda global** con filtros por tamaño, fecha y extensión (`*.apk`, `*.pdf`).
- Android 14/15: soporte de **acceso parcial a fotos** (Select photos) con aviso y ampliación guiada.

## ⚡ Rendimiento

| Métrica objetivo | Estrategia |
|---|---|
| **120/90 FPS · Zero jank** | listas perezosas con clave, datos estables, metadatos preformateados, cero `elevation`/blur |
| **RAM en reposo < 25 MB** | sin Room/DB en disco, índice acotado (≤ 150k), visor de texto con ventana acotada, caché PDF LRU de 5 páginas, Coil limitado |
| **Batería en 2º plano = 0%** | **ningún** componente en segundo plano: sin services, sin WorkManager, sin receivers |
| **Cold start < 150 ms** | `Application` vacío, splash de sistema, trabajo pesado diferido tras el primer frame |

> Los valores son objetivos de diseño por especificación; la medición final requiere pruebas en dispositivo real.

## 🔌 Soporte de arquitecturas (x86_64 / ARM64)

El proyecto compila APKs **optimizados por ABI** vía `splits.abi` + `ndk.abiFilters`:

| APK | Arquitectura | Uso |
|---|---|---|
| `*-arm64-v8a.apk` | **ARM64** | Teléfonos y tablets actuales |
| `*-armeabi-v7a.apk` | ARM32 | Dispositivos antiguos |
| `*-x86_64.apk` | **x64** | Emuladores y dispositivos x86_64 |
| `*-universal.apk` | Todas | Distribución simple / sideload |

- R8 **minify + shrinkResources** activos en `release`.
- `useLegacyPackaging = false` → librerías nativas comprimidas en el APK.
- La app es 100% Kotlin hoy; el filtro ABI ya fija el empaquetado para cualquier librería nativa futura.

## 🧰 Requisitos de almacenamiento (Android 11–15+)

| Permiso | Uso |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` (All Files Access) | navegación completa + herramientas recursivas |
| `MediaStore` | categorías Imágenes / Vídeos / Audio y miniaturas |
| `READ_MEDIA_*` (13+) / `READ_EXTERNAL_STORAGE` (≤12) | permisos multimedia |
| `ACTION_OPEN_DOCUMENT_TREE` (SAF) | USB-OTG + modo alternativo sin acceso total |

## 📦 Instalación

### Desde GitHub Releases (recomendado)
1. Descarga el APK de tu arquitectura (`arm64-v8a`, `x86_64`…) o el `-universal`.
2. Verifica el checksum: `sha256sum -c SHA256SUMS.txt`.
3. Instala con permiso de *orígenes desconocidos* y concede **Acceso a todos los archivos** al primer arranque.

### Compilar desde el código
Requisitos: **JDK 17** + **Android Studio Koala o superior**.

```bash
./gradlew assembleDebug            # APK de depuración
./gradlew assembleRelease          # APK release (firmado con debug si no hay keystore)
./gradlew testDebugUnitTest        # tests unitarios JVM
```

Sin `gradlew`, abre la carpeta en Android Studio: regenera el wrapper desde `gradle-wrapper.properties`.

## 🤖 Automatización — GitHub Actions

Cada **push a `main`** ejecuta [`.github/workflows/release.yml`](.github/workflows/release.yml) y publica automáticamente una **GitHub Release**:

1. Unit tests (`testReleaseUnitTest`).
2. APKs release por ABI + universal (R8 + shrink).
3. `SHA256SUMS.txt` con checksums.
4. Notas de versión generadas desde `git log` (cambios desde la última release).
5. Release etiquetada `v1.0.<run_number>` con todos los artefactos.

### Firma estable (opcional pero recomendada)
Para que las actualizaciones funcionen entre releases, configura dos secretos en el repositorio:

```bash
# 1. Genera tu keystore (una sola vez)
keytool -genkeypair -v -keystore release.keystore -alias apex \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass TU_PASSWORD -dname "CN=APEX, O=APEX"

# 2. Súbelo codificado en base64 como secreto KEYSTORE_BASE64
base64 -w0 release.keystore | pbcopy   # macOS
# o en Linux: base64 -w0 release.keystore

# 3. (Opcional) Añade el secreto KEYSTORE_PASSWORD
```

Sin secretos, el pipeline genera una clave efímera por build (APKs instalables, pero sin firma estable entre versiones).

## 🧪 Tests

Tests puros JVM (sin emulador):

```bash
./gradlew testDebugUnitTest
```

`CategoryEngineTest` · `SizeFormatterTest` · `DateFormatterTest` · `DuplicateFinderTest` · `TarReaderTest` · `TreemapLayoutTest` · `SearchFilterTest` · `TransferGuardTest` · `SortersTest` · `OpResultTest` · `DuplicateAlgorithmTest` · `MemoryIndexTest` · `CategoryEngineCompoundTest`

## 🗂️ Estructura

```
.
├─ .github/workflows/release.yml   ← pipeline automatizado
├─ gradle/libs.versions.toml       ← catálogo de versiones
└─ app/src/main/java/com/apex/files
   ├─ core/        AppContainer (DI manual), ajustes, hashing, progreso
   ├─ data/        modelo, FsRepository (router File/SAF), índice, regex, TAR, MediaStore…
   ├─ tools/       Limpiador, Duplicados (SHA-256), APK, Treemap, Benchmark
   └─ ui/          tema OLED, componentes, 18 pantallas + ViewModels
```

## 🛡️ Garantías

- **Sin permiso `INTERNET`** — la app no puede hacer llamadas de red.
- **Sin Firebase, ML Kit, TensorFlow, ONNX** ni ninguna IA.
- **Sin servicios en segundo plano, WorkManager ni receivers** → batería 0% en reposo.
- UI en **español**; código y comentarios en inglés.

---

<div align="center">

**Hecho con ♥ en OLED puro `#000000`** — acento `#00E5FF` · bordes `#1E1E28`

</div>
