# Registro de Cambios - NuclearWearable v1.0.0 (Hito Mayor)

**Versión:** 1.0.0 (Compilación 20)  
**Fecha:** 31 de Octubre de 2026  
**Paquete:** `com.nuclear.soundconnect`  
**Firma:** `nuclear_ecosystem.keystore` (Release firmado)

---

## 🚀 Resumen Ejecutivo del Hito v1.0.0

La versión **1.0.0** representa el mayor salto evolutivo en la historia de **NuclearWearable (Sony SoundConnect)**. Tras meses de desarrollo y optimizaciones iterativas, alcanzamos la versión de producción oficial abierta al ecosistema completo de auriculares inalámbricos de Sony:

1. **Soporte Universal Sony (52+ Modelos):** 16 familias de diseño industrial con vectores nativos independientes dibujados a mano respetando la geometría real de cada modelo y su estuche.
2. **Motor de Detección Infalible de 3 Capas:** Identificación con 100% de precisión incluso si el usuario renombró sus audífonos a `"mis audifonos sony"` o cualquier alias personalizado.
3. **Interfaz Dinámica e Inteligente según Hardware (Filtro ANC):** Detección automática de modelos sin Cancelación Activa de Ruido (ej. WF-C500, WH-CH520, WI-C100, LinkBuds Open), ocultando los controles de ruido y modo ambiente para ofrecer una experiencia ultra-minimalista centrada puramente en la batería y conexión.
4. **Catálogo Interactivo con Layout Adaptativo y Responsivo:** 
   - **Modo Horizontal (Landscape):** Arquitectura de 2 columnas donde el panel izquierdo contiene el banner activo y filtros, mientras la lista de modelos ocupa la columna derecha con el 100% de la altura disponible de pantalla, eliminando franjas recortadas.
   - **Modo Vertical (Portrait):** Espaciado ergonómico One UI con apertura a pantalla completa (`skipPartiallyExpanded = true`).
5. **Cero Nombres Truncados y Botón Compacto:** Se cambió el texto del botón de *"Elegir este modelo"* a *"Elegir"* (ES) / *"Select"* (EN), liberando más de 85dp horizontales para que modelos como `WF-1000XM5`, `WH-1000XM5` y `LinkBuds S` quepan completos sin puntos suspensivos (`...`).
6. **Eliminación de Nombres Duplicados:** En familias con un solo modelo (ej. `WF-1000XM6`, `WH-1000XM5`), el subtítulo ya no repite el mismo nombre abajo, sino que muestra la categoría comercial de la serie (ej. *"1000X Flagship"*).
7. **Soporte Bilingüe Nativo al 100% (Español / English):** Nuevo submenú dedicado en hoja modal con selector de idioma instantáneo, traduciendo la totalidad de menús, submenús, ajustes de tema, historial y diálogos del sistema.
8. **Perfeccionamiento Tipográfico y Casing:** Eliminación de mayúsculas agresivas en descripciones y botones (`Cancelación` / `Cancelling` en minúsculas normales; mayúsculas reservadas exclusivamente para nombres de modelos en el Hero y títulos de sección).
9. **Corrección Geométrica del Ícono Desactivado:** Círculo exterior rediseñado a radio 8.2 y centrado exacto en (12, 12), eliminando cualquier corte o aplanamiento en el borde inferior.
10. **Respuesta Inmediata y Prioridad Absoluta en Cambios de Modo (RFCOMM):** Conmutación instantánea de modos ANC / Ambiente / Apagado eliminando colisiones con el sondeo de batería de fondo, tiempos muertos y esperas en cola de socket.
11. **Ícono Oficial Definitivo:** Enfoque puro en el buque insignia Sony WF-1000XM6 en grafito OLED y detalles dorados, sin anillo verde exterior, adaptado a esquinas redondeadas Squircle One UI y circular.

---

## 🛠️ Detalle Técnico de Novedades y Refinamientos

### 1. Layout Responsivo Landscape (2 Columnas) y Expansión Completa
- **Problema previo:** En orientación horizontal (landscape), el encabezado, el banner del modelo activo y los botones de filtro consumían casi 240dp de los ~360dp de alto de pantalla, dejando una "mini franja" de apenas ~70dp donde solo se asomaba el borde de una tarjeta.
- **Solución implementada:**
  - Se detecta la orientación mediante `LocalConfiguration.current.orientation`.
  - En **modo horizontal**, la interfaz cambia automáticamente a una disposición `Row` de dos paneles:
    - **Columna izquierda (310dp de ancho fijo):** Título del catálogo, previsualización del modelo activo y pestañas de filtro (Todos / TWS / Diadema).
    - **Columna derecha (`weight(1f)` y `fillMaxHeight()`):** La lista de modelos (`LazyColumn`) aprovecha el alto completo de la pantalla, permitiendo ver 3 a 4 modelos a la vez y desplazarse con total fluidez.
  - Se activó `skipPartiallyExpanded = true` en `rememberModalBottomSheetState()` para que la hoja modal se expanda al máximo de inmediato sin detenerse a la mitad.

