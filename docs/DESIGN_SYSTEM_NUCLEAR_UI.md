# NuclearUI Design System
**Especificación de Diseño para Aplicaciones de Dispositivo / Wearable Companion**

> [!IMPORTANT]
> **Alcance y Arquitectura Actual:**
> **NuclearUI** es un lenguaje visual fuertemente inspirado en la estética de **One UI** (Galaxy Wearable / Galaxy Watch / Buds Companion), optimizado para **aplicaciones de una sola interfaz (Single-Surface Companion)**.
> Está diseñado y probado específicamente para flujos centrados en:
> 1. Vitrina / Hero superior del dispositivo conectado al aire.
> 2. Fila horizontal de acciones rápidas con botones circulares.
> 3. Tarjeta contenedora agrupada de ajustes con filas, divisores sutiles y hojas modales (`ModalBottomSheet`).
> 4. Widgets de pantalla de inicio transparentes y adaptativos.
> 
> *Nota:* Para aplicaciones complejas con múltiples pestañas inferiores (`NavigationBar`), navegación jerárquica profunda o múltiples ventanas/pantallas, la adaptación de este lenguaje aún debe ser evaluada y probada.

---

## 1. Principios Fundamentales de NuclearUI

1. **Hero al aire (Free-Floating Hero):** El dispositivo no se encierra en tarjetas cuadradas pesadas. Flota directamente sobre el fondo general (negro OLED, carbón o claro) con tipografía destacada y estado de carga compacto.
2. **Esquinas Squircle Homogéneas (`26.dp`):** Todas las tarjetas principales, contenedores agrupados y placas de widget comparten exactamente el mismo radio de curvatura (`26.dp`), logrando una coherencia geométrica natural.
3. **Agrupación en Tarjetas Únicas:** En lugar de crear una tarjeta para cada ajuste, las opciones se agrupan dentro de una sola tarjeta contenedora separadas por divisores horizontales de `0.5.dp`.
4. **Píldoras Circulares de Iconos:**
   - **En listas de ajustes:** Círculo de `38.dp` con fondo de color saturado e icono blanco de `20.dp`.
   - **En acciones rápidas:** Círculo de `52.dp` que anima suavemente su color de fondo al estado activo con duración de `220 ms`.
5. **Diferenciación de Estado de Conexión (App vs Widget):**
   - **En la App (Hero Showcase):** Los iconos conservan el mismo tamaño; el conectado se muestra con alto contraste, mientras que el desconectado se atenúa en gris tenue.
   - **En los Widgets:** Los iconos mantienen siempre **exactamente el mismo tamaño, el mismo grosor limpio y el 100% de opacidad** (blanco puro `#FFFFFF` en oscuro / negro carbón `#1C1C1E` en claro). La conexión de cada componente se distingue de manera limpia y directa por la aparición de su porcentaje de batería y/o su anillo correspondiente.

---

## 2. Jerarquía del Sistema de Temas

La configuración de apariencia sigue estrictamente esta estructura de 3 modos con interruptor OLED independiente:

```
Claro
└── Colores (Paleta de acento)
Oscuro
├── Switch OLED (Negro puro #000000 vs Gris oscuro #121214)
└── Colores (Paleta de acento)
Sistema
├── Switch OLED en modo nocturno
└── Colores (Paleta de acento)
```

### Tabla de Tokens de Color

| Token | Modo Oscuro (OLED) | Modo Oscuro (Gris) | Modo Claro |
| :--- | :--- | :--- | :--- |
| `background` | `#000000` | `#121214` | `#F2F2F7` |
| `surfaceCard` | `#111113` | `#1C1C1E` | `#FFFFFF` |
| `textPrimary` | `#FFFFFF` | `#FFFFFF` | `#1C1C1E` |
| `textSecondary` | `#8E8E93` | `#8E8E93` | `#707070` |
| `divider` | `#1FFFFFFF` | `#1FFFFFFF` | `#1F000000` |
| `inactivePill` | `#148E8E93` | `#148E8E93` | `#12000000` |

