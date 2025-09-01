# Boosted – Usage & Docs

## Commands

### /boost <player|@s|@a|@all> <name> <duration>
- **player**: one online player name
- **@s**: the command sender (must be a player)
- **@a**: all online players without `boosted.bypass`

`<name>` is a key under `boosts:` in the config.  
`<duration>` supports mixed units like `90s`, `10m`, `1h30m`, `2d4h`.

Examples:
- `/boost @s vip 30m`
- `/boost @a mcmmo2x 1h`
- `/boost Notch vip 2h30m`

### /unboost <name>
Ends **all** active instances of the named boost immediately for all players (removes the configured LP group and runs end commands/message).

### /boost reload
Reloads `config.yml`. Existing boosts keep running with their original end times.

## Config Format

```yaml
boosts:
  vip:
    group: "vip"
    global_start_message: "&a%player% activated &e%boost_name% &afor &e%duration%&a!"
    global_end_message: "&c%player%'s &e%boost_name% &chas ended."
    commands:
      - start: "say Enjoy your VIP boost, %player%!"
        end: "say %player%'s VIP session is over."
  mcmmo2x:
    group: "mcmmo_boost"
    global_start_message: "&b%player% started &e%boost_name% &bfor &e%duration%&b!"
    global_end_message: "&7The &e%boost_name% &7for &e%player% &7has expired."
    commands:
      - start: "mmoxp multiplier add %player% 2 %duration%"
        end: "mmoxp multiplier remove %player% 2"
```

### Placeholders
- `%player%`, `%player_uuid%`, `%boost_name%`, `%duration%`

### LuckPerms Integration
We execute console commands:
- `lp user <uuid> parent addtemp <group> <duration> accumulate`
- On end (and on `/unboost`): `lp user <uuid> parent remove <group>`

Using a temp parent ensures removal even if the server restarts.

## Bypass
Players with `boosted.bypass` are never affected by `/boost` (including `@a`).

## Data File
Active boosts are tracked in `plugins/Boosted/active_boosts.yml`. If the server restarts, the plugin reschedules ends and still fires end messages/commands.


### /boostlist
Shows a **grouped** summary: each boost name with player count and the soonest remaining time. Use `/boostlist <name>` to expand that group and see each player + remaining time.


**Target aliases:**
- `@a` = all online players (respects `boosted.bypass`)
- `@all` = online **and** offline players (online still respects `boosted.bypass`; offline players are included without a bypass check)


- When LuckPerms is present, `@all` also **respects `boosted.bypass` for offline users** by checking their stored permission data.
- Without LuckPerms available, offline players are included (bypass cannot be checked).