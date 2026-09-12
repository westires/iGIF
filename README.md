# iGIF

Animated GIF to Minecraft Title, Subtitle, and ActionBar via ItemsAdder.

**Author:** Westires  
**Platform:** Paper 1.21.4+  
**Java:** 21+  
**Depends:** ItemsAdder  
**SoftDepends:** Skript  

---

## Installation

1. Drop `igif-1.0.0.jar` into your `plugins/` folder.
2. Make sure **ItemsAdder** is installed.
3. Start the server — iGIF will create its folder structure automatically.

---

## Workflow

```
1. Place your GIF:
   plugins/iGIF/animations/welcome/animation.gif

2. Edit the animation config:
   plugins/iGIF/animations/welcome/config.yml

3. Generate the animation:
   /igif generate welcome

4. iGIF extracts frames → generates ItemsAdder assets → triggers /iazip
   Wait for /iazip to finish, then run /iareload in-game.

5. Play the animation:
   /igif play welcome PlayerName
```

---

## Animation Config (`animations/<name>/config.yml`)

```yaml
name: welcome

source: animation.gif

display:
  type: TITLE          # TITLE | SUBTITLE | ACTIONBAR

animation:
  fps: 10
  loop: true

title:
  fade-in: 0
  stay: 20
  fade-out: 0

resolution:
  width: 64
  height: 32
```

---

## Commands

| Command | Description |
|---|---|
| `/igif create <name>` | Create an animation directory with a template config |
| `/igif generate <name>` | Process GIF and generate ItemsAdder assets |
| `/igif regenerate <name>` | Re-process an existing animation |
| `/igif play <name> <player> [type]` | Play animation for a player |
| `/igif stop <name> <player>` | Stop a specific animation |
| `/igif stopall <player>` | Stop all animations for a player |
| `/igif list` | List all loaded animations |
| `/igif info <name>` | Show animation details |
| `/igif reload` | Reload config and animations |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `igif.admin` | Full access (grants all below) | op |
| `igif.create` | Create animation directories | op |
| `igif.generate` | Generate/process animations | op |
| `igif.play` | Play animations | op |
| `igif.stop` | Stop animations | op |
| `igif.reload` | Reload configuration | op |
| `igif.list` | List animations | op |
| `igif.info` | View animation info | op |

---

## Skript Integration (optional)

If Skript is installed, these effects become available:

```skript
# Play an animation for a player
play igif "welcome" for player
play igif animation "welcome" for player

# Stop an animation
stop igif "welcome" for player
stop igif animation "welcome" for player
```

---

## Public API

Add iGIF as a dependency and use the registered service:

```java
iGIFAPI api = getServer().getServicesManager().load(iGIFAPI.class);

// Check if ready
api.isAnimationReady("welcome");

// Play
api.play(player, "welcome");
api.play(player, "welcome", DisplayType.ACTIONBAR);

// Stop
api.stop(player, "welcome");
api.stopAll(player);

// Check state
api.isPlaying(player, "welcome");

// Generate async
api.generate("welcome").thenAccept(anim -> {
    // animation is ready
});
```

### Events

```java
@EventHandler
void onStart(IGIFAnimationStartEvent e) { ... }

@EventHandler
void onStop(IGIFAnimationStopEvent e) { ... }

@EventHandler
void onGenerated(IGIFAnimationGeneratedEvent e) { ... }
```

---

## Global Config (`config.yml`)

```yaml
debug: false

processing:
  async: true
  cleanup-old-frames: true

limits:
  max-width: 256
  max-height: 256
  max-frames: 500

cache:
  enabled: true

console:
  colored-output: true

itemsadder:
  namespace: igif
  auto-reload: true   # dispatches /iazip after generation
```

---

## Folder Structure

```
plugins/
└── iGIF/
    ├── config.yml
    ├── messages.yml
    ├── animations/
    │   └── welcome/
    │       ├── animation.gif
    │       └── config.yml
    └── generated/
        └── welcome/
            ├── frame_0001.png
            ├── frame_0002.png
            └── ...
```

ItemsAdder assets are written to:
```
plugins/ItemsAdder/data/items_packs/igif/animations/welcome.yml
plugins/ItemsAdder/data/resource_pack/assets/igif/textures/animations/welcome/frame_0001.png
```

---

## Building from Source

```bash
mvn clean package
# Output: target/igif-1.0.0.jar
```

Requires Java 21 and Maven 3.6+.