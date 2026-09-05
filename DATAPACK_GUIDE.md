# Custom Flyover Events via Datapack

You can add your own flyover airships using a Minecraft datapack.

### Folder structure

```
<datapack_name>/
|-- pack.mcmeta
`-- data/
    `-- <your_namespace>/
        |-- flyover_events/
        |   `-- <event_name>.json
        `-- structure/
            `-- <template_name>.nbt

```

Each datapack uses a **namespace** (e.g. `my_custom`) to avoid conflicts with other datapacks. All your files go under `data/<your_namespace>/`.

### 1. `pack.mcmeta` (required)

This is for Minecraft to recognize the datapack. Create a file called `pack.mcmeta` in the root of your datapack folder:

```json
{
  "pack": {
    "description": "My custom flyovers",
    "pack_format": 48
  }
}
```

The `pack_format` number changes with each Minecraft version. For Minecraft 1.21.1 the value is `48`.

### 2. Flyover event config: `data/<namespace>/flyover_events/<name>.json`

Each JSON file registers one flyover event. Create one per airship template you want to appear:

```json
{
  "template": "<namespace>:<template_name>",
      "min_altitude": 220,              
      "max_altitude": 260,              
      "weight": 1,                      
      "initial_velocity": {             
    "linear": [
      0.0,
      0.0,
      0.0
    ],
    "angular": [
      0.0,
      0.0,
      0.0
    ],
    "impulse": false
  },
  "dimensions": ["minecraft:overworld"],  
  "biome_filter": {                       // Optional. Restricts which biomes the flyover can spawn over, checked at the player's position.
    "only": ["minecraft:desert"]          //   using "only" -> spawn ONLY over these biomes. Using "exclude" instead:
  }                                       //   "exclude": ["minecraft:ocean"] -> spawn over every biome except these.
                                          //   Omitting propriety will allow every biome in the allowed dimension(s).
}
```

| Field              | Type   | Required                 | Description                                                                                                  |
|--------------------|--------|--------------------------|--------------------------------------------------------------------------------------------------------------|
| `template`         | id     | Yes                      | The structure template to assemble (found in `data/<namespace>/structure/<template_name>.nbt`)               |
| `min_altitude`     | int    | No (default `150`)       | Lower bound of the altitude band where the craft spawns.                                                     |
| `max_altitude`     | int    | No (default `200`)       | Upper bound of the altitude band where the craft spawns. One altitude is picked at random inside the band.   |
| `weight`           | int    | No (default `1`)         | Weight of the event in the pool, i.e. how likely it is to spawn.                                             |
| `initial_velocity` | object | No                       | Optional velocity applied on spawn (same format as flyovers).                                                |
| `plan`             | object | No                       | Optional flight plan flown by the craft once assembled. See the [Flight plans](#flight-plans) section above. |
| `dimensions`       | list   | No (default all allowed) | Optional list of allowed dimensions for the flyover to spawn in.                                             |

Flyover events also accept a `plan` field, which gives the airship a flight plan to follow (e.g. orbit a location). Plans are shared by flyover events and patrols, and are described in their own section below.

### 3. Structure template: `data/<namespace>/structure/<template_name>.nbt`

This is the actual build; the `template` field in your flyover JSON must match `<namespace>:<template_name>`.

Contraptions on the structure must already **receive redstone/rotational power** but **not be assembled** before exporting to nbt; **entities** should also be exported.
The NBT should be exported with the templated oriented **towards north** in game.

Note that making some Create contraptions work properly from a template can be finicky, and not every Create/Create Aeronautics block is exported correctly into nbt.
Always make sure they are being imported correctly into the game and that they remain functional before adding them as flyovers.

You can add Pins using the PinWand item (or commands) to add custom controllable behaviors on the structure before exporting to nbt.

> [!NOTE]
> For a guide on how to use Pins, refer to [this file](./PIN_GUIDE.md)

### 4. Testing the custom flyover
> [!WARNING]
> Always test custom events in a new world first to make sure nothing unexpected happens.
> Very large templates or templates that make use of untested blocks may cause game crashes. A template-verification system to avoid this is planned in the future, but please exercise caution until then.
>

You can use these in-game commands to test:

```
/discovery flyover          # spawn a random flyover event
/discovery flyover <name>   # spawn your custom event by template name
```

New templates will appear in tab-completion automatically after `/reload`.

---



## Custom Patrols via Datapack

Patrols are crafts that spawn automatically tied to **world-generated structures**: when the structure's own chunk gets loaded, the craft spawns near it, optionally flying a flight plan around it. Unlike flyovers they do not fly over the world on a timer; once created, the craft and its plan are permanent until removed.

### Folder structure

```
<datapack_name>/
|-- pack.mcmeta
`-- data/
    `-- <your_namespace>/
        |-- patrols/
        |   `-- <patrol_name>.json
        `-- structure/
            `-- <template_name>.nbt
```

