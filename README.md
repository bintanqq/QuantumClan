# ⚔️ QuantumClan

**A feature-rich, production-ready Clan plugin for Paper 1.21.x**

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![API](https://jitpack.io/v/bintanqq/QuantumClan.svg)](https://jitpack.io/#bintanqq/QuantumClan)
---

## ✨ Features

| Category | What's included |
|---|---|
| **Clan Management** | Create, disband, invite, kick, transfer ownership — all via chat input (no GUI typing) |
| **Role System** | Fully configurable roles with per-permission nodes in `roles.yml` |
| **Economy** | Vault (primary), PlayerPoints, or built-in Coins as secondary currency |
| **Clan Leveling** | Level up with clan treasury — unlocks more member slots and homes |
| **Clan Shop** | Buffs, consumables, and utility upgrades bought with shared treasury |
| **Coins Shop** | Premium shop using the built-in Coins currency (grant via admin command or donation stores) |
| **Contribution Points** | Members earn points from deposits, bounties, and wars — redeemable in a contribution shop |
| **Bounty System** | Place bounties on players; claim by collecting their dropped skull |
| **Clan War** | Scheduled or manual wars with LAST_STANDING or KILL_COUNT formats, arena teleport, and rewards |
| **Clan Home** | Multiple homes per clan (scales with level), GUI teleport with cooldown |
| **Clan Hall** | Optional schematic engine using Paper's built-in NBT structures or FAWE |
| **PlaceholderAPI** | 20+ per-player placeholders + leaderboard top-50 placeholders |
| **Developer API** | Full API module published on JitPack — listen to events, query clan data |

---

## 📦 Installation

1. Drop `QuantumClan-x.x.x.jar` into your `plugins/` folder.
2. Install **Vault** and an economy plugin (e.g. EssentialsX).
3. Restart your server.
4. Edit `plugins/QuantumClan/config.yml` to your liking.
5. Run `/qclanadmin reload` to apply changes without a full restart.

### Optional dependencies

| Plugin | Purpose |
|---|---|
| Vault | Primary economy (required for most features) |
| PlayerPoints | Alternative economy provider |
| PlaceholderAPI | Exposes clan placeholders to other plugins |
| LuckPerms | Reads player ranks for internal permission use |
| WorldGuard | Protects the war arena (no-build, no-break) |
| FastAsyncWorldEdit | Faster Clan Hall schematic engine |

---

## ⚙️ Commands

### Player — `/qclan` (alias: `/clan`)

| Command | Description |
|---|---|
| `/qclan` | Open the main menu GUI |
| `/qclan create` | Create a new clan (prompts via chat) |
| `/qclan invite <player>` | Invite a player |
| `/qclan accept` / `decline` | Accept or decline a pending invite |
| `/qclan kick <player>` | Remove a member |
| `/qclan leave` | Leave your clan |
| `/qclan info [clan]` | View clan information (supports names with spaces) |
| `/qclan home [name]` | Teleport to a clan home |
| `/qclan sethome <name>` | Set a clan home at your location |
| `/qclan delhome <name>` | Delete a clan home |
| `/qclan deposit <amount>` | Deposit money into the clan treasury |
| `/qclan shop` | Open the clan shop |
| `/qclan coins` | Open the Coins shop |
| `/qclan contribution` | Open the contribution shop |
| `/qclan bounty place <player>` | Place a bounty (amount via chat) |
| `/qclan bounty board` | View the bounty board |
| `/qclan bounty submit` | Submit a bounty head from your hand |
| `/qclan war register` | Register your clan for war |
| `/qclan upgrade` | Upgrade your clan level |
| `/qclan top` | View the clan leaderboard |
| `/qclan role set <player> <role>` | Change a member's role |
| `/qclan transfer <player>` | Transfer clan ownership |
| `/qclan disband` | Disband the clan (leader only, with confirmation GUI) |
| `/qclan announce <message>` | Broadcast a server-wide announcement |

### Admin — `/qclanadmin` (alias: `/clanadmin`)

| Command | Description |
|---|---|
| `/qclanadmin reload` | Reload all config files |
| `/qclanadmin give coins <player> <amount>` | Grant Coins to a player |
| `/qclanadmin setarena` | Set the war arena at your location |
| `/qclanadmin war start` | Manually start a war |
| `/qclanadmin war end` | Manually end the active war |
| `/qclanadmin clan info <clan>` | View any clan's info (supports spaces in name) |
| `/qclanadmin clan delete <clan>` | Force-delete a clan |
| `/qclanadmin clan setlevel <clan> <level>` | Override a clan's level |
| `/qclanadmin clan setreputation <clan> <amount>` | Override a clan's reputation |

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `quantumclan.use` | `true` | Access to `/qclan` |
| `quantumclan.admin` | `op` | Access to `/qclanadmin` |
| `quantumclan.bypass.cooldown` | `op` | Skip all cooldowns |
| `quantumclan.bypass.cost` | `false` | Skip creation cost |
| `quantumclan.coins.shop` | `true` | Use the Coins shop |
| `quantumclan.*` | `false` | All permissions |

Full permission list is in `plugin.yml`.

---

## 📋 PlaceholderAPI

### Per-player

```
%quantumclan_clan_name%
%quantumclan_clan_tag%
%quantumclan_clan_tag_colored%
%quantumclan_clan_level%
%quantumclan_clan_reputation%
%quantumclan_clan_money%
%quantumclan_clan_members_online%
%quantumclan_clan_members_total%
%quantumclan_clan_shield_active%
%quantumclan_clan_rank%
%quantumclan_player_role%
%quantumclan_player_contribution%
%quantumclan_player_coins%
```

### Leaderboard (N = 1–50)

```
%quantumclan_top_1_name%
%quantumclan_top_1_tag_colored%
%quantumclan_top_1_reputation%
%quantumclan_top_1_level%
%quantumclan_top_1_members%
%quantumclan_top_1_leader%
```

---

## 🛠 Configuration

All settings live in `plugins/QuantumClan/`:

| File | Purpose |
|---|---|
| `config.yml` | General settings — costs, cooldowns, economy provider, level data |
| `messages.yml` | Every message in MiniMessage format — no hardcoded strings |
| `gui.yml` | All GUI titles, materials, and slot positions |
| `shop.yml` | Clan shop, contribution shop, and coins shop items |
| `war.yml` | War schedule, format, arena, and rewards |
| `roles.yml` | Role names, colors, and per-permission nodes |

### Example: changing deposit contribution unit

```yaml
# config.yml
contribution-weights:
  deposit-per-unit: 1       # points awarded per unit
  deposit-amount-unit: 100  # 1 point per 100 deposited (was 1000)
```

### Example: customising the Coins currency name

```yaml
# config.yml
coins-name: "Gems"
```

---

## 👨‍💻 Developer API

QuantumClan ships a separate `QuantumClan-API` module published on JitPack.

### Depend on the API

**Maven**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.bintanq.QuantumClan</groupId>
        <artifactId>QuantumClan</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin DSL)**
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.bintanq.QuantumClan:QuantumClan-API:v1.0.0")
}
```

Add QuantumClan as a soft-depend in your `plugin.yml`:
```yaml
softdepend:
  - QuantumClan
