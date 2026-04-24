# ⚔️ QuantumClan

**The ultimate production-ready Clan system for modern Paper 1.21+ servers.**

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue?style=for-the-badge&logo=polkadot)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![API](https://img.shields.io/badge/API-JitPack-green?style=for-the-badge)](https://jitpack.io/#bintanqq/QuantumClan)
[![License](https://img.shields.io/badge/License-MIT-red?style=for-the-badge)](LICENSE)

## 💎 The Quantum Advantage

Why settle for a basic clan plugin when you can have a full-scale social and tactical ecosystem? Here is what makes **QuantumClan** the #1 choice:

* **Immersive Chat-First UX**: Forget clicking through endless GUI menus just to name your clan. QuantumClan uses sleek, timed chat-input prompts for a modern, "roleplay-like" experience that keeps players in the game.
* **Dynamic Diplomacy**: It's not just "us vs. them." Forge strategic **Alliances** to rule the server together or declare **Rivalries** to turn the world into a personal hunting ground.
* **The Physical-Virtual Hybrid**: We bridge the gap between virtual commands and physical survival. Link your **Clan Vault** to an actual block in your **Clan Hall**—accessing shared loot has never felt so grounded.
* **Tactical Warfare**: With **Spy Scrolls**, **War Schedulers**, and relationship-based combat, scouting and preparation actually matter.
* **Triple-Economy & RPG Progression**: Native integration for **Vault**, **PlayerPoints**, and its own **Premium Coins**. Members can buy clan-wide RPG buffs and upgrades that make the whole team stronger.

---

## 🚀 Key Highlights

* **Alliances & Rivalries**: Build a network of allies for protection or mark enemies as rivals. Includes automated friendly-fire protection for allies and combat bonuses against rivals.
* **Clan Hall & Physical Vaults**: Build a physical home base for your clan. Link your shared vault to a physical chest in your Hall for an immersive survival experience.
* **Tactical Spy Scrolls**: Become a master tracker. Use Spy Scrolls to hunt down enemies with a real-time compass and location tracking on your action bar.
* **Clan-Wide Potion Buffs**: Level up your entire team at once! Purchase Haste, Strength, or XP Boosts that apply to every online member instantly.
* **The Bounty Board**: Put a price on your rivals' heads. Collect player skulls to claim rewards from the shared treasury.
* **Automated Clan Wars**: Join epic team battles. Our automated scheduler handles registration, arena teleports, and reward distribution without admin intervention.

---

## ✨ Everything You Need

| Feature | What makes it awesome? |
| :--- | :--- |
| **Diplomacy System** | Manage **Alliances** and **Rivalries**. Coordinate with friends or target your enemies. |
| **Friendly Fire** | Smart protection! Automatically prevents hitting allies while allowing no-mercy combat with rivals. |
| **Clan Levels** | Unlock more member slots, multiple homes, and unique perks as your clan grows. |
| **Shared Treasury** | A common bank account where members deposit funds for upgrades and bounties. |
| **Custom Roles** | Create a hierarchy! Fully configurable roles (Leader, Officer, etc.) with custom permissions. |
| **Contribution Shop** | Active members earn points for helping the clan, which they can spend on private rewards. |
| **Spying System** | Use magical scrolls to track any player's exact coordinates and world in real-time. |
| **Clan Vault** | A massive shared inventory for members to store items and trade gear safely. |
| **Global Leaderboards** | Compete for the top spot! Track reputation, level, and power against other clans. |
| **Rich Visuals** | Stunning colored tags, gradient messages, and hoverable tooltips powered by MiniMessage. |

---

## ⚙️ Commands & Permissions

### 👤 Player Commands (`/qclan` | `/clan`)
*Permission: `quantumclan.use`*

| Command | Description |
| :--- | :--- |
| `/qclan ally <add/remove/list>` | Manage your clan's diplomatic alliances. |
| `/qclan rival <add/remove/list>` | Declare or remove rivalries with other clans. |
| `/qclan create` | Create a clan (Interactive prompts). |
| `/qclan invite <player>` | Invite a player to your clan. |
| `/qclan info [clan]` | View statistics of your clan or another. |
| `/qclan home [name]` | Teleport to a clan home. |
| `/qclan sethome <name>` | Set a new home at your location. |
| `/qclan vault` | Access the clan's virtual inventory. |
| `/qclan shop` | Access the buff and upgrade shop. |
| `/qclan bounty place` | Start a bounty on a target. |
| `/qclan war register` | Sign up for the next clan war. |
| `/qclan top` | Open the clan leaderboard GUI. |
| `/qclan disband` | Permanently delete the clan (Leader only). |

### 🛠️ Admin Commands (`/qclanadmin` | `/clanadmin`)
*Permission: `quantumclan.admin`*

| Command | Console? | Description |
| :--- | :--- | :--- |
| `/qclanadmin reload` | ✅ | Reload all config, messages, and GUI files. |
| `/qclanadmin forceally <c1> <c2>`| ✅ | Force two clans into an alliance. |
| `/qclanadmin clan delete <clan>` | ✅ | Force-disband a clan. |
| `/qclanadmin clan setlevel <c> <l>`| ✅ | Change a clan's level instantly. |
| `/qclanadmin setvaultblock` | ❌ | Link the targeted block to the virtual vault. |
| `/qclanadmin war start/end` | ✅ | Manually control the war cycle. |
| `/qclanadmin addnpc <type>` | ❌ | Add a hall NPC (Shop, War, etc.). |

---

## 📋 PlaceholderAPI Integration

### 👤 Per-Player Placeholders
*Use `%quantumclan_<identifier>%`*

* `clan_name`: The name of the player's clan.
* `clan_tag_colored`: The formatted/colored tag.
* `clan_alliances_count`: Number of active alliances.
* `clan_rivals_count`: Number of active rivals.
* `clan_level`: Current clan level.
* `clan_money`: Shared treasury balance.
* `player_role`: The player's role name.
* `player_contribution`: Total contribution points earned.

---

## 👨‍💻 Developer API

### Available Events
*All events are under `me.bintanq.quantumclan.api.event`*

| Event | Description |
| :--- | :--- |
| `ClanAllyEvent` | Fired when two clans form a formal alliance. |
| `ClanRivalEvent` | Fired when a rivalry is declared or removed. |
| `ClanCreateEvent` | Fired when a new clan is founded. |
| `ClanDisbandEvent` | Fired when a clan is permanently deleted. |
| `ClanLevelUpEvent` | Fired when a clan reaches a new level. |
| `WarStartEvent` | Fired when a war session becomes active. |
| `BountyCompletedEvent` | Fired when a bounty is successfully claimed. |

---

## 📄 License
QuantumClan is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

Developed with ❤️ by **bintanqq**