# NuclearWearable para Windows (v1.1.0 / Build 21)

Cliente nativo de escritorio para Windows 10/11 construido con Compose Desktop + Winsock RFCOMM (`JNA`), con paridad funcional de protocolo Sony MDR (Fases 0, 1 y 2), diferenciadores locales sin telemetría (Fase 3) e interfaz **NuclearUI** alineada con Android v1.1.0 (`versionCode = 21`).

> [!NOTE]
> `NuclearWearable\windows\` es la raíz oficial del proyecto Windows a partir de `v1.1.0` (con su propio `settings.gradle.kts` independiente). La carpeta hermana `NuclearWearable_Windows\` se conserva sin modificar como archivo histórico de `v1.0.0`.

## Arquitectura y módulos implementados

- **Protocolo Sony MDR Dual-Table (`0x0C` + `0x0E`)**:
  - `src/main/kotlin/com/nuclear/soundconnect/bluetooth/SonyModels.kt`
  - `src/main/kotlin/com/nuclear/soundconnect/bluetooth/SonyProtocolParser.kt`
  - `src/main/kotlin/com/nuclear/soundconnect/bluetooth/SonyWindowsRfcommClient.kt`
  - Incluye guard de seguridad V1, sincronización bajo demanda (`syncExtendedControls`), fast-path sin bloqueo en `setNoiseMode`, ecualizador de 5 bandas + Clear Bass y 10 bandas `0..12` para `WH-1000XM6`, DSEE, prioridad de conexión (incluyendo LE Audio de 4 bytes), Auto Power Off, Remote Power Off (exclusivo V2), Speak-to-Chat (V1/V2/Ext), pausa al quitarse, reducción de ruido de viento (`0x15` WF-1000XM4), asignación táctil L/R + bloqueo, Multipoint (`PERI` + fallback `GS 0xD2`) e idioma/volumen de guía de voz.
- **Motores Fase 3 nativos para Windows (0 telemetría / sin red)**:
  - `audio/WindowsAudioMonitor.kt`: Monitor WASAPI CoreAudio (`MMDeviceEnumerator`, `IPropertyStore`, `IAudioMeterInformation`, `IAudioSessionManager2`) para `LiveCodecHudInfo` (A2DP, LE Audio/LC3, detección de perfil HFP activo y frecuencia/bits reales de Windows).
  - `audio/FindMyBudsTonePlayer.kt`: Generador estéreo PCM 44.1 kHz / 16-bit con aislamiento izquierdo/derecho (`2100 + 2800 Hz`), pulso de 350 ms / 200 ms, rampa de seguridad de 4 s (`0.12 -> 0.72`) y auto-detención a los 20 s.
  - `automation/LocalAutomationEngine.kt`: Automatización local por eventos (`CallStart`, `CallEnd`, `MediaPlayback`), franja horaria nocturna/estándar y enfriamiento de 30 s tras cambio manual.
  - `backup/ProfileBackupManager.kt`: Exportación e importación de perfiles JSON (`NuclearBackupBundle`, `schemaVersion = 1`) 100 % compatible con Android, más filtro y exportación del monitor Hex.
- **Interfaz NuclearUI y experiencia de escritorio**:
  - `Main.kt`, `ui/MainView.kt`, `ui/ExtendedSheets.kt`, `ui/AuxiliarySheets.kt`, `ui/NuclearTrayMenu.kt`, `ui/AppStrings.kt`, `util/WindowsFileDialogs.kt`, `util/SingleInstanceLock.kt`.
  - Instancia única por socket local (`127.0.0.1:47831`), ventana oculta de barra de tareas (`WS_EX_TOOLWINDOW`), menú en bandeja del sistema con diseño NuclearUI, minimizar a bandeja y cierre limpio con `✕`.

## Pruebas unitarias

```powershell
.\gradlew.bat cleanTest test --no-daemon
```

Suites en `src/test/kotlin/com/nuclear/soundconnect/`:
- `bluetooth/SonyProtocolParserTest.kt` (14 pruebas de protocolo y framing)
- `Phase3ModulesTest.kt` (5 pruebas de backup JSON, ventana horaria, filtro Hex y monitor CoreAudio)

## Compilación y empaquetado portable (`.exe` sin Java requerido)

1. Compilar pruebas y generar UberJar desde `C:\Antigravity Projects\NuclearAddons\NuclearWearable\windows\`:

```powershell
.\gradlew.bat cleanTest test packageUberJarForCurrentOS --no-daemon
Copy-Item -Force ".\build\compose\jars\NuclearWearable-windows-x64-1.1.0.jar" ".\releases\NuclearWearable.jar"
```

2. Compilar el lanzador autoextraíble x64 (`launcher.cs` + `releases\NuclearWearable.jar` + `build\runtime.zip` + `releases\app.ico`) con `csc.exe`:

```powershell
& "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /platform:x64 /target:winexe `
  "/win32icon:$PWD\releases\app.ico" `
  "/resource:$PWD\releases\NuclearWearable.jar,NuclearWearable.jar" `
  "/resource:$PWD\build\runtime.zip,runtime.zip" `
  "/out:$PWD\releases\NuclearWearable.exe" `
  "$PWD\launcher.cs" `
  /reference:System.Windows.Forms.dll `
  /reference:System.IO.Compression.FileSystem.dll
```

Al ejecutarse, `NuclearWearable.exe` extrae o actualiza automáticamente `runtime/` y `NuclearWearable.jar` en `%LOCALAPPDATA%\NuclearWearable` comparando tamaño y marca temporal, sin requerir que el usuario tenga Java instalado.

## Entregables en `releases/`

- `releases/NuclearWearable.exe` — Ejecutable portable autocontenido x64 (`125,987,328` bytes).
- `releases/NuclearWearable.jar` — UberJar compilado de la versión `1.1.0` (`78,737,627` bytes).
- `releases/app.ico` — Ícono de aplicación Windows (`45,908` bytes).
