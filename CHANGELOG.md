# Registro de Cambios - NuclearWearable v1.1.0

**Versión:** 1.1.0 (Compilación 21)  
**Fecha:** 15 de Noviembre de 2026  
**Paquete Android / Wear OS:** `com.nuclear.soundconnect`  
**Firma Android / Wear OS:** `nuclear_ecosystem.keystore` (Certificado SHA-256 unificado: `bd944ae04a6fb8df7b7abd83c8209932188e3a2a793a860c51f3c5a770029784`)  
**Plataformas:** Android 12+ (API 31–35) · Wear OS 3+ (API 30–35) · Windows x64 · Linux x64

---

## ✨ Novedades principales de v1.1.0 (Fases 0, 1, 2 y 3)

1. **Arquitectura modular y cliente RFCOMM optimizado (Fases 0 y 1):**
   - Extracción de `:core` compartido entre móvil (`:app`) y reloj (`:wear`), con deduplicación ARQ sensible al canal (`0x0C` Table 1 / `0x0E` Table 2) y detección de capacidades por `DeviceCapabilities.resolve`.
   - **Quick Settings Tile nativo** (`NuclearQuickSettingsTileService`) en Android para alternar modos de ruido sin abrir la app.

2. **Paridad de protocolo Sony MDR (Fase 2):**
   - **Ecualizador hardware (`EQEBB`)**: presets oficiales y curva manual de 5 bandas (`-10..+10`) + Clear Bass (V1/V2), así como soporte de **10 bandas (`0..12`)** para `WH-1000XM6` (`0x04`).
   - **Audio:** interruptor **DSEE** (`AUDIO 0x01`) y **prioridad de conexión Bluetooth** (`0x00 / 0x02 / 0x05`, incluyendo trama de 4 bytes para LE Audio).
   - **Confort y energía:** **Speak-to-Chat** (V1 `0x05` y V2 `0x02`/`0x0C` con sensibilidad, tiempo de retorno y enfoque de voz), **Pausa al retirar (`0xF8 0x01`)**, **Filtro de viento dedicado WF-1000XM4 (`0x15` 8 bytes)** conservando intacto el invariante de 7 bytes en `0x17`, **Apagado automático (`POWER 0x04/0x05`)** y **Apagado remoto seguro (`0x24 0x03 0x01`)** exclusivo V2 con diálogo de confirmación.
   - **Controles táctiles, guía de voz y Multipunto:** reasignación independiente L/R (`0x03/0x0E` incluyendo `NO_FUNCTION = 0xFF`), bloqueo de panel táctil (`GS 0xD3`), **Guía de voz (`0x46..0x49` en `0x0E`)** con idioma y volumen `-2..+2`, y gestión **Multipunto (`PERI 0x0E` / `GS 0xD2`)** con historial de hosts, conexión/desconexión y cambio rápido de fuente (`0x3C`).

3. **Diferenciadores locales sin telemetría (Fase 3):**
   - **HUD de Audio y Códec en vivo (`AudioCodecMonitor` / `WindowsAudioMonitor` / `LinuxAudioCodecMonitor`):** muestra códec real (`LDAC`, `AAC`, `SBC`, `aptX`, `LC3`), frecuencia y profundidad de bits junto con alerta HFP SCO durante llamadas y acceso directo a Ajustes BT.
   - **Automatización local 100% libre de GPS, Wi-Fi SSID e Internet (`LocalAutomationEngine`):** reglas reactivas por llamada, inicio de reproducción y franja horaria local con enfriamiento de 30s tras override manual.
   - **Find My Buds estéreo local (`FindMyBudsTonePlayer`):** baliza sonora progresiva aislada al 100% en canal Izquierdo o Derecho con rampa de seguridad de 4s, apagado automático a los 20s y aviso auditivo.
   - **Respaldo JSON offline y Modo Laboratorio mejorado (`ProfileBackupManager`):** exportar/importar configuración (`NuclearBackupBundle`, `schemaVersion = 1`) interoperable entre Android, Windows y Linux, más filtro de tramas desconocidas/errores con exportación `.txt` en `HexMonitorCard`.
   - **Privacidad verificada:** cero permiso `INTERNET` y cero permisos de ubicación (`ACCESS_FINE_LOCATION` / Wi-Fi SSID) en todas las plataformas.

