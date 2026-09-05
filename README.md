# DataDrivenVillagers

Define villager professions in JSON. Bind one to a workstation block, give it a texture, and let
[VillagerTradingPlus](https://www.curseforge.com/minecraft/mc-mods/villagerstradingplus) handle the
trades.

No Java, no fork, no resource pack required.

- Minecraft 1.20.1, Fabric and Forge
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

## Why is this not a datapack?

Because a datapack is too late. Villager professions and points of interest live in registries that
Minecraft freezes before the first datapack is read, so nothing loaded from `data/` could ever create
one. This folder is read while the game starts instead, which is why changes need a restart rather
than `/reload`.

Everything that *can* be a datapack still is one: trades, loot, tags, translations.

The same reason makes the folder part of the install. A profession or a villager type is a registry
entry, and a client that joins without it is refused by the game itself, with `server sent registries
with unknown keys`, before any code of this mod runs. So every player needs the mod **and the same
profession and type files** in their own `config/datadrivenvillagers/`. `/ddv export <name>` builds a
zip of one profession with everything that belongs to it, made to be handed out, and `/ddv doctor`
lists the files a client has to bring. Textures and hats travel on their own when a player joins, so a
stale png is never the problem; a missing file is.

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
| `health` | max health, default 20. Set whenever the brain is built, so it follows a job change and a reload |
| `villages` | villager types allowed to take the job, e.g. `["desert"]`. Leave it out for all |
| `ticket_count` | how many villagers work at one station at the same time, default 1 |
| `search_distance` | how far a villager looks for the station, default 1 |

Any block works as a workstation, including blocks from other mods, **as long as it is not already a
job site**. Minecraft allows exactly one point of interest per block state, so the thirteen vanilla
workstation blocks are off limits: `smoker`, `barrel`, `blast_furnace`, `brewing_stand`,
`cartography_table`, `cauldron`, `composter`, `fletching_table`, `grindstone`, `lectern`, `loom`,
`smithing_table`, `stonecutter`. A definition that names only such blocks is rejected with the
conflicting job site in the message; if it names several blocks, the taken ones are skipped with a
warning and the rest still work.

## Textures

Put `baker.png` next to `baker.json` and set `"texture": "baker.png"`. The image is loaded at runtime,
so no resource pack is needed. It is a villager profession overlay with the same layout as the vanilla
ones, and it is used for the zombie variant as well. Give the zombie an image of its own with
`"zombie_texture": "baker_zombie.png"`.

On a server the images travel: when a player joins, and again after every `/ddv reload`, the server
sends each profession and type to the client - hat, texture identifier or the png itself. What the
server says wins over the client's own config folder, which only fills in what the server left open.
A png above 900 kB stays home with a warning; that size belongs in a resource pack.

### What `hat` really decides

Not how this profession looks. Its own image is drawn over the head either way. `hat` decides whether
the hat of the **villager type texture underneath** survives, and vanilla's rule is

    the type's hat is drawn  <=>  hat == none  ||  (hat == partial && that type's own hat != full)

Two things follow that are worth knowing before wondering why nothing changes. In 1.20.1 exactly two
villager types bring a hat of their own, `minecraft:desert` and `minecraft:snow`, and both declare it
`full` - so `partial` behaves like `full` on those two and like `none` on every other type, and is
never something in between. And a villager type from this mod never brings one, because its texture is
written at runtime and a runtime texture has no `.png.mcmeta` to declare a hat in.

`/ddv why <profession>` says which of the three is set and what it does to that profession.

To ship a texture in a resource pack instead, give a full identifier:
`"texture": "mypack:textures/entity/villager/profession/baker.png"`.

## Day plans

Vanilla gives every adult villager the same day: idle at sunrise, work from tick 2000, meet at the
bell from 9000, sleep from 12000. `"schedule": "night"` is that same plan half a day later, so the
villager sleeps through the day, wakes at dusk, works the night, and gathers at the bell before
sunrise. `"default"` names the vanilla plan, worth writing when you want to say so out loud.

Or write the switch-over points yourself. Each entry says what the villager does from that tick until
the next one, and the last entry carries over midnight into the first:

```json
"schedule": [
  { "time": 0,     "activity": "rest" },
  { "time": 13000, "activity": "work" },
  { "time": 22000, "activity": "idle" }
]
```

