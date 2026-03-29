# Tubes 1 - PriaOslo
# Bot utama - bot1
# Bot alternatif - bot2, bot4

**Tugas Besar 1 IF2211 Strategi Algoritma**  
Semester II Tahun 2025/2026  

Pemanfaatan Algoritma Greedy dalam pembuatan bot permainan Battlecode 2025.

---

## Deskripsi Proyek

Proyek ini merupakan tugas kelompok untuk mata kuliah Strategi Algoritma. Tim mengimplementasikan tiga bot dengan strategi greedy yang berbeda, masing-masing menggunakan heuristic yang berbeda pula, dengan tujuan memenangkan permainan Battlecode 2025 (mewarnai >70% peta atau menghancurkan semua unit lawan).

---

## Algoritma Greedy yang Diimplementasikan

### Bot Utama (main_bot)

**Strategi:** Greedy berbasis prioritas aksi dan koordinasi tim.

- **Heuristic:** Urutan prioritas tetap: (1) combat jika ada menara musuh dekat, (2) gank menara musuh jika target sudah ditetapkan, (3) kembali ke menara sekutu jika cat rendah, (4) idle: eksplorasi, mewarnai, dan membangun menara di reruntuhan.
- **Fungsi seleksi:** Memilih aksi dengan prioritas tertinggi yang layak (feasible). Untuk membangun menara, memilih reruntuhan terdekat dan menggilir tipe menara (Paint -> Money -> Defense).
- **Koordinasi:** Menara mengirim perintah gank saat cukup robot sekutu idle; robot memilih menara musuh terdekat sebagai target.

### Bot Alternatif 1 (alternative_bots_1)

**Strategi:** Greedy eksplorasi kuadran dan klaim reruntuhan.

- **Heuristic:** Setiap robot diberi kuadran peta berbeda (berdasarkan ID) untuk mendistribusikan eksplorasi. Robot memilih reruntuhan terdekat yang belum diklaim, lalu memilih petak pola menara yang belum selesai dengan jarak terkecil.
- **Fungsi seleksi:** Memilih reruntuhan terdekat yang feasible; memilih petak dalam radius 3 dengan jarak terkecil untuk diwarnai; menggilir tipe menara (Money → Paint → Defense).

### Bot Alternatif 2 (alternative_bots_2)

**Strategi:** Greedy reruntuhan terdekat dengan fokus menara cat.

- **Heuristic:** Robot selalu menarget reruntuhan terdekat. Hanya membangun menara cat (Paint Tower) untuk memaksimalkan produksi cat dan ekspansi wilayah.
- **Fungsi seleksi:** Memilih reruntuhan terdekat; memilih petak pola yang belum selesai dengan jarak terkecil; fokus pada satu reruntuhan sampai selesai.

---

## Struktur Proyek

```
Tubes1_PriaOslo/
├── src/
│   ├── main_bot/            # Bot utama
│   ├── alternative_bots_1/  # Bot alternatif 1
│   └── alternative_bots_2/  # Bot alternatif 2
├── doc/
│   └── laporan.pdf
├── client/            # Aplikasi client Battlecode
├── maps/              # Peta permainan
├── matches/           # Output pertandingan
├── build.gradle
└── README.md
```

---

## Author

| Nama                  | NIM      |
|-----------------------|----------|
| Muhammad Iqbal Raihan | 13524011 |
| Mahmudia Kimdaro Amin | 13524083 |
| Ariel C Sitorus       | 13524085 |

**Kelompok:** PriaOslo

---

## Referensi

- [Battlecode 2025](https://battlecode.org/)
- [Battlecode 2025 Java Quick Start](https://play.battlecode.org/bc25java/quick_start)
- [Battlecode 2025 API Javadoc](https://releases.battlecode.org/javadoc/battlecode25/3.1.0/battlecode/common/package-summary.html)
