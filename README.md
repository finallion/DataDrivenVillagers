# DataDrivenVillagers

Define villager professions in JSON. Bind one to a workstation block, give it a texture, and let
[VillagerTradingPlus](https://www.curseforge.com/minecraft/mc-mods/villagerstradingplus) handle the
trades.

No Java, no fork, no resource pack required.

- Minecraft 1.21.1, Fabric and NeoForge
- Fabric additionally needs Fabric API

## Quick start

Start the game once. The mod writes an example and a readme into

```
config/datadrivenvillagers/professions/
```

Edit `example_baker.json`, restart, and `/ddv list` shows the profession.

```json
{
  "workstation": "minecraft:campfire",
  "display_name": "Baker",
  "texture": "baker.png",
  "hat": "none",
  "work_sound": "minecraft:entity.villager.work_farmer",
  "gatherable_items": ["minecraft:wheat", "minecraft:bread"],
  "secondary_job_sites": ["minecraft:cake"]
}
```

The file name is the id: `baker.json` becomes `datadrivenvillagers:baker`.

## Why a config folder and not a datapack

Villager professions and points of interest are registries. Minecraft freezes them before it reads
the first datapack, so a datapack cannot create them. The mod reads this folder at startup. For this
reason, a new file or a removed file needs a restart, not `/reload`.

Trades, loot tables, tags and translations stay in datapacks and resource packs.

Every player needs the mod **and the same profession and type files** in their own
`config/datadrivenvillagers/`. The game refuses a client that does not know a registered profession or
type, with `server sent registries with unknown keys`. `/ddv export <name>` packs one profession with
all its files into a zip for your players. `/ddv doctor` lists the files a client must have. Textures
and hats are sent to each player on join, so only missing files cause problems, not old images.

## Fields

| Field | Meaning |
| --- | --- |
| `workstation` | required, a block id or a list of block ids, see the note below |
| `display_name` | shown when no language file translates the profession |
| `texture` | a file name next to the json, or a full identifier into a resource pack |
| `hat` | `none`, `partial` or `full`. It hides the *type* texture's hat, not this one's, see below |
| `work_sound` | sound id played while working |
| `gatherable_items` | items the villager picks up |
| `secondary_job_sites` | extra blocks the villager is drawn to |
| `gift` | loot table thrown at a Hero of the Village after a raid |
| `schedule` | the day plan, see below. Leave it out to keep vanilla's |
| `zombie_texture` | the zombie villager's own image, same rule as `texture`. Leave it out and the zombie wears `texture` |
| `work_behaviour` | `station` (default) or `farm`, see below |
| `flees_from` | entity types this profession runs from, on top of vanilla's list, see below |
| `flees_only_from` | the complete list instead of vanilla's; `[]` fears nothing on sight |
| `attacks` | entity types the villager goes after, see below |
| `attack` | `{ "damage": 2, "cooldown": 20 }`, only with `attacks` |
| `health` | max health, default 20. Applied whenever the brain is built, so it follows a job change and a reload |
| `villages` | villager types allowed to take the job, e.g. `["desert"]`. Leave it out for all |
| `ticket_count` | how many villagers work at one station at the same time, default 1 |
| `search_distance` | how far a villager looks for the station, default 1 |

Any block can be a workstation, including blocks from other mods, **if it is not already a job
site**. Minecraft allows one point of interest per block state, so the thirteen vanilla workstation
blocks are not available: `smoker`, `barrel`, `blast_furnace`, `brewing_stand`, `cartography_table`,
`cauldron`, `composter`, `fletching_table`, `grindstone`, `lectern`, `loom`, `smithing_table`,
`stonecutter`. A definition that names only such blocks is rejected, and the message names the
conflicting job site. If a definition names several blocks, the mod skips the taken ones with a
warning and uses the rest.

## Textures

Put `baker.png` next to `baker.json` and set `"texture": "baker.png"`. The mod loads the image at
runtime, so no resource pack is needed. It is a villager profession overlay with the same layout as
the vanilla ones. The zombie variant uses it too. To give the zombie its own image, set
`"zombie_texture": "baker_zombie.png"`.

On a server, the server sends each profession and type to the client when a player joins and after
every `/ddv reload`: hat, texture identifier or the png itself. The server's data replaces the
client's own config folder; the client folder only fills in what the server did not send. A png above
900 kB is not sent and the log shows a warning. Put images of that size into a resource pack.

### What `hat` does

`hat` does not change how this profession looks. Its own image is always drawn over the head. `hat`
decides whether the hat of the **villager type texture underneath** is drawn. Vanilla's rule:

    the type's hat is drawn  <=>  hat == none  ||  (hat == partial && that type's own hat != full)

