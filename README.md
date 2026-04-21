Buatkan plugin Minecraft Paper 1.21.1 bernama QuantumClan dengan package me.bintanq.quantumclan menggunakan Maven.

## TECH STACK
- Paper 1.21.1
- Java 21
- Maven (dengan shading + relocation)
- HikariCP + SQLite
- Adventure API + MiniMessage (bundled di Paper)

## DEPENDENCIES & HOOKS
Semua dependency berikut wajib ada hook-nya dengan null-check (kalau plugin tidak ada, fitur terkait disabled gracefully, dan logger info saat onEnable() menampilkan status tiap hook).

REQUIRED:
- Vault (Economy abstraction — primary currency "balance")

OPTIONAL HOOKS:
- PlayerPoints (secondary point currency alternatif)
- GemsEconomy (multi-currency support)
- PlaceholderAPI (expose placeholder clan untuk hologram/chat)
- LuckPerms (baca rank player untuk keperluan permission internal jika dibutuhkan)
- DecentHolograms (tampil leaderboard reputasi clan via hologram)
- HolographicDisplays (fallback jika DecentHolograms tidak ada)
- WorldGuard (proteksi arena war — no-build, no-break, no-pvp-outside-war)
- FAWE (Fast Async WorldEdit) — untuk Clan Hall schematic engine alternatif

Semua hook dibungkus dalam class HookManager.java yang diinisialisasi di onEnable().

## FITUR LENGKAP

### 1. CLAN CORE
- Buat clan bayar Vault balance (configurable di config.yml)
- Nama clan + tag pendek maks 8 karakter misal [NWF]
- Tag bisa di depan atau belakang nama di chat (configurable di config.yml: PREFIX/SUFFIX)
- Tag color bisa dibeli via Coins (built-in secondary currency plugin)
- Delete clan, transfer ownership
- Clan info GUI
- Input nama clan, tag, dll via CHAT bukan GUI (kirim prompt ke player, tunggu next chat message)

### 2. ROLE SYSTEM
- Role sepenuhnya customizable via roles.yml
- Default role: Leader, Officer, Member
- Setiap role punya permission node sendiri yang configurable di roles.yml:
    - can-kick, can-invite, can-upgrade, can-access-shop, can-set-home,
      can-deposit, can-declare-bounty, can-manage-war, can-announce, dll
- Leader selalu punya semua permission, tidak bisa diubah
- Hierarki role berdasarkan urutan di roles.yml (index atas = lebih tinggi)

### 3. ECONOMY
- Vault sebagai primary balance (REQUIRED)
- PlayerPoints sebagai opsional alternatif jika dikonfigurasi
- GemsEconomy sebagai opsional multi-currency
- Built-in Coins sebagai secondary premium currency (tersimpan di SQLite)
- Coins bisa di-grant via command admin (integrasi donation store)
- Clan Money = kas bersama clan, diisi via deposit anggota
- Abstraction layer EconomyProvider interface dengan implementasi:
    - VaultProvider
    - PlayerPointsProvider
    - GemsEconomyProvider
    - CoinsProvider (built-in)
- Provider yang aktif dikonfigurasi di config.yml

### 4. CLAN LEVELING
- Level clan naik dengan spend Clan Money
- Tiap level unlock slot member + slot sethome lebih banyak
- Semua cost dan slot per level configurable di config.yml
- Max level configurable

### 5. CLAN SETHOME
- Set home lokasi clan, bisa dipakai semua anggota sesuai permission role
- Jumlah slot sethome naik seiring level clan
- Cooldown teleport configurable di config.yml
- GUI list sethome dengan tombol teleport + delete

### 6. CLAN HALL (OPTIONAL)
- Bisa diaktifkan/nonaktifkan via config.yml (clan-hall.enabled)
- Abstraction layer SchematicProvider interface dengan dua implementasi:
    - NBTStructureProvider — pakai Paper StructureManager built-in (zero dependency)
    - FAWEStructureProvider — pakai FAWE jika plugin terdeteksi
