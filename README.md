# Pemutar Musik Saiya 🎵

Aplikasi Pemutar Musik sederhana namun bertenaga untuk Android, dibangun menggunakan **Jetpack Compose** dengan arsitektur modern dan performa yang sangat dioptimalkan.

## 🚀 Fitur Utama
*   **Performa Super Ringan:** Pemuatan lagu berjalan asinkron di latar belakang (*Background thread*) menggunakan Kotlin Coroutines (`Dispatchers.IO`), bebas hambatan dan instan saat dibuka.
*   **Bilah Mini Player Presisi:** Mini Player dirancang khusus agar selalu berada tepat di atas *Navigation Bar* (tidak bertumpuk) dengan animasi transisi yang mulus.
*   **Pengurutan Alami (Natural Sorting):** Mendukung pengurutan lagu berbasis alfabet murni tanpa terganggu oleh simbol-simbol aneh atau tanda kurung nomor track lagu di awal nama file.
*   **Kriteria Sortir Lengkap:** Urutkan koleksi musikmu berdasarkan **Judul**, **Artis**, **Durasi**, atau **Terbaru ditambahkan** lewat *Dropdown Menu* yang praktis.
*   **Integrasi Volume Sistem:** Kontrol volume audio di dalam aplikasi disinkronkan langsung dengan slider volume fisik Android via `AudioManager`.
*   **Gestur Swipe Album Art:** Geser ke kanan atau kiri pada album art untuk mengganti lagu dengan kartu pratinjau (*peek cards*) lagu sebelum dan sesudahnya yang menonjol di tepi layar.
*   **Fitur Tidur Otomatis (Sleep Timer):** Atur waktu mati otomatis agar pemutar musik berhenti dengan sendirinya saat Anda tertidur.
*   **Kecepatan Putar (Playback Speed):** Atur tempo lagu dari lambat hingga cepat sesuka Anda.

---

## 🛠️ Tech Stack & Library
*   **Language:** Kotlin 1.9+
*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Concurrency:** Kotlin Coroutines & Flow
*   **Build Tool:** Gradle (Kotlin DSL)
*   **Minimum SDK:** Android 26 (Android 8.0)
*   **Target SDK:** Android 34 (Android 14)

---

## 📦 Cara Membangun (Build) Proyek

### 1. Build Versi Debug (Untuk Pengujian)
```bash
./gradlew assembleDebug
```
Output APK dapat ditemukan di: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Versi Release (Sangat Mulus & Cepat dengan R8/ProGuard)
```bash
./gradlew assembleRelease
```
Output APK dapat ditemukan di: `app/build/outputs/apk/release/app-release.apk`
