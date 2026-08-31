# Pin Guide

Pins are invisible markers you can place on structures to give them custom behaviors (explosions, mob spawning, mob seating, stretching ropes and bridges, configure blocks etc).

Due to schematics limitations, they are sometimes essential to make templates properly work as flyover events.

Everything is done through the **Pin Wand** item and its clickable chat UI. The Pin Wand is a utility item that automatically executes `/pinwand` commands.

> **Important:** Pins are invisible to everyone unless they are holding a Pin Wand. If you can't see your pins, make sure the wand is in your hand or off-hand.

---

## Table of Contents

- [Pin Guide](#pin-guide)
  - [Table of Contents](#table-of-contents)
  - [1. Obtain the Wand](#1-obtain-the-wand)
  - [2. The Pin Wand UI](#2-the-pin-wand-ui)
    - [The trigger sub-menu](#the-trigger-sub-menu)
    - [The emitter sub-menu](#the-emitter-sub-menu)
  - [3. Placing Pins](#3-placing-pins)
  - [4. Behavior Types](#4-behavior-types)
    - [Explosive](#explosive)
    - [Spawn Mob](#spawn-mob)
    - [Rope Connector](#rope-connector)
    - [Fill Up Balloon](#fill-up-balloon)
  - [5. Triggers](#5-triggers)
  - [6. Chain Reactions (Emitter)](#6-chain-reactions-emitter)
  - [7. Inspecting and Removing Pins](#7-inspecting-and-removing-pins)
    - [Inspect a pin](#inspect-a-pin)
    - [Remove via command](#remove-via-command)
    - [Automatic Despawn](#automatic-despawn)
  - [8. Using Pins in Structure Templates](#8-using-pins-in-structure-templates)
    - [Testing pins](#testing-pins)

---

## 1. Obtain the Wand

Right now, the Wand is a developer item, and it does not appear in the Creative inventory. To obtain it, you can run a give command:
```
/give @s aeronauticsdiscovery:pin_wand
```

---

## 2. The Pin Wand UI

**Right-click while holding the wand** (don't click a block) to open the configuration screen in chat. It will look something like this:

```
[ Pin Wand - explosive]
  Behavior: explosive  [⤶ Cycle]
  Parameters:
    Power: 4.0  [✎]
  Triggers: Assembled, External Force, Projectile  [✎ Triggers]
  Emitter: (off)  [✎ Emitter]
  Right-click a block to place the pin
```

All the buttons you see are clickable directly from the chat. Here's what each button does:

| Button                    | What it does                                                                                                  |
|---------------------------|---------------------------------------------------------------------------------------------------------------|
| `[⤶ Cycle]`               | Switches to the next [behavior](#4-behavior-types) type)                                                      |
| `[✎]` next to a parameter | Pre-fills the chat with `/pinwand set <key> <current_value>`, you only need to edit the value and press Enter |
| `[✎ Triggers]`            | Opens the [triggers](#5-triggers) sub-menu                                                                    |
| `[✎ Emitter]`             | Opens the [emitter](#6-chain-reactions-emitter) sub-menu                                                      |


### The trigger sub-menu

After clicking `[✎ Triggers]`, you see a list of trigger kinds, each with a toggle button:

```
[✓] Assembled
[✕] External Force
[✕] Player Proximity
[✕] Projectile
[✕] Explosion
[✕] Released
[← Back]
```

Click a `[✓]` or `[✕]` to toggle that trigger on or off. Click `[← Back]` to return to the main UI.

### The emitter sub-menu

After clicking `[✎ Emitter]`, you see:

```
  Radius: (off)  [✎]
  Speed: -  [✎]
[← Back]
```

Click `[✎]` next to Radius to set a propagation radius (set to `0` to disable). Click `[✎]` next to Speed to set how fast the chain reaction travels (in blocks per tick).
The emitted trigger will be the same type as the one that triggered the pin in the first place.

---

## 3. Placing Pins

Once you have configured the Wand:

1. **Right-click a block** with the wand.
2. The pin appears as a colored translucent cube appears.
3. A chat message confirms placement:

```
[✧] Placed explosive at 102 64 -380
```

The pin inherits whatever behavior, parameters, triggers, and emitter settings the wand had when you placed it.

If you place a pin inside a structure (sub-level), it is automatically bound to that structure.

> [!TIP]
> **Right-click a block while sneaking** to place another pin inside the same block. A block can hold several pins at once. A normal right-click shows the configuration panels for **all** the pins in that block.

The color of the cube indicates the behavior type:

| Color  | Behavior        |
|--------|-----------------|
| Red    | Explosive       |
| Green  | Spawn Mob       |
| Orange | Rope Connector  |
| Cyan   | Fill Up Balloon |

---

## 4. Behavior Types

### Explosive

Detonates when triggered .

| Parameter | Default | Description                                    |
|-----------|---------|------------------------------------------------|
| Power     | `4.0`   | Explosion strength (same scale as vanilla TNT) |

> [!NOTE]
> When a structure crashes, only the **closest** explosive pin to the impact point detonates. If you want multiple pins to detonate, enable the [emitter](#6-chain-reactions-emitter) to propagate the explosion chain to other pins.

---

### Spawn Mob

Spawns a mob at the pin's location when triggered. If the pin sits on a Create Seat block, the mob is spawned and seated on it; otherwise it spawns standing at the pin.

| Parameter | Default              | Description                                                    |
|-----------|----------------------|----------------------------------------------------------------|
| mob_id    | `minecraft:pillager` | Entity type to spawn (use any valid mob ID)                    |
| nbt       | *(empty)*            | Optional raw NBT (incl. item components) applied to the mob    |

> [!NOTE]
> This replaces the former **Mob Spawn Point** (`mob_spawn_point`) and **Seat Mob** (`seat_mob`) behaviors, which are deprecated and scheduled for removal but still work on pins placed before the merge.

### Rope Connector

Connects two pins (start and end) with a rope bridge. Both pins must be on the same channel and within range of each other. 

> [!NOTE]
> **This behavior requires a RopeStrandHolder block (rope connector, rope winch) at each pin's position.**


| Parameter    | Default              | Description                                           |
|--------------|----------------------|-------------------------------------------------------|
| channel      | `0`                  | Pins only connect to partners on the same channel     |
| max_range    | `64.0`               | Maximum search distance for a partner pin (in blocks) |
| make_bridge  | `false`              | Whether to auto-create bridge planks                  |
| bridge_block | `minecraft:oak_slab` | Block used for bridge planks                          |

Use different channel numbers to create separate bridge pairs that don't interfere with each other. For example, channel `0` for one bridge, channel `1` for another.

### Fill Up Balloon

It must be placed on a Hot air balloon burner or Steam vent in order to work. It will scan and fill up the attached balloon by the defined `fill_amount` value once triggered.

| Parameter   | Default | Description                                                          |
|-------------|---------|----------------------------------------------------------------------|
| fill_amount | `1.0`   | Fraction of the balloon's capacity to fill (e.g. `0.5` = half full)  |

> [!NOTE]
> If the balloon is still assembling or has no heaters, the pin waits and retries for up to ~60 seconds before giving up.


---

## 5. Triggers

Triggers control *when* a pin activates. Click `[✎ Triggers]` in the wand UI to configure them.

| Trigger              | What activates it                                                                 |
|----------------------|-----------------------------------------------------------------------------------|
| **Assembled**        | Structure finishes assembling (e.g. by using a Physics Assembler)                 |
| **External Force**   | Structure crashes into something                                                  |
| **Projectile**       | When an arrow hits the pin                                                        |
| **Explosion**        | Triggered by explosive pins (useful for chain explosions)                         |
| **Player Proximity** | A non-creative, non-spectator player comes within the proximity radius of the pin |
| **Released**         | The flyover is released (when the player gets close to the it)                    |

> [!NOTE]
> The proximity radius is a mod config option (`pin.playerProximityRadius`, default `5.0`, range `1.0`–`256.0` blocks), set in the Mod Config file.

> [!NOTE]
> Pins will disappear after they have been triggered.

>[!TIP]
> Multiple triggers can be enabled at once on a pin.

---

## 6. Chain Reactions (Emitter)

The emitter lets a pin propagate its trigger to nearby pins. When a pin fires, it sends its trigger to all pins within the configured radius.

Click `[✎ Emitter]` in the wand UI to configure:

| Setting    | Description                                                            |
|------------|------------------------------------------------------------------------|
| **Radius** | How far the chain reaction reaches (in blocks). Set to `0` to disable. |
| **Speed**  | How fast the trigger travels (blocks per tick). Higher = faster.       |

The delay between pins is based on distance: **delay (in ticks) = distance / speed**. For example, at speed `2.0`, a pin 10 blocks away fires ~5 ticks later. At speed `0`, all pins in range fire instantly.

---

## 7. Inspecting and Removing Pins

### Inspect a pin

**Right-click a block that has a pin** with the wand. You'll see something like this:

```
[- Pin Info - explosive]
  Position: 102 64 -380
  Config:
    Power: 12.0
  Triggers: Assembled, External Force, Projectile
  Emitter: (off)
  Bound: Yes
  [✕ Remove]
```

> [!NOTE]
> If a block holds several pins, a normal right-click shows the config panel for **every** pin in that block, each with its own `[✕ Remove]` button. Use **Shift + right-click** to add another pin to the same block instead.

Click `[✕ Remove]` to delete the pin instantly.

### Remove via command

If you know the coordinates, you also need the pin's entity ID (shown when inspecting it):

```
/pinwand remove 102 64 -380 1234
```

> [!NOTE]
> You can remove ALL pins currently loaded in the game by using the command `/kill @e[type=aeronauticsdiscovery:pin]`

### Automatic Despawn

Pins contained inside of flyovers will despawn with the flyover.


---

## 8. Using Pins in Structure Templates

Pins can be embedded in `.nbt` structure templates, so that they activate automatically when the structure assembles if the trigger "assembled" is active.

> [!WARNING]
> When exporting a template using the Structure Block, you have to make sure "Include Entities" is ON.

### Testing pins

If you want to manually trigger a pin, look at it and run:
```
/pintest
```

---

New pins are planned for the future. Whenever a new pin will be released, this guide will be updated.