A day is 0 to 23999 ticks, 0 is sunrise and 12000 is sunset. Four activities: `idle`, `work`, `meet`,
`rest`. No others, and that is deliberate rather than a gap: `core` runs always, and `panic`, `raid`,
`pre_raid` and `hide` are started by what a villager senses rather than by the clock. An activity
outside those four would leave the brain with no task list to run, and the villager would stand still
with nothing to show for it, so the file is rejected instead.

This is the one field that also works together with `overrides`, because a day plan is looked up per
villager instead of being baked into the profession when it is created. Putting the vanilla farmer on
the night shift takes four lines and nothing else in this format can do it.

It is also the only field `/ddv reload` carries all the way to villagers that already stand in the
world: their brains are rebuilt the way vanilla rebuilds them when a villager changes job.

## Behaviour

Four more fields are looked up per villager, like the day plan, so they follow a reload at once and
work with `overrides` too.

**`work_behaviour`** decides what the villager does at work. `station`, the default, is what every
vanilla profession but one does: walk to the job site, look busy, restock. `farm` is the farmer's
routine on top of that - walk the farmland nearby, harvest what is ripe, plant what is in the
inventory, bone meal what is slow. Vanilla gates that routine on the profession *being* the farmer,
in two places; this flips both for your profession.

```json
{ "workstation": "minecraft:flower_pot", "work_behaviour": "farm" }
```

A farmer needs two things the file can leave out: `secondary_job_sites` has to contain
`minecraft:farmland`, or the routine never starts, and `gatherable_items` has to name seeds, or there
is nothing to plant. When the file says nothing, the farmer's own values are filled in - `wheat`,
`wheat_seeds`, `beetroot_seeds`, `bone_meal`, farmland. Name your own and they stay. `farm` cannot be
given to an override of anything but `minecraft:farmer`, because the overridden profession's list of
secondary sites was handed to vanilla once at registration and cannot learn farmland afterwards.

**`flees_from`** adds to what the villager runs from. Vanilla keeps eleven entity types with a
distance each - eight blocks for a zombie, fifteen for a pillager - and every profession fears exactly
those. This adds to the list for one profession; it never takes anything off it, so a zombie stays a
zombie.

```json
"flees_from": ["minecraft:creeper", { "entity": "minecraft:wolf", "distance": 12 }]
```

A bare id means eight blocks. Entity ids only, no tags.

To replace the list instead, write `flees_only_from`: then vanilla's eleven are switched off for this
profession and only what the file names counts. `"flees_only_from": []` is a villager that fears
nothing on sight - it still panics when something hurts it, that comes from a different sensor. A
file that writes both fields is rejected.

**`attacks`** makes the villager go after something instead of running from it. Vanilla villagers
have no attack at all - no damage attribute, no target memory, no task that swings - so this mod
gives every villager the first two and a profession with `attacks` the tasks: pick a target from
what it can see, walk up, hit, forget it when it is gone. The tasks are vanilla's own, the ones a
piglin fights with; the villager model has no swing animation, so the hit shows as a shove.

```json
"attacks": ["minecraft:zombie", { "entity": "minecraft:skeleton", "distance": 12 }],
"attack": { "damage": 3, "cooldown": 20 }
```

The list has the same shape as `flees_from`, a bare id meaning eight blocks. `attack` is optional:
2 damage and 20 ticks between swings by default. Anything in `attacks` is never feared, whatever
`flees_from` says, because the panic task and the attack task would otherwise fight over the same
tick. `"health": 40` changes the first half of that; there is still no armour, and no faster healing. It attacks in every activity, asleep excepted, so a night guard on the
`night` plan is a villager that stands outside and fights.

**`villages`** reserves the job for villagers of certain types - the biome clothing, `plains`,
`desert`, `savanna`, `snowy`, `taiga`, `jungle`, `swamp`, or a type of your own from the `types`
folder. For a villager of another type the block is simply not there: it is left out of the job search
and the villager looks for something else. `/ddv why <profession>` lists the allowed types.

```json
"villages": ["desert", "savanna"]
```


## Trades

Trades come from VillagerTradingPlus. Add a trade file to a datapack at
`data/<your_namespace>/default_villager_trades/baker.json` with `"profession": "datadrivenvillagers:baker"`.

Trades are one of three things this mod deliberately does not read itself: the gift is a loot table
and belongs to a datapack, the name is a translation and belongs to a resource pack. Each is a format
you would otherwise have to go and learn somewhere else first, which is where most people stop.

`/ddv scaffold baker` writes all three into `professions/scaffold/baker/`, each with a note saying
where in a pack it belongs. It never overwrites what you edited there.