### 2. Eliminación de Truncamiento con Puntos Suspensivos (`WF-1000X...`)
- **Problema previo:** El botón *"Elegir este modelo"* ocupaba casi la mitad del ancho de la tarjeta (~150dp), asfixiando la columna central y provocando que Compose truncara nombres como `WF-1000XM5` a `WF-1000X...`.
- **Solución implementada:**
  - El botón se simplificó a *"Elegir"* (ES) / *"Select"* (EN), con un ancho de ~60dp (exactamente el mismo ancho que el badge *"Activo"*).
  - La tarjeta completa es `clickable`, permitiendo pulsar en cualquier área para seleccionar el modelo.
  - La columna central gana ~85dp de espacio adicional, permitiendo que todos los nombres comerciales y badges se desplieguen en una sola línea holgada y sin puntos suspensivos.

### 3. Eliminación de Textos Redundantes en Modelos Únicos
- **Problema previo:** Para familias de un solo modelo (ej. `WF-1000XM6`), se mostraba el nombre en negrita arriba y exactamente el mismo texto `"WF-1000XM6"` en gris abajo.
- **Solución implementada:**
  - Si `modelsCovered.size == 1` y coincide con `displayName`, el subtítulo muestra `fam.seriesCategory` (ej. *"1000X Flagship"*).
  - Para familias que abarcan múltiples variantes (ej. `WH-1000XM3`, `WF-C500`, `WH-CH720N`), continúa listando todos los modelos cubiertos (`WH-1000XM3, WH-1000XM2, MDR-1000X`).

### 4. Latencia Cero y Prioridad de Conmutación en RFCOMM
- **Preempción del Usuario:** Cuando cambias de modo ANC / Ambiente / Apagado, se cancela de inmediato cualquier corrutina de sondeo de batería de fondo (`activeSyncJob?.cancel()`), liberando el socket RFCOMM sin esperar 2.2 segundos.
- **Inmunidad Acústica (5s):** Se suspende el sondeo pasivo durante los 5 segundos posteriores a una conmutación manual.
- **Espera de ACK y Asentamiento DSP:** Al enviar `SET_NC_ASM`, el cliente espera hasta 200ms la confirmación ACK del firmware de Sony y añade un margen de 80ms antes de cerrar el socket, evitando que el chip quede en estado ocupado.
- **Reintento con Backoff (120ms):** Pausa de 120ms antes del socket de respaldo para prevenir desconexiones transitorias.

### 5. Casing y Tipografía Limpia
- Botón ANC: `Cancelación` (ES) / `Cancelling` (EN) en minúsculas naturales (Sentence Case).
- Subtítulos de submenús en minúsculas normales.
- Mayúsculas reservadas exclusivamente para nombres de modelos en el Hero y títulos de sección.

### 6. Corrección Geométrica en `ic_anc_off.xml`
- Círculo exterior centrado en `(12, 12)` con radio `8.2`, trazo `2.0` y barra diagonal limpia de `(6, 6)` a `(18, 18)`. Círculo 100% visible sin ningún borde cortado en la base.

---

## 🛡️ Nota Técnica sobre Google Play Protect

Al instalar el archivo `.apk` directamente por fuera de Google Play Store (sideloading):
1. **Motivo del aviso:** Google Play Protect muestra una advertencia preventiva de *"Desarrollador desconocido"* para **cualquier aplicación instalada manualmente** cuyo certificado de firma (`nuclear_ecosystem.keystore`) no esté registrado en una cuenta de desarrollador comercial de Google Play Console.
2. **Seguridad verificada:** NuclearWearable es completamente segura y privada:
   - No contiene telemetría invasiva, anuncios ni rastreadores.
   - No solicita permisos de ubicación precisa ni acceso a internet en segundo plano.
   - Únicamente utiliza permisos estándar de Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` con flag `neverForLocation`).
3. **Instalación:** Basta con pulsar *"Más detalles"* -> *"Instalar de todas formas"*.

---

## 📦 Archivos Entregables en Este Paquete

| Archivo | Tamaño | Descripción |
| :--- | :--- | :--- |
| **`NuclearWearable_v1.0.0_release.apk`** | 40.1 MB | APK final de producción v1.0.0 (Build 20) firmada y optimizada para subir a GitHub. |
| **`CHANGELOG.md`** | 12.0 KB | Registro exhaustivo de cambios, diseño adaptativo y notas técnicas. |
| **`DESIGN_SYSTEM_NUCLEAR_UI.md`** | 6.9 KB | Especificación del NuclearUI Design System (One UI Squircle, radio 26dp, paleta OLED). |