```

### Accessing the API

```java
if (QuantumClanProvider.isReady()) {
    QuantumClanAPI api = QuantumClanProvider.getAPI();

    // Query a player's clan
    ClanAPI clan = api.getClan(player);
    if (clan != null) {
        player.sendMessage("You are in: " + clan.getName());
        player.sendMessage("Level: " + clan.getLevel());
        player.sendMessage("Reputation: " + clan.getReputation());
    }

    // Get the leaderboard top 10
    List<? extends ClanAPI> top10 = api.getLeaderboard(10);

    // Check if war is running
    if (api.isWarActive()) {
        WarSessionAPI war = api.getActiveWar();
        war.getRegisteredClanIds().forEach(id -> { /* ... */ });
    }

    // Grant Coins
    api.grantCoins(player.getUniqueId(), 500, "donation-store")
       .thenAccept(success -> {
           if (success) player.sendMessage("500 Coins granted!");
       });
}
```

### Listening to events

```java
@EventHandler
public void onClanCreate(ClanCreateEvent e) {
    Bukkit.broadcast(
        Component.text(e.getCreator().getName() + " created clan " + e.getClan().getName())
    );
}

@EventHandler
public void onWarEnd(WarEndEvent e) {
    if (e.hasWinner()) {
        Bukkit.broadcast(Component.text("War winner: " + e.getWinner().getName()));
    }
}
```

### Available events

| Event | When it fires |
|---|---|
| `ClanCreateEvent` | After a new clan is successfully created |
| `ClanDisbandEvent` | After a clan is disbanded |
| `ClanJoinEvent` | After a player joins a clan |
| `ClanLeaveEvent` | After a player leaves / is kicked (includes `Reason` enum) |
| `ClanLevelUpEvent` | After a clan upgrades its level |
| `ClanReputationChangeEvent` | After clan reputation changes (any source) |
| `BountyCompletedEvent` | After a bounty head is submitted and reward paid |
| `WarStartEvent` | After the war transitions to ACTIVE state |
| `WarEndEvent` | After the war ends and rewards are distributed |

---

## 🏗 Building from source

```bash
git clone https://github.com/bintanq/QuantumClan.git
cd QuantumClan
mvn clean package -pl api,plugin
```

Output jar: `plugin/target/QuantumClan-x.x.x.jar`

---

## 📄 License

MIT — see [LICENSE](LICENSE).


BUG LIST
1. Ketika Upgrade level 2 chat malah level 3
2. Lore/nama gui itu masih hardcoded dan berbahasa indonesia harusnya inggris
3. Cost di coinsshop double text nya
4. Jangan ada kata KAS lagi tapi ganti jadi apa gitu kas itu terlalu indo banget
5. Clan banner aneh banget, bannernya cuman putih doang jir lah harusnya itu bisa di customize per clan gasih?
6. Annoying banget ketika kita click item di gui gitu kan misal gagal atau apa itu langsung ngeclose dan itu annoying sih contoh ketika click clan homes dan ga ada homes jangan ngeclose jir aneh
7. Dan juga di gui ini gabisa kah tombol back aja jangan close tapi kalo ngebuka dari command langsung ke gui spesifik itu gpp close aja tapi kalo dari main menu atau dari menu lain itu back biar bagus
8. masih ada chat yang ngawur misal insufficient balance, harusnya you need $1.000 ini malah you have $1.000 wtf itu pas duit kurang re check lagi kalo ada message yang  ngawur
9. add /clan help itu based on permission ngeluarin chatnya
10. pas ngetik /clanadmin itu dia random banget jir chatny  kaya simbol simbol gajelas tolong fix (kalo bisa jangan hardcoded untuk help dll)
11. POKONYA GUA MINTA SEMUA URUSAN TEXT YANG NGARUH KE PLAYER JANGAN SAMPE ADA YANG HARDCODED SAMA SEKALI

Question:
1. System Clan Hall itu gimana sih itu di set ama admin gitu kah berarti ada 1 clan hall misal di spawn dan itu bisa dipake semua clan gitu apa gmn?

Feat Add:
1. Add Opsi untuk kaya disable/enable announcement dll pokonya perbanyak opsi untuk semuanya agar lebih customizable

QA:
1. Check Semua Apakah ada unused key di semua yaml kalau ada hapus aja
2. Ganti key yang masih bahasa indonesia (Kalau ada)
3. API System Check apakah udah oke atau ada sesuatu yang rusak