The trades file also has a shorter road: **Create default trades** on the Work page of `/ddv edit`
writes it straight into the world's own datapacks (`datapacks/ddv_trades/`), filled in from the saved
profession, and the answer on the screen names the one command left - `/reload`. It never overwrites
a trades file that is already there.

`/ddv export baker` packs the profession, its png and those three files into a single zip somebody
else can unpack, laid out in three folders named after where their contents go. Anything in it that
was generated rather than written by you is marked as such, in the report and again in the readme
inside the zip: a placeholder shipped as though it were the real thing is worse than an empty slot.

## Village buildings

Put the `.nbt` of a building into `config/datadrivenvillagers/structures/` with a json beside it, and
it turns up in the villages the game generates:

```json
{
  "structure": "bakery.nbt",
  "weight": 5
}
```

Or leave the building to the mod:

```json
{
  "workstation": "minecraft:campfire",
  "weight": 5
}
```

That draws a roofed stall, five blocks to a side, with the campfire under the roof, in the materials of
whatever village it lands in — oak in the plains, sandstone in the desert, acacia in the savanna,
spruce in the taiga and under snow. It stands on the street and brings its own villager, who takes the
job. Good enough to find your profession in a generated village; build the real thing when you want
it to look like one. A file names either `structure` or `workstation`, not both.

That is the whole file. Without `villages` it goes into all five village types, and without `pool`
into the houses. `weight` is how often it is drawn against the other pieces of the same pool — vanilla's
own houses sit between 1 and 3, so 5 does not make yours common, it makes the village yours.

One thing to know about that default: a saved `.nbt` is a building, not a plan. It is placed as it was
saved, so an oak bakery from the plains stands in the desert in oak. Name `villages` to keep it where
its material fits. A generated stall needs no such care, it is drawn in each village's own material.
`/ddv why <name>` says so for a saved building that goes everywhere.

| Field | Means |
| --- | --- |
| `structure` | the nbt beside this json, saved in game with a structure block |
| `workstation` | instead of `structure`: a block id, and the mod draws a stall around it |
| `weight` | draws against the rest of the pool, default 1 |
| `villages` | `plains`, `desert`, `savanna`, `snowy`, `taiga`; all of them when left out |
| `pool` | `houses`, `decor` or `streets`, default `houses` |
| `pools` | pool ids written out, for pools the shorthand cannot name. Replaces the two fields above |
| `ground` | `rigid` like a vanilla house, or `terrain` to follow the ground. Default `rigid` |
| `processors` | a processor list, `minecraft:mossify_10_percent` is what weathers vanilla's houses |

**Your own building needs a jigsaw block.** A generated stall has its two already; a saved one is the one part no mod can do for you: a village piece
connects to the street through a jigsaw block, and without one the generator never places it and
never says why. Give it a target matching what it should attach to, and point a second one at
`minecraft:village/<type>/villagers` if the building should come with its own villager — which is how
vanilla's houses get theirs, and how a new profession ends up staffed.

`/ddv why bakery` checks all of this, the jigsaw block included.

Why this needs a mod at all: a datapack can only **replace** a pool file, and
`village/plains/houses.json` holds 36 entries across 10 kB, five times over for the five village
types, to be rewritten at every Minecraft update. Two datapacks that both add a house delete each
other. Appending at runtime is the one way that composes.

Zombie villages are left alone. They draw from their own pools, and a normal building among the
cobwebbed ones would look out of place.

## Commands

| Command | Does |
| --- | --- |
| `/ddv list` | every loaded profession with its workstation, texture source and trade count |
| `/ddv errors` | every rejected file and why |
| `/ddv why <name>` | walks the whole chain for one profession or villager type and shows where it breaks |
| `/ddv why villager [<target>]` | the same for one villager standing there: job, station, bed, plan, what it is doing, why it does not sleep |
| `/ddv blocks [text]` | which blocks are still free to be a workstation |
| `/ddv reload` | reads the config folder again, without restarting |
| `/ddv scaffold <name>` | writes the trade file, the gift loot table, the language file, and a village stall as nbt, to be edited |
| `/ddv export <name>` | packs the profession, its png and those three files into one shareable zip |
| `/ddv doctor` | every report at once, into `doctor.txt`, to paste into an issue |
| `/ddv edit [<name>]` | opens the editor on a profession, or on a new one, and saves it back into the folder |
| `/ddv help` | this table, in the chat; bare `/ddv` does the same |

