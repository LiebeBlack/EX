/* APEX File Manager — documentation interactivity.
   Vanilla JS, no dependencies, safe under file:// and any hosting base. */

(function () {
  "use strict";

  /* ------------------------------------------------------------ utilities */

  function isVisible(el) {
    if (!el) return false;
    var rect = el.getBoundingClientRect();
    return rect.top < window.innerHeight && rect.bottom > 0 && rect.width > 0 && rect.height > 0;
  }

  function setHidden(el, hidden) {
    if (!el) return;
    if (hidden) el.setAttribute("hidden", "");
    else el.removeAttribute("hidden");
  }

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

  var vlist = document.getElementById("version-list");
  var versionNote = document.getElementById("version-note");

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function orderedByDate(a, b) {
    var da = a.date ? new Date(a.date).getTime() : 0;
    var db = b.date ? new Date(b.date).getTime() : 0;
    if (da !== db) return db - da;
    if (a.ref && b.ref) return String(a.ref).localeCompare(String(b.ref));
    return 0;
  }

  var VERSIONS = [
    {
      tag: "v1.0.x",
      what: "Revisión integral: seguridad de datos, conflictos, audio y documentación",
      date: "2026-09-06",
      ref: "local · docs/",
      body: [
        "Protección anti-recursión al copiar/mover una carpeta dentro de sí misma, en acceso total y SAF.",
        "Transferencias mixtas File ⇄ SAF y movimiento seguro: el origen solo se borra si la copia terminó sin errores.",
        "Resolución interactiva de conflictos de nombre (sobrescribir / omitir / conservar ambos / cancelar) en copiar, mover y extraer, incluido “Extraer todo”.",
        "Las operaciones informan resultados reales (archivos, omitidos y errores) y las selecciones de copiar/mover ya no se pierden al navegar al destino.",
        "Nuevo reproductor de audio OLED en primer plano, búsqueda de texto dentro de los visores y sugerencias de archivos grandes en Inicio.",
        "Orden ascendente/descendente persistido, selección por rango, acceso rápido a ocultos, “Abrir con…”, raíz seleccionable en herramientas y paleta de acento personalizada.",
        "Nueva pantalla “Acerca de”, soporte del acceso parcial a fotos de Android 14+ y nuevos tests unitarios.",
        "Documentación técnica renovada: tono editorial, secciones de búsqueda semántica/OCR y limpieza inteligente, y página de descargas actualizada."
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


  /* ------------------------------------------------------ ABI builder */

  var abiSelect = document.getElementById("abi-select");
  var nameOut = document.getElementById("artifact-name");
  var urlOut = document.getElementById("artifact-url");
  var BASE = "https://github.com/LiebeBlack/EX/releases";
  var HOST = location.host || null;

  function isFileURL() {
    return HOST === null && location.protocol === "file:";
  }
  var HOST = location.host || null;

  function isFileURL() {
    return HOST === null && location.protocol === "file:";
  }

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

    if (isFileURL()) {
      urlOut.setAttribute("data-copy", "");
      urlOut.closest ? urlOut.closest(".output").querySelector("button[data-copy]").disabled = true : null;
    } else {
      if (urlOut.closest) {
        var copyBtn = urlOut.closest(".output").querySelector("button[data-copy]");
        if (copyBtn) copyBtn.disabled = false;
      }
    }
  }

  if (abiSelect) abiSelect.addEventListener("change", updateArtifact);
  updateArtifact();

  /* ------------------------------------------------------ performance hints */

  if (vlist) {
    var details = vlist.querySelectorAll("details");
    details.forEach(function (d, index) {
      d.setAttribute("data-index", String(index));
    });
  }

  if ("loading" in HTMLLinkElement.prototype && /https?:\/\//.test(location.href)) {
    var scripts = document.querySelectorAll("script[src]");
    scripts.forEach(function (s) {
      if (s.getAttribute("defer")) s.setAttribute("loading", "lazy");
    });
  }

  /* ------------------------------------------------------ navrail */

  function highlightedSection() {
    var sections = Array.prototype.slice.call(document.querySelectorAll("main.section, main[id]"));
    var scrollY = window.scrollY + 120;
    for (var i = 0; i < sections.length; i++) {
      var top = sections[i].getBoundingClientRect().top + window.scrollY;
      if (top <= scrollY) {
        var id = sections[i].id;
        if (id) return id;
      }
    }
    return null;
  }

  function applyRailState() {
    var activeId = highlightedSection();
    var anchors = document.querySelectorAll(".navrail a");
    anchors.forEach(function (a) {
      var target = a.getAttribute("href");
      if (!target) return;
      var clean = target.charAt(0) === "#" ? target.slice(1) : target;
      if (clean === activeId) {
        a.setAttribute("aria-current", "true");
        a.querySelector("strong").style.color = "var(--ink)";
        a.querySelector("strong").style.fontWeight = "650";
      } else {
        a.removeAttribute("aria-current");
        a.querySelector("strong").style.color = "";
        a.querySelector("strong").style.fontWeight = "";
      }
    });
  }

  if (document.querySelector(".navrail")) {
    applyRailState();
    var ticking = false;
    window.addEventListener("scroll", function () {
      if (!ticking) {
        window.requestAnimationFrame(function () {
          applyRailState();
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });
    window.addEventListener("resize", applyRailState);
  }

  /* ------------------------------------------------------------ copy */

  function copyText(text, trigger) {
    if (!trigger || !text) return;
    function done(ok) {
      var prev = trigger.textContent;
      trigger.textContent = ok ? "Copiado" : "Error";
      trigger.classList.toggle("ok", ok);
      setTimeout(function () {
        trigger.textContent = prev;
        trigger.classList.toggle("ok", false);
      }, 1400);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { done(true); }, function () { done(false); })
        .catch(function () { done(false); });
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
    if (btn) {
      var target = document.querySelector(btn.getAttribute("data-copy"));
      if (target) {
        var text = target.textContent.trim();
        if (text) copyText(text, btn);
      }
      return;
    }

    var railAnchor = event.target.closest ? event.target.closest(".navrail a") : null;
    if (railAnchor) {
      var href = railAnchor.getAttribute("href");
      if (href && href.charAt(0) === "#") {
        var id = href.slice(1);
        var section = document.getElementById(id);
        if (section) {
          var top = section.getBoundingClientRect().top + window.scrollY - 84;
          window.scrollTo({ top: top, behavior: "smooth" });
          history.replaceState(null, "", href);
        }
      }
    }
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") {
      var openDetails = document.querySelector("details[open]");
      if (openDetails) {
        openDetails.removeAttribute("open");
        var summary = openDetails.querySelector("summary");
        if (summary) summary.focus();
        return;
      }

      var rail = document.activeElement;
      if (rail && rail.classList && rail.classList.contains("navrail")) {
        rail.blur();
      }
    }
  });

  document.addEventListener("keydown", function (event) {
    if (event.ctrlKey || event.metaKey) {
      var f = document.activeElement;
      if (f && (f.tagName === "INPUT" || f.tagName === "TEXTAREA" || f.isContentEditable)) return;
    }
  });

  if (document.querySelectorAll("[data-copy]").length) {
    var copyButtons = document.querySelectorAll("[data-copy]");
    copyButtons.forEach(function (b) {
      var label = b.textContent.trim() || (b.getAttribute("data-copy") || "").replace(/^#/, "");
      b.setAttribute("title", label);
      b.setAttribute("type", "button");
      if (!b.getAttribute("aria-label")) b.setAttribute("aria-label", label);
      try { b.setAttribute("data-native", "1"); } catch (e) { /* ignore */ }
    });
  }

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") {
      var openDetails = document.querySelector("details[open]");
      if (openDetails) {
        openDetails.removeAttribute("open");
        var summary = openDetails.querySelector("summary");
        if (summary) summary.focus();
        return;
      }

      var rail = document.activeElement;
      if (rail && rail.classList && rail.classList.contains("navrail")) {
        rail.blur();
      }
    }
  });

  document.addEventListener("focusin", function (event) {
    var focused = event.target;
    if (focused && focused.classList && focused.classList.contains("navrail")) return;
  });

  if (navigator.serviceWorker) {
    try {
      if ("controller" in navigator.serviceWorker) {
        var sw = navigator.serviceWorker.controller;
        if (sw) sw.postMessage({ type: "APEX_DOCS_HINT", payload: "ping" });
      }
    } catch (e) { /* ignore */ }
  }

  try {
    if (window.performance && window.performance.mark) {
      window.performance.mark && window.performance.mark("apex-docs-ready");
    }
  } catch (e) { /* ignore */ }
})();

/* APEX File Manager — documentation footer note.
   This file is intentionally tiny and dependency-free. */