In 1.21.1, only two villager types have a hat of their own, `minecraft:desert` and `minecraft:snow`,
and both declare it `full`. So `partial` works like `full` on those two types and like `none` on all
other types. A villager type from this mod never has a hat, because its texture is written at runtime
and has no `.png.mcmeta`.

`/ddv why <profession>` shows which value is set and what it does to that profession.

To ship a texture in a resource pack instead, give a full identifier:
`"texture": "mypack:textures/entity/villager/profession/baker.png"`.

## Day plans

Vanilla gives every adult villager the same day: idle at sunrise, work from tick 2000, meet at the
bell from 9000, sleep from 12000. `"schedule": "night"` is the same plan shifted by half a day: the
villager sleeps during the day, wakes at dusk, works at night and meets at the bell before sunrise.
`"default"` is the vanilla plan.

You can also write the switch-over points yourself. Each entry sets the activity from its tick until
the next entry. The last entry continues past midnight into the first:

```json
"schedule": [
  { "time": 0,     "activity": "rest" },
  { "time": 13000, "activity": "work" },
  { "time": 22000, "activity": "idle" }
]
```

A day is 0 to 23999 ticks. 0 is sunrise, 12000 is sunset. There are four activities: `idle`, `work`,
`meet`, `rest`. Other activities are rejected: `core` always runs, and `panic`, `raid`, `pre_raid` and
`hide` start from what a villager senses, not from the clock. A villager with any other activity would
have no tasks and would stand still.

`schedule` also works with `overrides`, because the mod looks up the day plan per villager. Example:
four lines put the vanilla farmer on the night shift.

`/ddv reload` applies a new day plan also to villagers that are already in the world. Their brains are
rebuilt the same way vanilla rebuilds them when a villager changes job.

## Behaviour

The mod looks up these four fields per villager, like the day plan. They apply at once after a
reload and work with `overrides`.

**`work_behaviour`** sets what the villager does at work. `station` is the default and is what every
vanilla profession except the farmer does: walk to the job site, look busy, restock. `farm` adds the
farmer's routine: walk the farmland nearby, harvest ripe crops, plant seeds from the inventory, use
bone meal.

```json
{ "workstation": "minecraft:flower_pot", "work_behaviour": "farm" }
```

A farming profession needs two things. `secondary_job_sites` must contain `minecraft:farmland`, or the
routine does not start. `gatherable_items` must contain seeds, or there is nothing to plant. If the
file does not set them, the mod uses the farmer's values: `wheat`, `wheat_seeds`, `beetroot_seeds`,
`bone_meal`, farmland. Values you set yourself are kept. An override can use `farm` only for
`minecraft:farmer`, because vanilla fixes the secondary job sites of an existing profession at
registration.

**`flees_from`** adds entity types the villager runs from. Vanilla has eleven entity types, each with
a distance: eight blocks for a zombie, fifteen for a pillager. `flees_from` adds to this list for one
profession and never removes an entry.

```json
"flees_from": ["minecraft:creeper", { "entity": "minecraft:wolf", "distance": 12 }]
```

A bare id means eight blocks. Entity ids only, no tags.

**`flees_only_from`** replaces the list. Vanilla's eleven entries are then off for this profession,
and only the entries in the file count. With `"flees_only_from": []` the villager runs from nothing it
sees. It still panics when it takes damage. A file that sets both fields is rejected.

**`attacks`** makes the villager attack entities instead of running from them. The villager picks a
visible target, walks to it, hits it, and forgets it when it is gone. The villager model has no swing
animation, so a hit looks like a push.

```json
"attacks": ["minecraft:zombie", { "entity": "minecraft:skeleton", "distance": 12 }],
"attack": { "damage": 3, "cooldown": 20 }
```

