# PremiumShop

**PremiumShop** is an enterprise-grade virtual shop and auction house plugin built for high-performance **Paper** and **Purpur** Minecraft servers (supporting versions **1.21.x through 26.3**).

---

## ✨ Features

- **Multi-Version Compatibility (1.21.x – 26.3)**:
  - Built with Paper's modern native item serialization (`ItemStack#serializeAsBytes()`).
  - Adventure MiniMessage and native `Component` formatting for text & GUIs.
  - Multi-sided Sign support (`sign.getSide(Side.FRONT)`).
- **Dual Economy Engines**:
  - Full support for **Vault** economies.
  - Native custom **Item Currency** system with custom model data support.
- **Dynamic Price Engine**:
  - Automatically adjusts item buy/sell prices based on transaction velocity and market demand.
  - Periodic persistence into database history.
- **Enterprise Auction House**:
  - Synchronized auction bidding and buyout logic eliminating race conditions and duplication exploits.
  - Automatic expiration and offline item/currency claim system.
  - Built-in pagination, search filtering, and administration tools.
- **In-Game GUI & Configuration**:
  - Interactive In-Game Editor (`/shopadmin editor <shop> <category>`).
  - Click debounce protection on confirm menus to prevent double-spend bugs.
- **Database Storage**:
  - Supports embedded **SQLite** (zero configuration) and remote **MySQL** connection pooling via HikariCP.
  - Automatic schema versioning and database migrations.

---

## 🚀 Commands & Permissions

| Command | Description | Default Permission |
| :--- | :--- | :--- |
| `/shop [category] [page]` | Opens the main shop interface | `premiumshop.shop` |
| `/auction` | Opens the auction house interface | `premiumshop.auction` |
| `/auction list <price> [buyout]` | Lists held item on auction house | `premiumshop.auction.list` |
| `/auction bid <id> <amount>` | Places a bid on an active listing | `premiumshop.auction.bid` |
| `/auction cancel <id>` | Cancels your own active listing | `premiumshop.auction` |
| `/auction claim` | Claims won items or earned proceeds | `premiumshop.auction` |
| `/shopadmin reload` | Reloads configurations and shops | `premiumshop.admin` |
| `/shopadmin editor <shop> <cat>` | Opens in-game GUI editor | `premiumshop.admin.editor` |
| `/shopadmin setprice ...` | Adjusts base buy/sell prices | `premiumshop.admin` |
| `/shopadmin setstock ...` | Adjusts item stock levels | `premiumshop.admin` |
| `/shopadmin resetprices <shop>` | Resets dynamic pricing to base | `premiumshop.admin` |
| `/shopadmin auctionban <player>`| Bans/unbans player from auctions | `premiumshop.admin` |
| `/shopadmin info` | Displays runtime statistics | `premiumshop.admin` |

---

