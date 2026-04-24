# ⚔️ QuantumClan

**The ultimate production-ready Clan system for modern Paper 1.21+ servers.**

[![Paper](https://img.shields.io/badge/Paper-1.21.x-blue?style=for-the-badge&logo=polkadot)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![API](https://img.shields.io/badge/API-JitPack-green?style=for-the-badge)](https://jitpack.io/#bintanqq/QuantumClan)
[![License](https://img.shields.io/badge/License-MIT-red?style=for-the-badge)](LICENSE)

## 💎 The Quantum Advantage

Why settle for a basic clan plugin when you can have a full-scale social and tactical ecosystem? Here is what makes **QuantumClan** the #1 choice:

*   **Immersive Chat-First UX**: Forget clicking through endless GUI menus just to name your clan. QuantumClan uses sleek, timed chat-input prompts for a modern, "roleplay-like" experience that keeps players in the game.
*   **The Physical-Virtual Hybrid**: We bridge the gap between virtual commands and physical survival. Link your **Clan Vault** to an actual block in your **Clan Hall**—accessing your shared loot has never felt so real.
*   **Tactical Warfare**: We don't just "teleport players to an arena." Our **Spy Scrolls** and **War Scheduler** create a living, breathing tactical environment where scouting and preparation actually matter.
*   **Triple-Economy & RPG Progression**: Why limit yourself to just one balance? QuantumClan natively integrates **Vault**, **PlayerPoints**, and its own **Premium Coins**. Members can buy clan-wide RPG buffs, upgrades, and consumables that make the whole team stronger.
*   **Complete Performance**: No more "laggy clan lists." Our asynchronous data engine ensures that even with hundreds of clans and thousands of members, your server stays buttery smooth.

---

## 🚀 Key Highlights

*   **Clan Hall & Physical Vaults**: Build a physical home base for your clan. Link your shared vault to a physical chest in your Hall for an immersive survival experience.
*   **Tactical Spy Scrolls**: Become a master tracker. Use Spy Scrolls to hunt down enemies with a real-time compass and location tracking on your action bar.
*   **Clan-Wide Potion Buffs**: Level up your entire team at once! Purchase Haste, Strength, or XP Boosts that apply to every online member instantly.
*   **The Bounty Board**: Put a price on your rivals' heads. Collect player skulls to claim rewards from the shared treasury.
*   **Automated Clan Wars**: Join epic team battles. Our automated scheduler handles registration, arena teleports, and reward distribution without admin help.
*   **Insurance & Protection**: Stay safe with **Death Protection**. Purchase one-time insurance to keep your items or XP safe even if you fall in battle.

---

## ✨ Everything You Need

| Feature | What makes it awesome? |
| :--- | :--- |
| **Clan Levels** | Unlock more member slots, multiple homes, and unique perks as your clan grows. |
| **Shared Treasury** | A common bank account where members deposit funds for upgrades and bounties. |
| **Custom Roles** | Create a hierarchy! Fully configurable roles (Leader, Officer, etc.) with custom permissions. |
| **Contribution Shop** | Active members earn points for helping the clan, which they can spend on private rewards. |
| **Spying System** | Use magical scrolls to track any player's exact coordinates and world in real-time. |
| **Clan Vault** | A massive shared inventory for members to store items and trade gear safely. |
| **Premium Coins** | A secondary "donor" currency to buy exclusive items from the premium shop. |
| **Global Leaderboards** | Compete for the top spot! Track reputation, level, and power against other clans. |
| **Pro Interaction** | Chat-based clan creation and bounty placement—no clunky typing in GUIs. |
| **Rich Visuals** | Stunning colored tags, gradient messages, and hoverable tooltips powered by MiniMessage. |

---

## ⚙️ Commands & Permissions

### 👤 Player Commands (`/qclan` | `/clan`)
*Permission: `quantumclan.use`*

| Command | Description |
| :--- | :--- |
| `/qclan` | Open the main menu GUI. |
| `/qclan menu` | Alias to open the main menu. |
| `/qclan create` | Create a clan (Interactive prompts). |
| `/qclan invite <player>` | Invite a player to your clan. |
| `/qclan accept` | Accept a pending invitation. |
| `/qclan decline` | Decline a pending invitation. |
| `/qclan kick <player>` | Kick a member (Requires appropriate role). |
| `/qclan leave` | Leave your current clan. |
| `/qclan info [clan]` | View statistics of your clan or another. |
| `/qclan home [name]` | Teleport to a clan home. |
| `/qclan sethome <name>` | Set a new home at your location. |
| `/qclan delhome <name>` | Remove an existing home. |
| `/qclan deposit <amt>` | Contribute money to the shared treasury. |
| `/qclan vault` | Access the clan's virtual inventory. |
| `/qclan shop` | Access the buff and upgrade shop. |
| `/qclan coins` | Open the premium Coins shop. |
| `/qclan contribution` | Open the contribution point shop. |
| `/qclan bounty place` | Start a bounty on a target. |
| `/qclan bounty board` | View active bounties. |
| `/qclan bounty submit` | Claim a bounty with a target's head. |
| `/qclan war register` | Sign up for the next clan war. |
| `/qclan war leave` | Withdraw registration from the war. |
| `/qclan upgrade` | Level up your clan (Increases slots/homes). |
| `/qclan top` | Open the clan leaderboard GUI. |
| `/qclan role set <p> <r>`| Manage member ranks. |
| `/qclan transfer <p>` | Hand over leadership to another member. |
| `/qclan disband` | Permanently delete the clan (Leader only). |
| `/qclan announce <msg>` | Broadcast a message to the entire server. |

### 🛠️ Admin Commands (`/qclanadmin` | `/clanadmin`)
*Permission: `quantumclan.admin`*

| Command | Console? | Description |
| :--- | :--- | :--- |
| `/qclanadmin reload` | ✅ | Reload all config, messages, and GUI files. |
| `/qclanadmin give coins <p> <n>` | ✅ | Grant premium coins to a player. |
| `/qclanadmin coins <give/take/set>`| ✅ | Manage player premium coin balances. |
| `/qclanadmin clan info <clan>` | ✅ | View admin stats of any clan. |
| `/qclanadmin clan delete <clan>` | ✅ | Force-disband a clan. |
| `/qclanadmin clan setlevel <c> <l>`| ✅ | Change a clan's level instantly. |
| `/qclanadmin clan setcoins <c> <n>`| ✅ | Set a clan's premium coin balance. |
| `/qclanadmin clan setpts <c> <n>` | ✅ | Set a clan's total reputation points. |
| `/qclanadmin vault clear <clan>` | ✅ | Clear all items from a clan's vault. |
| `/qclanadmin hall grant <clan>` | ✅ | Grant hall access to a specific clan. |
| `/qclanadmin hall revoke <clan>` | ✅ | Revoke hall access from a clan. |
| `/qclanadmin hall listnpc` | ✅ | List all NPC spawn points. |
| `/qclanadmin hall reload` | ✅ | Reload only the clan hall configurations. |
| `/qclanadmin setvaultblock` | ❌ | Link a block to the virtual vault. |
| `/qclanadmin clearvaultblock` | ✅ | Unlink the physical vault block. |
| `/qclanadmin setarena` | ❌ | Set the war spawn/arena location. |
| `/qclanadmin war start/end` | ✅ | Manually control the war cycle. |
| `/qclanadmin addnpc <type> [name]` | ❌ | Add a hall NPC (Shop, War, etc.). |
| `/qclanadmin removenpc <id>` | ❌ | Remove an NPC point. |

---

## 📋 PlaceholderAPI Integration

### 👤 Per-Player Placeholders
*Use `%quantumclan_<identifier>%`*

*   `clan_name`: The name of the player's clan.
*   `clan_tag`: The raw tag of the clan.
*   `clan_tag_colored`: The formatted/colored tag.
*   `clan_level`: Current clan level.
*   `clan_reputation`: Total reputation points.
*   `clan_money`: Shared treasury balance.
*   `clan_members_online`: Count of online members.
*   `clan_members_total`: Total member count.
*   `clan_shield_active`: Returns `true`/`false`.
*   `clan_rank`: The leaderboard rank (e.g. #1).
*   `player_role`: The player's role name.
*   `player_contribution`: Total contribution points earned.
*   `player_coins`: Player's premium coins balance.

### 🏆 Leaderboard Placeholders
*Use `%quantumclan_top_<N>_<field>%` (N = 1 to 50)*

*   `name`, `tag`, `tag_colored`, `reputation`, `level`, `members`, `leader`

---

## 👨‍💻 Developer API

QuantumClan provides a rich set of events and interfaces for deep integration.

### Available Events
*All events are under `me.bintanq.quantumclan.api.event`*

| Event | Description |
| :--- | :--- |
| `ClanCreateEvent` | Fired when a new clan is founded. |
| `ClanDisbandEvent` | Fired when a clan is permanently deleted. |
| `ClanJoinEvent` | Fired when a player joins a clan. |
| `ClanLeaveEvent` | Fired when a player leaves or is kicked. |
| `ClanLevelUpEvent` | Fired when a clan reaches a new level. |
| `ClanReputationChangeEvent` | Fired when clan reputation points change. |
| `BountyCompletedEvent` | Fired when a bounty is successfully claimed. |
| `WarStartEvent` | Fired when a war session becomes active. |
| `WarEndEvent` | Fired when a war session concludes. |

---

## 📄 License
QuantumClan is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

Developed with ❤️ by **bintanqq**
--