
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
      "min_altitude": 220,              // Minimum Y-level where this flyover can spawn       
      "max_altitude": 260,              // Maximum Y-level where this flyover can spawn  
      "weight": 1,                      // This is the probabily for this specific flyover to spawn
      "initial_velocity": {             // This object is optional and is used to give the flyover a specific force when it spawns, obtmitting it will be the same as writing it like this.
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
  "dimensions": ["minecraft:overworld"]   // The dimensions in which the flyover is allowed to spawn, leaving an empty list '[]' allows every dimension.
}
```

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

The currently bundled flyover files are:
- `ariplane.json`/ `ariplane.nbt` = Default plane

(This will be updated in the future to reflect new events added to the mod)