### Paleta de Acento Oficial Nuclear (12 Colores Minimalistas sin Etiqueta de Texto)
1. `#00E676`
2. `#10B981`
3. `#00B0FF`
4. `#007AFF`
5. `#00D2FF`
6. `#FFB703`
7. `#FFB300`
8. `#FF5722`
9. `#FF1744`
10. `#FF007F`
11. `#A855F7`
12. `#8B5CF6`

El color de acento elegido en la app se propaga automáticamente a los botones activos y anillos de los widgets.

---

## 3. Estructura de la Pantalla Principal (Single-Surface)

```
[ Insets: .statusBarsPadding() ]
  └── Top Bar (Solo botón sutil de recarga/spinner en la esquina superior derecha)
  └── Hero Showcase (Centrado)
        ├── Iconos del dispositivo (+20% tamaño protagonista: 70dp auriculares, 77dp estuche, 156dp diadema)
        ├── Título en negrita (26.sp)
        └── Fila compacta de batería con icono (14.sp)
  └── Tarjeta de Acciones Rápidas (Card 26.dp)
        ├── Fila horizontal de botones circulares (52.dp) con label abajo
        └── Subpanel animado colapsable para ajustes directos (deslizadores con onValueChangeFinished)
  └── Tarjeta de Menú Agrupado (Card 26.dp)
        ├── MenuRowItem 1 (Icono circular 38.dp + Título + Subtítulo + Chevron)
        ├── HorizontalDivider (0.5.dp)
        ├── MenuRowItem 2
        ├── ...
        └── Acerca de (Nombre de la app, versión y compilación)
[ Insets: .navigationBarsPadding() ]
```

---

## 4. Subpantallas y Menús Secundarios (`ModalBottomSheet`)

En lugar de navegar a una nueva Activity para opciones secundarias, NuclearUI utiliza `ModalBottomSheet` con esquinas superiores redondeadas y contenedor en `surfaceCard`:
- **Tema y colores:** Selector de 3 tabs (Claro / Oscuro / Sistema) + Switch OLED + Cuadrícula limpia de 12 círculos de color (2 filas de 6).
- **Historial de dispositivos:** Lista de dispositivos vinculados con tarjetas internas redondeadas (`16.dp`), última batería vista y marca de tiempo.
- **Ajustes detallados:** Controles de audio y parámetros finos.
- **Acerca de:** Hoja informativa compacta con insignia de la aplicación, número de versión y detalles técnicos.

---

## 5. Especificación de Widgets (NuclearUI Widgets)

1. **Configurador con Wallpaper Real:**
   - La Activity de configuración de widgets utiliza un tema transparente con `android:windowShowWallpaper="true"` y un scrim sutil (`Color.Black.copy(alpha = 0.28f)`), proyectando el fondo de pantalla real del usuario.
2. **Tres Estilos de Widget Disponibles:**
   - **Minimalista:** Sin anillos, icono grande arriba (`66%` TWS / `72%` Diadema), porcentaje directo en negrita abajo y etiqueta inferior (`L`, `R`, `Estuche`, `Diadema`).
   - **Medio anillo:** Arco abierto de `250°` con puntas redondeadas (`StrokeCap.Round`), icono amplio centrado (`52%` TWS / `58%` Diadema) y porcentaje ubicado en la abertura inferior del anillo.
   - **Anillo completo:** Aro cerrado de `360°` con icono compacto y porcentaje en el interior, manteniendo simetría exacta entre auriculares conectados y desconectados.
3. **Adaptabilidad Real:**
   - `onAppWidgetOptionsChanged` lee `minWidth` y `minHeight` en dp y genera el bitmap squircle con la relación de aspecto real, evitando deformar las esquinas de `26dp` en cualquier cuadrícula del launcher.
4. **Ocultación Inteligente de Controles:**
   - En el widget combinado (Batería + Control de Ruido), la barra inferior de modos de sonido se oculta automáticamente (`View.GONE`) cuando los audífonos están desconectados.
