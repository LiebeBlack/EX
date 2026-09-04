/* APEX File Manager — documentation interactivity.
   Vanilla JS, no dependencies, safe under file:// and any hosting base. */

(function () {
  "use strict";

  /* ------------------------------------------------------------ theme */

  var THEME_KEY = "apex-docs-theme";
  var root = document.documentElement;
  var button = document.getElementById("theme-toggle");
  var footerTheme = document.getElementById("footer-theme");

  function applyTheme(theme) {
    root.setAttribute("data-theme", theme);
    try { localStorage.setItem(THEME_KEY, theme); } catch (e) { /* ignore */ }
    if (footerTheme) {
      var label = theme === "auto" ? "tema automático"
        : theme === "light" ? "tema claro" : "tema oscuro";
      footerTheme.textContent = label;
    }
  }

  var initial = "auto";
  try {
    var saved = localStorage.getItem(THEME_KEY);
    if (saved === "light" || saved === "dark" || saved === "auto") initial = saved;
  } catch (e) { /* ignore */ }
  applyTheme(initial);

  if (button) {
    var order = ["auto", "light", "dark"];
    button.addEventListener("click", function () {
      var current = root.getAttribute("data-theme") || "auto";
      var next = order[(order.indexOf(current) + 1) % order.length];
      applyTheme(next);
    });
  }

  /* ------------------------------------------------------- version log */
  /* Most recent first. Source: git history of LiebeBlack/EX. */

  var VERSIONS = [
    {
      tag: "v1.0.x",
      what: "Revisión integral: seguridad de datos, conflictos y reproductor de audio",
      date: "2026-09-04",
      ref: "local · working tree",
      body: [
        "Protección anti-recursión al copiar/mover una carpeta dentro de sí misma, en acceso total y SAF.",
        "Transferencias mixtas File ⇄ SAF y movimiento seguro: el origen solo se borra si la copia terminó sin errores.",
        "Resolución interactiva de conflictos de nombre (sobrescribir / omitir / conservar ambos / cancelar) en copiar, mover y extraer, incluido “Extraer todo”.",
        "Las operaciones informan resultados reales (archivos, omitidos y errores) y las selecciones de copiar/mover ya no se pierden al navegar al destino.",
        "Nuevo reproductor de audio OLED en primer plano, búsqueda de texto dentro de los visores y sugerencias de archivos grandes en Inicio.",
        "Orden ascendente/descendente persistido, selección por rango, acceso rápido a ocultos, “Abrir con…”, raíz seleccionable en herramientas y paleta de acento personalizada.",
        "Nueva pantalla “Acerca de”, soporte del acceso parcial a fotos de Android 14+ y nuevos tests unitarios."
      ]
    },
    {
      tag: "main",
      what: "Pipeline «Code Quality»",
      date: "2026-09-03",
      ref: "8172645",
      body: [
        "Nuevo flujo de GitHub Actions que compila todas las variantes de Kotlin (debug + release, aplicación + tests).",
        "Ejecuta todos los tests unitarios en ambos tipos de build.",
        "Analiza el proyecto con Android Lint completo y publica el informe como artefacto.",
        "Sirve de red de seguridad previa al pipeline de release en main."
      ]
    },
    {
      tag: "v1.0.x",
      what: "Corrección del analizador de espacio (treemap)",
      date: "2026-09-03",
      ref: "72573c0 · a44f8e5 · 2010314",
      body: [
        "Reparado el algoritmo squarified: grosor de banda según la dimensión real y avance correcto del origen.",
        "Los bordes de los rectángulos encadenan sus coordenadas para evitar solapes por coma flotante.",
        "Actualizados y verificados los tests unitarios de disposición (TreemapLayoutTest)."
      ]
    },
    {
      tag: "v1.0.x",
      what: "Operaciones suspendibles y correcciones de interfaz",
      date: "2026-09-03",
      ref: "8246b6e",
      body: [
        "Las operaciones de archivo (copiar, mover, eliminar, comprimir) se ejecutan como funciones suspendibles con progreso en Flow.",
        "Corregida la cascada de errores de compilación de la interfaz Compose (tema, iconos, imports).",
        "Añadida la dependencia androidx.documentfile, necesaria para el soporte SAF."
      ]
    },
    {
      tag: "v1.0.0",
      what: "Versión inicial de la aplicación",
      date: "2026-09-03",
      ref: "69a8801",
      body: [
        "Explorador con vista lista/cuadrícula, selección múltiple y centro de operaciones.",
        "Categorías (MediaStore + índice local), búsqueda con filtros, analizador de espacio y herramientas de limpieza.",
        "Visores integrados de imagen, texto, PDF y archivos ZIP/TAR/GZ.",
        "Tema OLED oscuro con acento configurable y permisos gestionados por versión de Android."
      ]
    }
  ];

  var vlist = document.getElementById("version-list");

  function renderVersions() {
    if (!vlist) return;
    var html = "";
    for (var i = 0; i < VERSIONS.length; i++) {
      var v = VERSIONS[i];
      var bullets = "";
      for (var b = 0; b < v.body.length; b++) {
        bullets += "<li>" + v.body[b] + "</li>";
      }
      html +=
        '<details class="vitem"' + (i === 0 ? ' open' : '') + ">" +
          "<summary>" +
            '<span class="vtag">' + v.tag + "</span>" +
            '<span class="vwhat">' + v.what + "</span>" +
            '<span class="vmeta">' + v.date + " · " + v.ref + "</span>" +
          "</summary>" +
          '<div class="vbody"><ul>' + bullets + "</ul></div>" +
        "</details>";
    }
    vlist.innerHTML = html;
  }

  renderVersions();

  /* ------------------------------------------------------ ABI builder */

  var abiSelect = document.getElementById("abi-select");
  var nameOut = document.getElementById("artifact-name");
  var urlOut = document.getElementById("artifact-url");
  var BASE = "https://github.com/LiebeBlack/EX/releases";

  function tagPart() {
    return "latest";
  }

  function updateArtifact() {
    if (!abiSelect || !nameOut || !urlOut) return;
    var abi = abiSelect.value;
    var tag = tagPart();
    if (abi === "universal") {
      nameOut.textContent = "APEX-" + tag + "-universal.apk";
    } else {
      nameOut.textContent = "APEX-" + tag + "-" + abi + ".apk";
    }
    urlOut.textContent = tag === "latest" ? BASE + "/latest" : BASE + "/download/" + tag + "/" + nameOut.textContent;
  }

  if (abiSelect) abiSelect.addEventListener("change", updateArtifact);
  updateArtifact();

  /* ------------------------------------------------------------ copy */

  function copyText(text, trigger) {
    function done(ok) {
      if (!trigger) return;
      var prev = trigger.textContent;
      trigger.textContent = ok ? "Copiado" : "Error";
      trigger.classList.toggle("ok", ok);
      setTimeout(function () {
        trigger.textContent = prev;
        trigger.classList.toggle("ok", false);
      }, 1400);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); });
    } else {
      var ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      var ok = false;
      try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
      document.body.removeChild(ta);
      done(ok);
    }
  }

  document.addEventListener("click", function (event) {
    var btn = event.target.closest ? event.target.closest("[data-copy]") : null;
    if (!btn) return;
    var target = document.querySelector(btn.getAttribute("data-copy"));
    if (target) copyText(target.textContent.trim(), btn);
  });
})();