The list has the same form as `flees_from`; a bare id means eight blocks. `attack` is optional: the
default is 2 damage and 20 ticks between hits. The villager never runs from an entity in `attacks`,
even if `flees_from` names it. `"health": 40` gives a guard more health; there is no armour and no
faster healing. The villager attacks in every activity except while it sleeps, so a guard on the
`night` plan fights at night.

**`villages`** allows the job only for villagers of the listed types: `plains`, `desert`, `savanna`,
`snowy`, `taiga`, `jungle`, `swamp`, or a type of your own from the `types` folder. A villager of
another type ignores the block and looks for a different job. `/ddv why <profession>` lists the
allowed types.

```json
"villages": ["desert", "savanna"]
```

## Trades

Trades come from VillagerTradingPlus. Add a trade file to a datapack at
`data/<your_namespace>/default_villager_trades/baker.json` with `"profession": "datadrivenvillagers:baker"`.

The gift is a loot table in a datapack, and the name is a translation in a resource pack.

`/ddv scaffold baker` writes the trade file, the gift loot table and the language file into
`professions/scaffold/baker/`. Each file has a note that says where it belongs in a pack. The command
never overwrites a file you edited there.

For trades there is also a shorter way: **Create default trades** on the Work page of `/ddv edit`
writes the trade file into the world's datapacks (`datapacks/ddv_trades/`), filled in from the saved
profession. Then run `/reload`. The button never overwrites an existing trade file.

`/ddv export baker` packs the profession, its png and those three files into one zip, in three
folders named after where their contents go. Files that the mod generated are marked as generated in
the report and in the readme inside the zip.

## Village buildings

Put the `.nbt` of a building into `config/datadrivenvillagers/structures/` with a json next to it, and
the building appears in generated villages:

```json
{
  "structure": "bakery.nbt",
  "weight": 5
}
```

Or let the mod build it:

```json
{
  "workstation": "minecraft:campfire",
  "weight": 5
}
```

The mod then builds a roofed stall, five blocks on each side, with the campfire under the roof. It
uses the material of the village: oak in the plains, sandstone in the desert, acacia in the savanna,
spruce in the taiga and in snowy villages. The stall stands at the street and brings its own
villager, who takes the job. A file names either `structure` or `workstation`, not both.

Without `villages`, the building goes into all five village types. Without `pool`, it goes into the
houses. `weight` sets how often it is picked against the other pieces of the same pool. Vanilla's
houses have weights from 1 to 3, so a weight of 5 makes the building very frequent.

A saved `.nbt` is placed exactly as it was saved. An oak bakery from the plains also appears in oak in
the desert. Set `villages` to keep it where its material fits. A generated stall uses each village's
own material. `/ddv why <name>` warns about a saved building that goes into all village types.

| Field | Meaning |
| --- | --- |
| `structure` | the nbt next to this json, saved in game with a structure block |
| `workstation` | instead of `structure`: a block id, and the mod builds a stall around it |
| `weight` | how often it is picked against the rest of the pool, default 1 |
| `villages` | `plains`, `desert`, `savanna`, `snowy`, `taiga`; all of them when left out |
| `pool` | `houses`, `decor` or `streets`, default `houses` |
| `pools` | full pool ids, for pools the short form cannot name. Replaces the two fields above |
| `ground` | `rigid` like a vanilla house, or `terrain` to follow the ground. Default `rigid` |
| `processors` | a processor list, `minecraft:mossify_10_percent` is the one vanilla uses to weather its houses |

**Your own building needs a jigsaw block.** A generated stall already has two. A village piece
connects to the street through a jigsaw block. Without one, the generator never places the building
and shows no error. Give the jigsaw block a target that matches what it should attach to. Add a second
jigsaw block that points at `minecraft:village/<type>/villagers` if the building should come with its
own villager. Vanilla's houses get their villagers the same way.

`/ddv why bakery` checks all of this, including the jigsaw block.

A datapack can only **replace** a pool file. `village/plains/houses.json` alone has 36 entries, and
there are five village types. Two datapacks that both add a house overwrite each other. The mod adds
its buildings to the pools at runtime, so they combine with other packs.

Zombie villages are not changed. They use their own pools.

## Commands

