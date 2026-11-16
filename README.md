# AutoFlow Engine

**AutoFlow Engine** adalah orkestrator alur kerja dan otomasi lokal untuk sistem operasi Android yang menggabungkan:
- **CPython 3.11 Runtime (Chaquopy)**: Eksekusi skrip Python dinamis, scraping BeautifulSoup, dan manipulasi data secara offline langsung di dalam Android.
- **Room Database v2**: Output Vault riwayat eksekusi dan Key-Value Store deduplikasi.
- **Accessibility Service & Humanized Motion**: Eksekusi sentuhan natural (Cubic Bezier) dan tombol darurat (*kill switch*) Volume Down 2x.
- **Jetpack Compose UI**: Antarmuka dashboard responsif berbasis Material 3.
- **Foreground Service & WorkManager**: Eksekusi persisten di latar belakang dan penjadwalan periodik.

---

## Struktur Modul Utama

- `engine/`: State Machine alur kerja (`WorkflowOrchestrator`), evaluasi variabel path (`ExecutionContext`), dan singleton runtime.
- `workers/`: Eksekutor tugas (Python, HTTP REST, Headless WebView, Shizuku ADB Shell, Gemini AI).
- `data/`: Entitas Room Database, DAO, dan utilitas ekspor CSV RFC 4180.
- `service/`: Foreground Service, Notification Listener, dan Accessibility Service.
- `ui/`: Dashboard, Visual/Raw JSON Editor, Output Vault, dan Manajemen Variabel.

---

## Lisensi & Kontribusi
Dibuat untuk ekosistem Android modern (Android 8.0 - Android 15).