- Config clan-hall.engine: AUTO / NBT / FAWE
    - AUTO = deteksi FAWE dulu, fallback ke NBT otomatis
- Admin tentukan lokasi paste via command
- Lokasi Clan Hall tersimpan di database
- Jika disabled, semua fitur Clan Hall tidak muncul di GUI manapun

### 7. CLAN SHOP
Semua item shop configurable via shop.yml.

BUFF (effect ke semua member, dibeli dengan Clan Money):
- Strength, Speed, Haste, Regeneration, Invisibility
- Durasi configurable per item di shop.yml
- Member online: langsung dapat effect
- Member offline: flag tersimpan di database (tabel clan_buffs_pending),
  saat login otomatis dapat sisa durasi

CONSUMABLE (dibeli dengan Clan Money):
- Clan Banner — item dengan PDC clan tag, bisa di-place sebagai dekorasi
- Spy Scroll — reveal lokasi target player clan lain di actionbar + compass selama X menit
- Clan Announcement — broadcast MiniMessage ke seluruh server (cooldown panjang, configurable)
- Death Protection — keep inventory sekali untuk member yang mati (cooldown per player, configurable)

UTILITY UPGRADE (permanent, dibeli dengan Clan Money):
- XP Boost — multiplier XP vanilla semua member selama X menit
- Clan Shield — proteksi dari bounty selama X jam (configurable)

GUI shop harus:
- Anti-dupe: semua transaksi dicek balance SEBELUM item/effect diberikan, atomic operation
- Cooldown check sebelum purchase
- Konfirmasi pembelian (GUI confirm dengan tombol YES/NO) untuk item mahal
- Tidak bisa klik saat transaksi sedang diproses (flag per player)

### 8. BOUNTY SYSTEM
- Player clan A pasang bounty ke player clan B, bayar Vault balance
- Input jumlah bounty via CHAT bukan GUI
- Nama pemasang bounty disembunyikan di Bounty Board, hanya tampil nama clan pemasang
- Bounty Board GUI: list semua bounty aktif, bisa diambil oleh member clan lain
- Mekanisme penyelesaian:
    1. Hunter kill target
    2. Target drop player head custom (skull dengan nama + UUID target, PDC bounty marker)
    3. Hunter pickup head lalu submit via GUI atau command /qclan bounty submit
    4. Sistem validasi PDC di head — anti-dupe: head langsung di-invalidate saat submit
    5. Hunter terima balance bounty
    6. Bounty selesai, reputasi clan hunter +, log tersimpan
- Bounty expired setelah durasi configurable jika tidak diselesaikan
- Clan Shield memblokir clan dari menjadi target bounty sementara
- Satu player hanya bisa punya 1 bounty aktif dalam satu waktu
- Anti-dupe: head bounty hanya bisa disubmit sekali, langsung dihapus dari inventory saat submit

### 9. CONTRIBUTION POINTS
- Member dapat poin dari:
    - Deposit ke kas clan (per nominal configurable)
    - Menyelesaikan bounty
    - Menang war
- Bobot poin per aktivitas configurable di config.yml
- GUI Contribution Shop: tukar poin dengan:
    - Buff pribadi (strength, speed, dll durasi pendek)
    - Clan Banner item
    - Spy Scroll
    - Item configurable lainnya via shop.yml
- History kontribusi tersimpan di database
- Anti-dupe: poin deducted SEBELUM reward diberikan, rollback jika gagal

### 10. WAR SYSTEM
- Jadwal event configurable di war.yml (daily/weekly/monthly + jam mulai + hari)
- Clan bisa daftar war via command (sesuai permission role)
- Minimum member online untuk ikut war configurable di war.yml
- Saat event mulai: member online dari clan terdaftar auto-teleport ke arena
- Arena didefinisikan via /qclanadmin war setarena, disimpan di war.yml
- Format war configurable di war.yml:
    - LAST_STANDING: clan terakhir yang punya member hidup menang
    - KILL_COUNT: clan dengan kill terbanyak dalam X menit menang
