# Guía Maestra: Creación de Widgets Estilo Samsung One UI en Android

Esta guía documenta la arquitectura técnica, estándares de diseño y patrones de implementación para desarrollar widgets en Android con la misma calidad visual, fluidez y comportamiento nativo que los widgets oficiales de Samsung One UI (Galaxy Buds, Batería del Sistema y Controles de Dispositivo).

---

## 1. El Secreto Gráfico: Motor Canvas Bitmap vs. XML Tradicional

Las vistas estándar de widgets de Android (`RemoteViews`) son extremadamente limitadas por diseño del sistema:
- No admiten trazados SVG dinámicos.
- No soportan terminaciones de trazo redondeadas (`Paint.Cap.ROUND`) para diales circulares.
- No permiten antialiasing fino ni composición de capas vectoriales en tiempo real.

### La Solución Arquitectónica
En lugar de depender de layouts XML rígidos para los gráficos, se utiliza un **generador en Canvas de alta definición** que crea un `Bitmap` en memoria y lo proyecta directamente a un `ImageView` del widget:

```kotlin
// Generación en memoria con antialiasing nativo y calidad ARGB_8888
val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
val canvas = Canvas(bitmap)

// 1. Pistas circulares y arcos de progreso
val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 10f
    color = 0x24FFFFFF // Blanco al 14% de opacidad
    strokeCap = Paint.Cap.ROUND
}
canvas.drawCircle(cx, cy, radius, trackPaint)

// 2. Arco de batería (-90° es la posición de las 12 en punto)
val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 10f
    color = getOneUiBatteryColor(level)
    strokeCap = Paint.Cap.ROUND
}
val sweepAngle = (level / 100f) * 360f
canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, sweepAngle, false, progressPaint)

// 3. Estampar vector exacto centrado en el anillo
val budDrawable = ContextCompat.getDrawable(context, iconRes)
budDrawable?.setBounds(left, top, right, bottom)
budDrawable?.draw(canvas)

// 4. Inyección instantánea al RemoteViews
remoteViews.setImageViewBitmap(R.id.widget_canvas_view, bitmap)
```

---

## 2. Métricas y Tokens de Diseño One UI

Para mantener consistencia absoluta con One UI 6, 7 y 8:

| Elemento | Token / Valor | Comportamiento |
| :--- | :--- | :--- |
| **Radio de esquina (Squircle)** | `26dp` | Define el fondo exterior del contenedor del widget. |
| **Padding exterior** | `16dp` | Margen interno entre el borde squircle y el contenido. |
| **Verde Batería One UI** | `#34C759` | Nivel normal de carga (> 20%). |
| **Ámbar Advertencia** | `#FF9500` | Nivel de advertencia (11% a 20%). |
| **Rojo Crítico** | `#FF3B30` | Nivel crítico (≤ 10%). |
| **Pista de fondo oscura** | `#24FFFFFF` | Tono sutil visible en fondos oscuros sin saturar. |
| **Fondo Oscuro Samsung** | `#E61C1C1E` | Gris carbón profundo con 90% de opacidad. |
| **Fondo Claro Samsung** | `#F2FFFFFF` | Blanco nieve con 95% de opacidad. |
| **Radio Píldora de Ruido** | `100dp` | Radio de píldora completa para la barra segmentada. |

---

## 3. Ajustes Nativos del Widget (Pulsación Larga -> "Ajustes")

A partir de Android 12 y en todo el ecosistema One UI, la personalización de widgets (color y nivel de transparencia) no debe estar escondida dentro de la aplicación principal. Debe abrirse de forma nativa cuando el usuario **mantiene presionado el widget en su launcher y toca el icono de engrane / "Ajustes"**.

### A. Declaración en los Metadatos XML (`res/xml/widget_info.xml`)
```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="280dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:configure="com.nuclear.wearable.widget.WidgetSettingsActivity"
    android:widgetFeatures="reconfigurable|configuration_optional"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_nuclear_combo_4x2" />
```

> **Clave técnica:** Los atributos `android:configure` y `android:widgetFeatures="reconfigurable|configuration_optional"` habilitan el menú contextual del launcher de Samsung para abrir `WidgetSettingsActivity` en cualquier momento.

### B. Implementación de `WidgetSettingsActivity`
1. **Captura del ID:** Recibe `AppWidgetManager.EXTRA_APPWIDGET_ID`.
2. **Controles:**
   - Selector de fondo: *Modo Oscuro*, *Modo Claro* o *Coincidir con el Sistema*.
   - Slider de transparencia: `0%` (totalmente translúcido) a `100%` (sólido).
   - Vista previa en vivo (`Live Preview`) que reacciona a los cambios en pantalla.
3. **Persistencia por Widget:**
   - Guarda los ajustes en `SharedPreferences` usando una clave asociada al ID: `widget_prefs_${appWidgetId}_alpha`.
4. **Confirmación y Redibujado:**
   ```kotlin
   // Forzar actualización inmediata del widget configurado
   NuclearWidgetManager.updateWidget(this, appWidgetManager, appWidgetId)

   val resultIntent = Intent().apply {
       putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
   }
   setResult(Activity.RESULT_OK, resultIntent)
   finish()
   ```

---

## 4. Jerarquía de Contenidos: Cabecera One UI

La cabecera superior debe colocarse a `28dp` de altura con la siguiente distribución:
- **Izquierda:** Nombre del dispositivo en tipografía Semi-Bold One UI (`14sp`), por ejemplo: `WF-1000XM6` o `WH-1000XM5`.
- **Derecha:** Punto de estado de conexión (`ic_status_dot_connected` en verde One UI o `ic_status_dot_disconnected` en gris/rojo) y botón táctil sutil de sincronización/actualización con `PendingIntent`.

---

## 5. Comportamiento en Estado Desconectado

Para evitar confusión visual y mantener la elegancia de One UI cuando los audífonos están apagados o fuera de rango:
1. **Ocultar números de porcentaje:** Los textos numéricos (`--%` o porcentajes residuales) se ocultan por completo en el widget.
2. **Pistas circulares sutiles:** Los anillos permanecen como líneas sutiles sin relleno de color.
3. **Siluetas vectoriales atenuadas:** Las ilustraciones vectoriales del último dispositivo conectado se mantienen visibles pero con un alfa del 40% (estado dormido).
4. **Subtítulo limpio:** La cabecera pasa a mostrar `WF-1000XM6 • Desconectado`.
5. **Historial In-App:** La aplicación guarda y muestra una tarjeta con la última lectura de carga y hora registrada para consulta del usuario.

---

## 6. Barra Segmentada de Control de Ruido (NC / ASM)

La barra de modos de audio reproduce la píldora segmentada de One UI:
- **3 Estados:** Cancelación Activa de Ruido (ANC), Desactivado (OFF) y Sonido Ambiente (ASM).
- **Segmento Activo:** Fondo sólido (`pill_segment_active.xml`) con icono en contraste alto y sombra sutil de 2dp.
- **Segmentos Inactivos:** Fondo transparente con icono en blanco translúcido (`#B3FFFFFF`).
- **Respuesta Háptica:** Cada `PendingIntent` dispara vibración táctil nativa al presionar.