| Command | Does |
| --- | --- |
| `/ddv list` | every loaded profession with its workstation, texture source and trade count |
| `/ddv errors` | every rejected file and why |
| `/ddv why <name>` | checks the whole chain for one profession or villager type and shows where it breaks |
| `/ddv why villager [<target>]` | the same for one villager: job, station, bed, plan, current activity, why it does not sleep |
| `/ddv blocks [text]` | which blocks are still free to be a workstation |
| `/ddv reload` | reads the config folder again, without restarting |
| `/ddv scaffold <name>` | writes the trade file, the gift loot table, the language file, and a village stall as nbt, to be edited |
| `/ddv export <name>` | packs the profession, its png and those three files into one shareable zip |
| `/ddv doctor` | every report at once, into `doctor.txt`, to paste into an issue |
| `/ddv edit [<name>]` | opens the editor on a profession, or on a new one, and saves it back into the folder |
| `/ddv help` | this table, in the chat; bare `/ddv` does the same |

`/ddv edit` opens a screen with every field, on five pages. The save button writes the file and runs
the same reload as `/ddv reload`. The result, including which fields need a restart, appears at the
bottom of the screen. The editor changes your existing file, so a `_comment` and fields without a
widget stay unchanged. If a save is rejected, the file on disk stays as it was and the screen shows
the reason.

`/ddv why baker` checks each link a villager needs: the profession, its job site, the
`acquirable_job_site` tag, and the block that leads back to it. It compares them with the running game
and names the link that is broken and what to do about it. It finds an override under its file name
and under the vanilla id it changes. It also works for a rejected file and shows why the file was
rejected.

`/ddv why villager` checks the villager nearest to you, or one selected with a selector:
`/ddv why villager @e[type=villager,limit=1,sort=nearest]`. It shows the villager's job and the file
behind it, job site, bed and bell with distances, the job site it is about to take and whether
`villages` refuses it, its day plan and current activity, and whether it can sleep now, with every
reason why not. Most of this comes from the villager's memories.

`/ddv doctor` writes every report into `config/datadrivenvillagers/doctor.txt`: versions, the folder,
every rejected file with its reason, and `/ddv why` for every loaded profession, type and structure.
Attach it to a bug report.

`/ddv scaffold` also writes the stall the mod would build for the profession, as `<name>.nbt` next to
a `<name>_house.json`. Copy both into `structures/` to use the stall as it is. Or load the nbt into a
structure block, build a house over it, keep the two jigsaw blocks, and save it again.

`/ddv blocks` shows which blocks are already a job site. A block state can belong to only one point of
interest, and every job site in the game (vanilla, this mod, other mods) takes its blocks away from
all others. Without an argument, the command lists all taken blocks; a block that is not in the list
is free. `/ddv blocks copper` shows only blocks whose id contains `copper`.

`/ddv reload` reads the folder again while the game runs. It applies texture, `hat`, `gift`, the
workstation blocks, `work_behaviour`, `flees_from`, `villages`, and the biomes of a villager type. It
names every file it could apply only in part. A new override applies at once.

A new day plan or work routine also reaches villagers that are already in the world: their brains
are rebuilt the same way vanilla rebuilds them when a villager changes job. The report shows how many
villagers were updated.

If a file has an error after an edit, the reload reports it as rejected and keeps the version loaded
before. The profession stays in the game.

Some changes need a restart, because professions, job sites and villager types are registries that
Minecraft freezes before a world loads: adding or removing a file, and changing `display_name`,
`work_sound`, `gatherable_items`, `secondary_job_sites`, `ticket_count` or `search_distance`. The
report names these per file.

On a server, every connected player receives the new hats and images after the reload. The report
shows how many players received them.

## Known limitations

On Fabric there are no registry phases. If another mod registers its blocks after this mod runs, those
blocks are not available yet and are skipped with a warning in the log. Vanilla blocks are always
available. NeoForge is not affected, because registration there is split across the matching
`RegisterEvent`s.

A definition loads as long as at least one of its workstation blocks exists.

A block state can belong to only one point of interest. Before it registers a job site, the mod checks
this and skips a block that another mod already claimed, with a warning. A block that another mod
claims **after** this mod runs cannot be checked. On NeoForge, vanilla then rejects the second claim
and the game crashes at startup. On Fabric, the last claim wins without a message. Do not use a block
that another villager mod makes its own workstation. `/ddv blocks` shows only what is taken at the
moment you run it.

## License

GPL-3.0, see [LICENSE](LICENSE).
