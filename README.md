# Price Share

An **optional** Fabric mod that shares the DonutSMP market menus and leaderboards you open with
[DonutMarket](https://donutmarket.cc), so its prices stay up to date. Client-side only, no
dependencies (not even Fabric API), about 45 KB.

**It is off until you say yes.** The first time you open one of the menus below on DonutSMP, it
asks once (after you close that menu): *"Share auction prices with DonutMarket? Only the items
and prices in the Auction, Orders and Quick Buy menus, and the leaderboards you open, are sent,
tied to your Minecraft name. You can turn this off any time."* Answer no and it never asks again and
never sends anything.

## What it reads

Only while you're connected to `donutsmp.net`, and only these menus, recognised by their title:

- `Auction (Page N)` (`/ah`)
- `Orders (Page N)` (`/orders`)
- `Quick Buy` (`/shop`)
- `Most Money (Page N)` (`/baltop`)
- `Bounties (Page N)` (`/bounty`)
- the full leaderboards from `/leaderboard`, which DonutSMP shows as a dialog (only dialogs
  listing more than 10 entries)

From the menus it reads the items: item id, amount, display name, lore text (price, "delivered"
counts, money, bounty and so on), enchantments and potion type. From a leaderboard it reads each
line's text (rank, player name, value) and the player name the line carries. Nothing else: not chat, not your
inventory (the bottom part of the menu is never read), not the world, not other players, and
nothing at all on any other server.

## What it never does

- It never sends a command, clicks a slot or a dialog button, or opens a menu. It only looks at the menu you
  opened yourself, so to the server you are just playing normally.
- It never reads anything in the background or while the menu is closed.
- It never stores pages on your disk. If an upload fails it is dropped.

## Your Minecraft account

So that one person can't pretend to be many, DonutMarket checks each contributor is a real
Minecraft account, the same way a Minecraft server checks you when you join:

1. The mod asks DonutMarket for a one-time code.
2. The game tells **Mojang** you are "joining" that code (the game's own join call, exactly what
   happens on every server join).
3. DonutMarket asks Mojang whether that name joined that code, and if so gives the mod an upload
   token.

**Your Microsoft/Mojang login token never leaves the game except to Mojang**, as on every server
join. DonutMarket only ever sees your Minecraft name and UUID. The upload token it gives back can
only upload pages; it can't log in to anything. It's stored in `config/priceshare/token` with
your UUID, and only used while that account is playing (switch accounts and the mod verifies the
new one).

The exact requests are in [PROTOCOL.md](PROTOCOL.md).

## Settings

With [Mod Menu](https://modrinth.com/mod/modmenu) installed: Mods → Price Share → settings.
Without it, edit `config/priceshare/config.json`:

```json
{
  "asked": true,
  "enabled": true,
  "showOnSite": false
}
```

- `enabled`: share at all.
- `showOnSite`: let DonutMarket show your name on its contributors list and a badge on your
  player page. Off by default; your uploads are used either way.

Delete the `config/priceshare` folder to reset everything (the prompt shows again).

## How much it sends

A page goes out once its items have finished loading, the same page at most once a minute, and
Quick Buy at most once every 30 seconds. Uploads are batched (up to 10 pages) on one low-priority
background thread, at most every 5 seconds. A page is a few kilobytes. Until your account is
verified, at most the 30 newest pages (none older than 10 minutes) wait in memory; they're never
written to disk.

## Supported versions

One jar per Minecraft version: **1.21.11, 26.1 (26.1–26.1.2), 26.2, 26.3**. Use the one matching
your game. Needs Fabric Loader 0.16 or later.

## Building

Needs JDK 25 (and JDK 21 for 1.21.11; Gradle downloads it through a toolchain if it's missing).

```sh
./build-all.sh                     # every version into dist/
./gradlew build -Pmc=26.2          # one version, into build/libs/
```

The code that touches the game is in `src/game` (shared by every version) plus a tiny
`Screens` class that differs between versions (`src/screens-field`, `src/screens-gui`).
Everything else, `src/main`, is plain Java with no Minecraft imports: the menu titles, the JSON,
the upload queue and the account check.

Testing against a local site:

```sh
./gradlew runClient -Pmc=26.2 -PpsArgs="-Dpriceshare.url=http://localhost:3000 -Dpriceshare.anyServer=true"
```

`priceshare.anyServer` reads menus with those titles on any server: for testing only.

## License

MIT. See [LICENSE](LICENSE).