4. **Wear OS — Paridad NuclearUI y Tiles interactivos (`wear/`):**
   - **Tarjetas (Tiles) con 3 círculos vectoriales in-place:** tanto `NuclearTileService` como `NuclearQuickControlsTileService` muestran los 3 botones circulares vectoriales sin texto (`ANC`, `Ambiente`, `Off`) con `LoadAction` para conmutar de inmediato sin abrir la app; la píldora `Nivel X/20` o cabecera abre la app cuando se requiere ajuste fino.
   - **Estética OLED unificada y atenuación por componente:** adopción exacta de la paleta OLED móvil (`#000000`, `#111113`, `#1F1F23`, `#8E8E93`), cero emojis (100% vectores incluidos rayo de carga y stepper `−` / `+` / check), eliminación de textos `"Desconectado"` / `"(Offline)"` sustituidos por atenuación gris individual (`NuclearDisconnectedTint`), píldora de códec activo y selector de color independiente (*"Seguir al celular"* o paleta de 12 colores NuclearUI).
   - **Complicación fiel al modelo:** icono dinámico en `NuclearComplicationService` según la familia Sony conectada + soporte **Standalone** directo vía Bluetooth RFCOMM (`SonyRfcommClient`).

5. **Windows x64 Desktop (`windows/`):**
   - Cliente nativo Compose Desktop + Winsock RFCOMM (`JNA`) sobre Windows 10/11 con paridad completa de protocolo Sony MDR dual-table (`0x0C` + `0x0E`), Fases 0–3 y sistema visual **NuclearUI**.
   - Monitor WASAPI CoreAudio en vivo (`WindowsAudioMonitor`), **Find My Buds** estéreo 44.1 kHz, **LocalAutomationEngine**, **ProfileBackupManager** compatible 1:1 con Android y diálogo nativo de archivos (`WindowsFileDialogs`).
   - Bandeja del sistema (`SystemTray`) con icono dinámico, menú flyout `NuclearTrayMenu`, instancia única por socket local (`127.0.0.1:47831`), `AppUserModelID` explícito y lanzador autoextraíble x64 sin Java requerido (`NuclearWearable_Windows_v1.1.0_release.exe`).

6. **Linux x64 Desktop (`linux/`) — Nuevo Port Oficial:**
   - Nuevo cliente nativo para escritorio Linux **100% sin root (rootless)**: descubrimiento BlueZ y batería de respaldo `org.bluez.Battery1` por D-Bus (`gdbus` / `bluetoothctl`), sockets RFCOMM directos vía JNA `libc` (`AF_BLUETOOTH = 31`, `BTPROTO_RFCOMM = 3`) y resolución SDP por L2CAP nativo (`PSM 0x0001`) con fallback `sdptool`.
   - Monitor en vivo de códec y llamadas (`LinuxAudioCodecMonitor`) mediante **PipeWire (`pw-dump`)**, **PulseAudio (`pactl`)** y **MPRIS D-Bus** (`org.mpris.MediaPlayer2.*`).
   - Almacenamiento XDG en `$XDG_CONFIG_HOME/NuclearWearable/settings.json` (`~/.config/NuclearWearable/settings.json`), selector de archivos nativo (`zenity` / `kdialog` / Swing), protección segura en GNOME Wayland para minimizar sin atrapar ventana y scripts de empaquetado `.jar` / `.deb` / `.AppImage`.

---

## 📦 Archivos Entregables — v1.1.0

| Plataforma | Archivo | Tamaño | SHA-256 |
| :--- | :--- | :--- | :--- |
| **Android 12+** | `NuclearWearable_v1.1.0_release.apk` | `41,981,678` B (~40.0 MiB) | `CB57FE69AFA926D5BAC01EF1D6C1882E7DDBED9667E53DAFA6FA1C21AD5E90DD` |
| **Wear OS 3+** | `NuclearWearable_WearOS_v1.1.0_release.apk` | `28,146,350` B (~26.8 MiB) | `030BAA2E6ED4B3E67EAD1E76AF70C1AF4F1C5B2204DDF79578B62D0253713330` |
| **Windows x64** | `NuclearWearable_Windows_v1.1.0_release.exe` | `125,987,328` B (~120.2 MiB) | `839F2747C93FA2DC118CBFE0EA58E932672FC4DE2083603F83AACB454F9245B3` |
| **Linux x64** | `NuclearWearable_Linux_x64_v1.1.0.jar` | `80,576,089` B (~76.8 MiB) | `92F3959DC0E7D1F9FC828166BF2DF1D1E5E1FEF67FF8102532E181082B033617` |

---

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

## 🛠️ Detalle Técnico de Novedades y Refinamientos (v1.0.0)

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
