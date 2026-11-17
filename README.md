# AutoFlow Engine

**AutoFlow Engine** adalah orkestrator alur kerja dan otomasi lokal untuk sistem operasi Android yang menggabungkan:
- **n8n Localhost Auto-Start, Bridge & Inspector**: Menghidupkan server **n8n** lokal (`127.0.0.1:5678`) otomatis saat tombol **Play** ditekan (via Local Bridge `:15678`, Shizuku Shell, atau Termux `RUN_COMMAND`), lalu menginspeksi seluruh workflow, node, koneksi, dan riwayat eksekusi di dalam n8n localhost.
- **CPython 3.11 Runtime (Chaquopy)**: Eksekusi skrip Python dinamis, scraping BeautifulSoup, dan manipulasi data secara offline langsung di dalam Android.
- **Room Database v4**: Output Vault riwayat eksekusi dan Key-Value Store deduplikasi serta konfigurasi n8n.
- **Accessibility Service & Humanized Motion**: Eksekusi sentuhan natural (Cubic Bezier) dan tombol darurat (*kill switch*) Volume Down 2x.
- **Jetpack Compose UI**: Antarmuka dashboard responsif berbasis Material 3.
- **Foreground Service & WorkManager**: Eksekusi persisten di latar belakang dan penjadwalan periodik.

---

## Integrasi n8n Localhost (Omalauncher / Linux / Termux)

Karena aplikasi Android tidak bisa mengeksekusi biner di dalam sandbox rootfs Linux/proot secara langsung tanpa izin khusus, proyek ini menyediakan **AutoFlow ↔ n8n Local Bridge** ringan (`bridge/autoflow_n8n_bridge.py`, hanya stdlib Python, listen strictly di `127.0.0.1:15678`).

### Cara Pasang Bridge di Omalauncher / Arch Linux:
```bash
bash bridge/install.sh
```
Skrip tersebut akan:
1. Memasang `/usr/local/bin/autoflow-n8n-bridge`
2. Memasang widget home screen `~/.config/omalauncher/widgets/n8n-bridge.owl` agar daemon bridge selalu siap di latar belakang (`127.0.0.1:15678`)
3. Menghidupkan bridge saat itu juga — saat **Play** ditekan di AutoFlow Engine APK, bridge akan otomatis menjalankan `/usr/bin/n8n start` dan menginspeksi workflow di `~/.n8n/database.sqlite` maupun API `:5678`.

---

## Struktur Modul Utama

- `engine/`: State Machine alur kerja (`WorkflowOrchestrator`), evaluasi variabel path (`ExecutionContext`), dan singleton runtime.
- `n8n/`: Klien API n8n (`/api/v1/*`, `/rest/*`, `/webhook/*`), pengelola konfigurasi & cookie sesi, serta pengendali auto-start bridge.
- `workers/`: Eksekutor tugas (`N8N_BRIDGE`, Python, HTTP REST, Headless WebView, Shizuku ADB Shell, Gemini AI).
- `data/`: Entitas Room Database, DAO, starter workflow, dan utilitas ekspor CSV RFC 4180.
- `service/`: Foreground Service, Notification Listener, dan Accessibility Service.
- `ui/`: Dashboard, n8n Localhost Inspector, Visual/Raw JSON Editor, Output Vault, dan Manajemen Variabel.
- `bridge/`: Daemon bridge Python stdlib + widget `.owl` untuk Omalauncher.

---

## Lisensi & Kontribusi
Dibuat untuk ekosistem Android modern (Android 8.0 - Android 15).
