# Price Share protocol (v1)

Everything the mod sends, and where. The site (DonutMarket, https://donutmarket.cc) treats every
upload as untrusted: the checks that stop fake prices are on the site, not in this mod, because
anyone can read and change open-source code.

## What is read

Only while connected to `donutsmp.net` (or a subdomain), and only these menus, matched on the
plain-text title of an open chest menu:

| kind     | title                         |
|----------|-------------------------------|
| `ah`     | `Auction (Page N)`            |
| `orders` | `Orders (Page N)`             |
| `shop`   | `Quick Buy`                   |
| `baltop` | `Most Money (Page N)`         |
| `bounty` | `Bounties (Page N)`           |

Titles are compared as plain text: formatting codes removed, small-caps letters (`ᴀ`, `ʙ`, …)
folded to plain ones, case ignored. Otherwise the shape must match exactly.

Plus DonutSMP's full leaderboards (`/leaderboard`), which are server-sent **dialogs**, not chest
menus: kind `board`, sent when an open dialog's body has more than 10 plain-message entries.

Any other menu or dialog, chat, the world, other players: never read. The mod never sends a
command, clicks a slot or a dialog button, or opens a menu. It only reads what the player opened.

A page is captured once its slots have stopped changing for 2 client ticks (the slots arrive in
a packet after the menu opens). The same page (same kind + same slot contents) is not sent twice
within 60 s. `shop` pages are captured at most once every 30 s while open (Quick Buy refreshes
its prices live). Slots holding the player's own inventory (index >= rows*9) are never read.
A leaderboard dialog is read once it has been open for 2 ticks; the same dialog content is not
sent twice within 60 s.

## Identity: proving a real Minecraft account

So one person can't pose as many, every contributor is a real, paid Minecraft account, checked
the same way a Minecraft server checks a player joining it. The player's Mojang/Microsoft token
never leaves the game: it only goes to Mojang, exactly as on every server join.

1. `POST /api/share/challenge` `{ "name": "<player name>" }` (the mod also sends `uuid`; the site ignores it)
   → `{ "serverId": "<40 hex chars>", "expiresIn": 60 }`
2. The mod calls Mojang's join endpoint with the player's session and that `serverId`
   (the game's own `MinecraftSessionService.joinServer`).
3. `POST /api/share/login` `{ "name": "<player name>", "serverId": "<same>" }`
   → the site asks Mojang `hasJoined` and, if it matches, replies
   `{ "token": "<opaque>", "uuid": "...", "name": "..." }`.

The token is stored in `config/priceshare/token` as `{"uuid": ..., "token": ...}` and sent as
`Authorization: Bearer <token>`. It is only used while that same account is playing: after an
account switch in the launcher the mod logs in again as the new account.
It can only upload pages; it is not a DonutMarket login. If an upload answers 401 the mod forgets
the token and logs in again, keeping that batch for after.

Retries: if Mojang refuses the join (often HTTP 429, too many joins) or the site can't be
reached, the next attempt waits 90 s, then 3 min, then 10 min at most; a successful login resets
this. If the site refuses the challenge or login (4xx), the next attempt waits 10 min. Pages
captured meanwhile wait in memory, the 30 newest and none older than 10 minutes, and are sent
once the account is verified.

## Uploading

`POST /api/share/donut` with the token, batches of at most 10 pages, sent from a background
thread at most once every 5 s:

```json
{
  "v": 1,
  "mod": "1.0.0",
  "mc": "1.21.11",
  "showOnSite": false,
  "pages": [
    { "kind": "ah", "at": 1794355407381, "title": "Auction (Page 1)",
      "slots": [ { "slot": 0, "id": "diamond_sword", "count": 1, "name": "Diamond Sword",
                   "lore": ["$ 1.2M"], "enchantments": { "sharpness": 5 }, "potion": null } ] },
    { "kind": "board", "at": 1794355409000, "title": "Money",
      "rows": [ { "text": "#1", "extra": ["", "Name", "-", "70M"], "name": "Name" } ] }
  ]
}
```

A batch can mix kinds. Chest menu pages carry `slots`; `board` pages carry `rows` instead:

- `text`: the message component's own text (without its siblings), plain.
- `extra`: the plain text of each sibling component, in order (at most 16).
- `name`: the `name` string of the message's click event, when that is a `custom` click event
  with a compound payload (checked on the message, then its siblings); otherwise null.
- At most 300 rows; each string at most 100 characters. Plain text as for titles, trimmed.

For slots:

- `slot`: index in the chest part of the menu (0..rows*9-1). Controls (the bottom row) are
  included; the site uses them to recognise the menu.
- `id`: item id without the `minecraft:` namespace.
- `name`: the item's display (hover) name as plain text.
- `lore`: lore lines as plain text, formatting codes removed, in order, empty lines kept.
- `enchantments`: enchantments and stored (book) enchantments, id without namespace → level.
- `potion`: the potion id (`strong_swiftness`) for potions and tipped arrows, else null.

Reply: `{ "ok": true, "accepted": n, "wait": <ms> }`. The mod waits at least `wait` ms before
its next upload. `showOnSite` is the player's setting, sent every time; the site stores it per
account. On 429 it waits `Retry-After` seconds, or `data.retryAfter` from the JSON body when
there is no header. On any other error it drops the batch
(nothing is retried more than twice; nothing is ever stored on disk).

## Opting in and out

Off until the player says yes to a one-time prompt, shown the first time a market menu or
leaderboard opens on DonutSMP (after it closes, never over it). The prompt reads: "Share auction
prices with DonutMarket? Only the items and prices in the Auction, Orders and Quick Buy menus,
and the leaderboards you open, are sent, tied to your Minecraft name. You can turn this off any
time." Settings (Mod Menu, or `config/priceshare/config.json`):

- `enabled` — share at all (default: false until the prompt is answered)
- `showOnSite` — let DonutMarket show your name on the contributors list and a badge on your
  player page (default: false)

## What the site does with it (summary)

- Ties each upload to the verified Minecraft account; rate-limits per account and per address.
- A listing only reaches prices once a second, unrelated contributor has seen it too, or the
  contributor has a record of uploads that matched everyone else's. Prices far below the known
  floor wait for a second sighting either way.
- Accounts that send data nobody else sees are quietly ignored.
