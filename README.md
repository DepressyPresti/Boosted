# Boosted

Boosted is a Paper (1.21.x) plugin that gives **temporary permission groups** and runs **paired start/end commands** for a configured duration.

- **/boost &lt;player|@s|@a&gt; &lt;name&gt; &lt;duration&gt;** – applies the configured LuckPerms group + runs start commands, schedules end commands.
- **/unboost &lt;name&gt;** – ends all active instances of a boost early.
- **/boost reload** – reloads `config.yml`.

### Permissions

- `boosted.admin` – use plugin commands (default: OP)
- `boosted.bypass` – players with this are never targeted by boosts

### Requirements

- Paper 1.21.x
- LuckPerms
- (Optionally) any plugins your configured commands invoke (e.g., Vault/Essentials for `eco`)

### Build

```bash
mvn -q -e -U -B package
```
JAR will be at `target/Boosted-1.0.0-shaded.jar`.

### Configure

See `src/main/resources/config.yml` for examples and placeholders:
- `%player%`, `%player_uuid%`, `%boost_name%`, `%duration%`

### Persistence

Active boosts are saved to `plugins/Boosted/active_boosts.yml`. On restart, end messages and end commands still run at the correct time. LuckPerms temp parents are used so group removal is reliable even across restarts.


**Also available:** `/boostlist` – grouped summary by boost (with counts + soonest time). Use `/boostlist <name>` to expand players.

**Targets:** `@s` (self), `@a` (online, bypass-respecting), `@all` (online + offline; bypass only checked for online players).

- With LuckPerms installed, `@all` respects `boosted.bypass` for offline users too.