- Selama war: WorldGuard region arena aktif (jika hook tersedia)
- Setelah war selesai:
    - Reputasi clan menang bertambah (configurable di war.yml)
    - Reward configurable (Vault balance, Coins, item) di war.yml
    - Broadcast hasil ke seluruh server via MiniMessage
    - Log tersimpan di database
- Anti-bug: jika player disconnect saat war, dianggap eliminated
- GUI war registration + status war

### 11. REPUTASI CLAN
- Dihitung dari bounty completed + war win, bobot configurable di config.yml
- GUI leaderboard top clan (inventory GUI)
- Jika DecentHolograms/HolographicDisplays tersedia: auto-create hologram leaderboard
- Hologram update interval configurable di config.yml

### 12. PLACEHOLDERAPI PLACEHOLDERS

Per-player placeholders:
%quantumclan_clan_name%
%quantumclan_clan_tag%
%quantumclan_clan_tag_colored%
%quantumclan_clan_level%
%quantumclan_clan_reputation%
%quantumclan_clan_money%
%quantumclan_clan_members_online%
%quantumclan_clan_members_total%
%quantumclan_clan_shield_active%
%quantumclan_clan_rank%              → rank clan si player di leaderboard (misal: #5)
%quantumclan_player_role%
%quantumclan_player_contribution%
%quantumclan_player_coins%

Leaderboard indexed placeholders (support top 1-50):
%quantumclan_top_[N]_name%           → nama clan di posisi N
%quantumclan_top_[N]_tag%            → tag clan di posisi N
%quantumclan_top_[N]_tag_colored%    → tag clan dengan warna di posisi N
%quantumclan_top_[N]_reputation%     → reputasi clan di posisi N
%quantumclan_top_[N]_level%          → level clan di posisi N
%quantumclan_top_[N]_members%        → total member clan di posisi N
%quantumclan_top_[N]_leader%         → nama leader clan di posisi N

N adalah angka 1 sampai 50. Generate via loop di PlaceholderAPI expansion.
Jika posisi N tidak ada clan (clan kurang dari N), return string kosong "".
Cache leaderboard data dan refresh setiap interval configurable di config.yml
agar tidak query database setiap kali placeholder dipanggil.

## DATABASE SCHEMA (SQLite via HikariCP)

clans:
id VARCHAR(36) PK, name VARCHAR(32) UNIQUE, tag VARCHAR(8),
tag_color VARCHAR(64), level INT DEFAULT 1, money BIGINT DEFAULT 0,
reputation INT DEFAULT 0, leader_uuid VARCHAR(36),
shield_until TIMESTAMP NULL, hall_world VARCHAR(64) NULL,
hall_x DOUBLE NULL, hall_y DOUBLE NULL, hall_z DOUBLE NULL,
created_at TIMESTAMP

clan_members:
uuid VARCHAR(36) PK, clan_id VARCHAR(36), role VARCHAR(32),
contribution_points INT DEFAULT 0, joined_at TIMESTAMP

clan_homes:
id VARCHAR(36) PK, clan_id VARCHAR(36), name VARCHAR(32),
world VARCHAR(64), x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT

clan_buffs_pending:
id VARCHAR(36) PK, uuid VARCHAR(36), effect_type VARCHAR(32),
amplifier INT, duration_seconds INT, expires_at TIMESTAMP

bounties:
id VARCHAR(36) PK, clan_id_poster VARCHAR(36), clan_id_target VARCHAR(36),
target_uuid VARCHAR(36), amount BIGINT, status VARCHAR(16),
head_claimed BOOLEAN DEFAULT FALSE, posted_at TIMESTAMP, expires_at TIMESTAMP

bounty_heads:
head_id VARCHAR(36) PK, bounty_id VARCHAR(36), submitted BOOLEAN DEFAULT FALSE

war_sessions:
id VARCHAR(36) PK, format VARCHAR(16), started_at TIMESTAMP,
ended_at TIMESTAMP NULL, winner_clan_id VARCHAR(36) NULL

war_participants:
session_id VARCHAR(36), clan_id VARCHAR(36), member_uuid VARCHAR(36),
kills INT DEFAULT 0, eliminated BOOLEAN DEFAULT FALSE

coins_ledger:
uuid VARCHAR(36), amount BIGINT, reason VARCHAR(128), timestamp TIMESTAMP

contribution_log:
uuid VARCHAR(36), clan_id VARCHAR(36), points INT,
reason VARCHAR(128), timestamp TIMESTAMP

## YAML STRUCTURE

config.yml — settings umum:
clan-creation-cost, max-name-length, max-tag-length,
teleport-cooldown, bounty-expire-hours, economy-provider,
reputation-weights, leaderboard-cache-interval,
contribution-weights, clan-hall.enabled, clan-hall.engine,
chat.format, chat.position,
level-requirements (list: level, cost, max-members, max-homes)

shop.yml — semua item clan shop + contribution shop:
clan-shop (list item: id, type, name, lore, price, duration, effect, amplifier)
contribution-shop (list item: id, type, name, lore, cost-points, reward)

war.yml — semua konfigurasi war:
schedule (type, time, day), format, duration-minutes,
min-members-online, arena (world, x, y, z, yaw, pitch),
rewards (balance, coins, items), reputation-reward

roles.yml — definisi role:
roles (list: name, display-name, color, permissions map)

messages.yml — SEMUA pesan plugin:
Semua pesan dalam MiniMessage format, dikelompokkan per fitur
(clan, bounty, war, shop, contribution, error, dll)

## ANTI-BUG & ANTI-DUPE RULES
Wajib diimplementasi di semua GUI dan transaksi:

1. Semua GUI menggunakan InventoryClickEvent dengan isCancelled(true) sebagai default
2. Flag HashMap<UUID, Boolean> per operasi untuk mencegah double-click processing
3. Semua transaksi economy: cek balance → deduct → berikan reward (bukan sebaliknya)
4. Jika pemberian reward gagal setelah deduct: rollback balance otomatis
5. Bounty head: PDC marker unik per bounty, invalidate langsung saat submit
6. GUI yang melibatkan item: shift-click, double-click, drag semua di-cancel
7. Player inventory penuh saat menerima item reward: drop di lokasi player, jangan hilang
8. Semua async database operation menggunakan CompletableFuture, callback ke main thread via Bukkit scheduler
9. Cache invalidation: update cache setelah setiap operasi write ke database
10. Saat plugin reload/disable: semua war session aktif di-end gracefully, data disimpan
11. Chat input listener: auto-expire setelah 60 detik jika player tidak input apapun
12. Chat input listener: cancel jika player logout sebelum input selesai

## COMMANDS

/qclan create — input nama + tag via chat
/qclan invite <player>
/qclan kick <player>
/qclan leave
/qclan info [nama_clan]
/qclan home [nama]
/qclan sethome <nama>
/qclan delhome <nama>
/qclan deposit <amount>
/qclan shop
/qclan bounty place <player> — input amount via chat
/qclan bounty board
/qclan bounty submit
/qclan war register
/qclan war leave
/qclan upgrade
/qclan top
/qclan role set <player> <role>
/qclan transfer <player>
/qclan disband
/qclan announce <pesan>
/qclan contribution

/qclanadmin give coins <player> <amount>
/qclanadmin setarena
/qclanadmin war start
/qclanadmin war end
/qclanadmin reload
/qclanadmin clan info <nama>
/qclanadmin clan delete <nama>
/qclanadmin clan setlevel <nama> <level>
/qclanadmin clan setreputation <nama> <amount>

## PERFORMANCE & OPTIMIZATION NOTES
Wajib diperhatikan di setiap implementasi:

DATABASE:
- Semua query ke database wajib async (CompletableFuture + HikariCP thread pool)
- Jangan pernah query database di main thread
- Gunakan batch insert/update untuk operasi bulk (misal saat war end, update kills semua participant sekaligus)
- Tambahkan index di kolom yang sering di-WHERE: clan_id, uuid, status, expires_at
- Prepared statement selalu digunakan, jangan string concatenation SQL

CACHING:
- Clan data dan member data wajib di-cache di memory (HashMap), jangan query tiap operasi
- Leaderboard top 50 di-cache dan refresh per interval (configurable), bukan query tiap placeholder dipanggil
- Cache pending buffs per UUID saat player login, jangan query tiap tick
- Spy scroll target location di-cache, update per 3 detik bukan per tick

SCHEDULER & TICK:
- Jangan gunakan repeating task per tick (period: 1) kecuali benar-benar perlu
- Spy scroll update: period 60 ticks (3 detik)
- Hologram leaderboard update: period configurable, default 6000 ticks (5 menit)
- War kill count broadcast: period 200 ticks (10 detik)
- Buff pending check saat player login saja, bukan scheduler terus-menerus
- Bounty expiry check: period 1200 ticks (1 menit), bukan per tick

LISTENER:
- Unregister listener yang tidak dibutuhkan jika fitur disabled di config
- Spy scroll listener hanya aktif jika ada scroll aktif (cek hashmap kosong dulu)
- Chat input listener hanya aktif per-player yang sedang input, bukan global always-on

MEMORY:
- Hapus entry dari cache saat player logout (jangan biarkan accumulate)
- Hapus pending chat input saat player logout
- Hapus spy scroll session saat player logout atau expired
- Gunakan WeakReference atau cleanup scheduler untuk data temporary

GUI:
- Jangan generate GUI dari database setiap kali dibuka — gunakan cache
- Bounty board GUI: refresh dari cache, bukan query tiap open
- Top clan GUI: gunakan leaderboard cache yang sama dengan placeholder

GENERAL:
- Hindari nested async yang tidak perlu
- Log hanya di level WARNING/SEVERE untuk error production, jangan spam INFO di operasi normal
- Gunakan lazy initialization untuk fitur opsional yang mungkin tidak dipakai

## OUTPUT

Generate file per file dengan urutan berikut, tunggu konfirmasi "lanjut" sebelum file berikutnya:

1. pom.xml (lengkap dengan semua dependency, shade + relocation HikariCP)
2. plugin.yml
3. Struktur folder Maven (tampilkan sebagai tree)
4. config.yml, shop.yml, war.yml, roles.yml, messages.yml (default values semua)
5. HookManager.java
6. SchematicProvider.java interface + NBTStructureProvider + FAWEStructureProvider
7. EconomyProvider.java interface + VaultProvider + PlayerPointsProvider + GemsEconomyProvider + CoinsProvider
8. DatabaseManager.java (HikariCP setup + semua CREATE TABLE + index)
9. Model classes: Clan.java, ClanMember.java, ClanRole.java, BountyEntry.java, WarSession.java
10. DAO classes: ClanDAO, MemberDAO, BountyDAO, WarDAO, CoinsDAO, ContributionDAO
11. ClanManager.java (cache + CRUD + leaderboard cache)
12. QuantumClan.java (main class, onEnable, onDisable, semua hook init)
13. ChatInputManager.java (handle semua chat input dengan auto-expire + cancel on logout)
14. Listener classes satu per satu
15. GUI classes satu per satu
16. Command classes satu per satu
17. Module classes: BountyManager, WarManager, WarScheduler, ClanShopManager, BuffTracker, ContributionManager, SpyScrollManager, QuantumClanPlaceholder

Jangan skip file apapun. Setiap file harus production-ready, fully implemented, tidak ada placeholder comment seperti "// TODO" atau "// implement this". Kalau file terlalu panjang split dengan bilang "lanjut?".