# XLobby

Minimal, performance-focused lobby essentials for Paper servers.

XLobby provides the features most lobby servers need without turning into a scoreboard, NPC, cosmetics, economy, or server-selector suite. Every protection is optional, expensive work is avoided on hot paths, and existing configurations are regenerated safely when new options are added.

## Features

- `/lobby` with an optional `/spawn` alias, usable from any world
- One safe spawn for commands, join teleporting, respawning, and void rescue
- Join teleport modes: disabled, first join, or every join
- Configurable teleport warmup and cooldown
- Optional warmup cancellation when the player moves or takes damage
- Safe-spawn validation and asynchronous chunk loading
- First-join and returning-player messages, titles, sounds, broadcasts, and harmless fireworks
- Rotating Adventure bossbar
- Lightweight, world-scoped lobby protection
- Separate denial messages for breaking, placing, dropping, and picking up items
- MiniMessage, built-in placeholders, and optional PlaceholderAPI support
- Automatic config regeneration, validation, and invalid-YAML backups
- Administrative diagnostics and filtered tab completion

Lobby protections are independently disabled by default. The world filter scopes lobby-only features such as protection, void rescue, bossbars, and welcome presentation; it never prevents players from using `/lobby` to enter the lobby from another world.

## Requirements

- Java 21
- Paper 1.21.4 or a compatible 1.21.x release
- PlaceholderAPI 2.11.6 or newer (optional)

XLobby compiles against Paper 1.21.4. Test new releases on a staging server before using them in production.

## Installation

1. Download or build `XLobby-1.0.0.jar`.
2. Place it in the server's `plugins` directory.
3. Start the server.
4. Stand at the lobby destination and run `/xlobby setspawn`.
5. Configure `plugins/XLobby/config.yml` and run `/xlobby reload`.

XLobby regenerates its configuration during startup, reload, `/xlobby check`, and `/xlobby setspawn`. Existing values and custom administrator keys are preserved, missing bundled entries are added, invalid values are repaired, and malformed YAML is backed up before a clean configuration is generated.

## Commands and permissions

| Command | Purpose | Permission |
|---|---|---|
| `/lobby`, `/spawn` | Teleport to the lobby | `xlobby.lobby` |
| `/xlobby reload` | Regenerate, validate, and reload the config | `xlobby.command.reload` |
| `/xlobby setspawn` | Set the lobby destination | `xlobby.command.setspawn` |
| `/xlobby check` | Regenerate the config and validate the spawn | `xlobby.command.check` |
| `/xlobby info` | Show the version, spawn, modules, and integrations | `xlobby.command.info` |

`xlobby.admin` grants every administrative command and both bypass permissions.

| Permission | Purpose | Default |
|---|---|---|
| `xlobby.lobby` | Use `/lobby` and `/spawn` | Everyone |
| `xlobby.bypass` | Bypass void rescue | No one |
| `xlobby.protection.bypass` | Bypass player-facing lobby protection | Operators |

## Configuration

The generated configuration documents every setting:

- `spawn` — shared lobby destination
- `world-filter` — worlds receiving lobby-only modules
- `lobby-command` — warmup, cooldown, and cancellation
- `respawn` — optional lobby respawning
- `void-check` — event-driven void rescue
- `join-actions` — teleport mode and presentation
- `bossbar` — rotating messages and appearance
- `protections` — independently toggleable restrictions
- `messages` — player-facing MiniMessage text

Player-aware text supports `<player>`, `<displayname>`, `<world>`, `<online>`, `<max_players>`, `<spawn_world>`, `<spawn_x>`, `<spawn_y>`, and `<spawn_z>`. PlaceholderAPI `%placeholder%` syntax works when PlaceholderAPI is installed.

Protection denial messages have a shared one-second anti-spam cooldown. Set an individual denial message to an empty string to disable that notification without disabling its protection.

## Building

```bash
mvn clean verify
```

The release jar is created at `target/XLobby-1.0.0.jar`.

## Contributing

Bug reports and focused pull requests are welcome. Keep additions within XLobby's small lobby-core scope and include regression tests for behavior changes.

## License

XLobby is released under the [MIT License](LICENSE).

Created by [xDele1ed](https://github.com/xdele1edmc14).