`/ddv edit` opens a screen with every field on it, grouped over five pages, and a save button that
writes the file and runs the same reload as `/ddv reload` — the answer, restart lines included, lands
at the bottom of the screen you are looking at. It edits the file you already have rather than a copy
of the fields it knows about, so a `_comment` and anything this version has no widget for come back
out untouched. A rejected save writes nothing: the reason appears and the file on disk stays as it was.
Everything it does you could do with a text editor and `/ddv reload`; what it saves you is knowing the
field names by heart.

`/ddv why baker` is the one to reach for when a file loaded but nothing happens in the game. It checks
each link a villager needs — the profession, its job site, the `acquirable_job_site` tag, and the block
that leads back to it — against what the running game actually holds, and names the one that broke plus
what to do about it. An override is found under its file name as well as under the vanilla id it changes. It answers for a rejected file too, so `/ddv why` on a name that never loaded
tells you why instead of "unknown".

`/ddv why villager` answers for the villager nearest to you, or for one named with a selector:
`/ddv why villager @e[type=villager,limit=1,sort=nearest]`. Where the file report says whether a
villager *could* take the job, this says what one villager holds: its job and the file behind it, job
site, bed and bell with distances, the site it is about to take and whether `villages` will turn it
away, the plan it is on and what that plan says for this hour against what it is actually doing, and
whether it may lie down right now, with every reason it may not. Most of it is read straight out of
the villager's memories, which makes it the answer to "why does he wake up" and "why does he keep
the job".

`/ddv doctor` writes every report this mod can give into `config/datadrivenvillagers/doctor.txt`:
versions, the folder, every rejected file with its reason, and `/ddv why` for every loaded profession,
type and structure. Paste it into an issue as it is.

`/ddv scaffold` also writes the stall the mod would draw for the profession, as `<name>.nbt` beside a
`<name>_house.json`. Copy both into `structures/` and it turns up in villages as it is, or load the nbt
into a structure block, build a proper house over it, keep the two jigsaw blocks, and save it back.


`/ddv blocks` answers the question before you write the file. A block state may belong to exactly one
point of interest, so every job site in the game — vanilla's, this mod's, another mod's — takes its
blocks away from everyone else, and nothing in the game shows which. With no argument it lists the
blocks that are already spoken for; anything not listed is free. `/ddv blocks copper` narrows that to
every block whose id contains the text.

`/ddv reload` reads the folder again while the game runs, which is what makes getting a texture or a
hat right bearable. It applies everything this mod answers itself - texture, `hat`, `gift`, the
workstation blocks, `work_behaviour`, `flees_from`, `villages`, and a villager type's biomes — and
names every file it could only apply in part. A new override is applied on the spot: its target
exists, so there is nothing to wait for.

A day plan and a work routine go further than any of those: villagers already standing in the world
have their brains rebuilt, the same way vanilla rebuilds one when a villager changes job, so the new plan takes effect
without waiting for the chunk to reload. The report says how many were given it.

A file that stops parsing mid-edit is reported as rejected and otherwise left alone: the version
loaded before the reload keeps running, and the report says so. A typo never costs you a profession.

Some things cannot be reloaded at all, because professions, job sites and villager types are
registered into registries that freeze before any world exists. Adding a file, removing one, or
changing `display_name`, `work_sound`, `gatherable_items`, `secondary_job_sites`, `ticket_count` or
`search_distance` needs a restart, and the report says so per file rather than ignoring it.

Textures follow on a server too: after the reload every connected player is sent the new hats and
images, and the report says how many.

## Known limitation

On Fabric there are no registry phases: if another mod registers its blocks after this one runs, those
blocks are not resolvable yet and are skipped with a warning in the log. Vanilla blocks are always
available. Forge is not affected, because registration is split across the matching
`RegisterEvent`s there.

A definition survives as long as at least one of its workstation blocks resolves.

The other way round is the dangerous one. A block state may belong to exactly one point of interest,
and this mod checks that before it registers a job site: a block another mod has already claimed is
skipped with a warning. But a block that another mod claims **after** this mod ran cannot be checked,
and on Forge that is a crash at startup, because vanilla refuses the second claim there where
Fabric silently lets the last writer win. Not measured, read out of the vanilla code: a definition on
a block that another villager mod later makes a workstation of its own is nothing to ship without
asking that mod. `/ddv blocks` shows what is taken at the moment it is asked, and no earlier.

## License

CC0-1.0