Each datapack uses a **namespace** (e.g. `my_custom`) to avoid conflicts with other datapacks. All your files go under `data/<your_namespace>/`. The `pack.mcmeta` file is the same as the one shown in step 1.

### Patrol config: `data/<namespace>/patrols/<name>.json`

Each JSON file registers one patrol. This is the full format:

```json
{
  "template": "<namespace>:<template_name>",        
  "target_structure": "minecraft:pillager_outpost", 
  "chance": 0.8,                                    
  "min_altitude": 170,                              
  "max_altitude": 190,
  "initial_velocity": {
    "linear": [0.0, 0.0, 0.0],
    "angular": [0.0, 0.0, 0.0],
    "impulse": false
  },
  "plan": {
    "goals": [
      {
        "type": "aeronauticsdiscovery:orbit",     
        "target": "minecraft:pillager_outpost",     
        "radius": 90,
        "direction": "auto"
      },
      {
        "type": "aeronauticsdiscovery:altitude",
        "min_altitude": 160,
        "max_altitude": 190
      }
    ]
  }
}
```

Field reference:

| Field              | Type     | Required           | Description                                                                                                                                       |
|--------------------|----------|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `template`         | id       | Yes                | The structure template to assemble (found in `data/<namespace>/structure/<template_name>.nbt`)                                                    |
| `target_structure` | id / tag | Yes                | Structure the patrol attaches to, e.g. `minecraft:pillager_outpost`. Use a `#tag` (e.g. `#minecraft:village`) to match any structure in that tag. |
| `chance`           | 0..1     | No (default `1.0`) | Probability a given instance of that structure gets the patrol craft. Rolled **once** per structure instance and saved.                           |
| `min_altitude`     | int      | No (default `150`) | Lower bound of the altitude band where the craft spawns.                                                                                          |
| `max_altitude`     | int      | No (default `200`) | Upper bound of the altitude band where the craft spawns. One altitude is picked at random inside the band.                                        |
| `initial_velocity` | object   | No                 | Optional velocity applied on spawn (same format as flyovers).                                                                                     |
| `plan`             | object   | No                 | Optional flight plan flown by the craft once assembled. See the [Flight plans](#flight-plans) section above.                                      |  


## Flight plans

Both **flyover events** and **patrols** can carry a flight plan: a `plan` object that tells the craft how to fly.

The plan is a list of `goals`, each dispatched by its `type`. Available goal types:

- `aeronauticsdiscovery:straight`: level straight flight; no extra fields.
- `aeronauticsdiscovery:altitude`: hold an altitude band. Fields: `min_altitude`, optional `max_altitude`.
- `aeronauticsdiscovery:orbit`: fly a circle around a target. Fields: `target` (a structure id, a `#tag`, or an `[x, z]` offset from the spawn point), `radius` (blocks), `direction` (`auto`, `cw`, `ccw`), optional `max_bank` (radians).

---

## Override existing events via datapack

You can also replace the mod's built-in events from a datapack. Minecraft applies datapack resources on top of mod resources, so placing a file at the same path overwrites the original.

For example, to replace the built-in airplane with your own:

```
<datapack_name>/
|-- pack.mcmeta
`-- data/
    `-- aeronauticsdiscovery/              // use the mod's namespace
        |-- flyover_events/
        |   `-- airplane.json              // overrides the bundled event config
        `-- structure/
            `-- airplane.nbt               // overrides the bundled template
```

Only include the files you want to change; if you just want a different spawn setting but keep the original template, only include the `.json` file.

The currently bundled files are:
### Structures:
- `airplane.nbt`          = Soaring trader plane 
- `airplane_pillager.nbt` = Pillager plane
### Flyovers: 
- `airplane.json`         = soaring trader flyover
- `airplane_pillager`     = pillager flyover 
### Patrols:
- `outpost_patrol.json`   = pillager outpost patrol
- `mansion_patrol`        = mansion mansion patrol

(This will be updated in the future to reflect new events added to the mod)