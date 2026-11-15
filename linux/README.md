# NuclearWearable for Linux Desktop (v1.1.0)

Native Linux Desktop controller for Sony headphones with **1:1 functional, NuclearUI visual, and Sony MDR protocol parity** against NuclearWearable Android v1.1.0 (`versionCode = 21`). **100% offline, rootless, zero telemetry.**

## Architecture Highlights
- **Rootless Bluetooth Discovery & Native RFCOMM:**
  - Discovers paired/connected Sony devices and OS `org.bluez.Battery1` fallback via system D-Bus (`gdbus` / `bluetoothctl`).
  - Connects Sony MDR RFCOMM sockets directly using JNA `libc` (`AF_BLUETOOTH = 31`, `BTPROTO_RFCOMM = 3`, packed `sockaddr_rc` with non-blocking `poll` + deterministic `shutdown`/`close`).
  - Resolves RFCOMM channels via native unprivileged L2CAP SDP (`BTPROTO_L2CAP`, PSM `0x0001`) with `sdptool` CLI fallback across Sony V2 (`956c7b26-...`), Fallback (`96cc203e-...`), and SPP (`00001101-...`) UUIDs.
  - Preserves persistent `SessionTransport`, dual-channel ARQ `(0x0C, 0x0E)` deduplication, strict 7-byte V2 `0x68 0x17` (and 8-byte WF-1000XM4 `0x15` wind-reduction / V1 `0x02`) NC/ASM commands, Table 1/Table 2 sync, and all 17+ Phase 2 setters.
- **Live Codec & HFP HUD (Phase 3.2):**
  - Reads live BlueZ codec (`LDAC`, `AAC`, `SBC-XQ`, `SBC`, `aptX`, `aptX HD`, `LC3`), sample rate, bit depth, channels, and active profile (`a2dp-sink` vs `headset-head-unit` HFP SCO call warning) from **PipeWire (`pw-dump`)** and **PulseAudio (`pactl`)**, with one-click Linux Bluetooth settings launcher (`gnome-control-center` / `kcmshell6` / `blueman-manager`).
- **Offline Local Automation Engine (Phase 3.3):**
  - Zero GPS, zero Wi-Fi SSID, zero network calls. Config stored in `$XDG_CONFIG_HOME/NuclearWearable/settings.json` (`~/.config/NuclearWearable/settings.json`).
  - Auto-switches to Ambient + Voice Focus during calls (`headset-head-unit` / active mic stream) and restores the previous noise mode on hangup; handles media start (`RUNNING` sink / MPRIS `Playing`) and local `HH:mm` quiet hours with 30s manual override cooldown.
- **Stereo Local Find My Buds (Phase 3.4):**
  - Uses `javax.sound.sampled` PCM 16-bit 44.1 kHz stereo output (`LEFT` or `RIGHT` 100% isolated), 2100+2800 Hz pulsed beacon, 4s soft gain ramp, and mandatory 20s timeout.
- **Interoperable JSON Profile Backup & Lab Mode (Phase 3.5):**
  - Uses exact `NuclearBackupBundle` v1 schema interoperable across Android, Windows, and Linux with native `zenity`/`kdialog`/Swing file dialogs, plus live RFCOMM Hex Monitor (`All` / `Unknown / Errors` filter, `.txt` save, clipboard copy).

## Build & Run on Linux
Prerequisites: JDK 17+, standard user session BlueZ (`bluez`), `gdbus`/`bluetoothctl`, PipeWire (`pw-dump`) or PulseAudio (`pactl`), optional `zenity`/`kdialog` for native dialogs. No `sudo` required.

```bash
# Run directly
./gradlew run

# Run all unit tests
./gradlew test

# Build portable bundle / .deb / .AppImage
./packaging/linux/build-linux.sh
./packaging/linux/appimage-build.sh
```
