---
name: minecraft-armor
description: Design and build wearable 3D armor, suits, costumes, helmets and other worn gear for the Heroes Evolved mod - skin-tight suits, raised emblems, fins, belts, cuffs, tanks, soles, anything that should stand off the body in 3D - with its own armor model, a texture generator and in-game photographs. Use this whenever the task involves making or changing anything a player or mob wears (a new suit, armor set, helmet, mask, cape-like piece or costume), when worn gear looks too thick, too flat or too bulky, or when the user shares a picture of armor they want something like.
---

# Wearable 3D armor for Heroes Evolved

Vanilla armor is a set of boxes one pixel bigger than the body, painted. That is right for plate
armor and wrong for almost everything a hero wears. A suit should hug the body, and its details
(a chest plate, fins, a belt, cuffs, a tank) should stand off it in real 3D. This skill does both
with plain NeoForge: a custom `HumanoidArmorModel` per look, extra boxes as children of the body
parts, and a texture drawn by a script. The reference implementation is `client/SuitModels`
(the radiation and velocity suits) with `tools/generate-suit-textures.py`; read both first.

Related notes: how the suits protect (`Suits`, one question for every hazard), the Suit Fabricator, dyeable layers and
the burnt-skin repaint are in [docs/agents/hazards-and-suits.md](../../../docs/agents/hazards-and-suits.md); preview
rules are in [docs/agents/previews.md](../../../docs/agents/previews.md). What the player sees of the suits:
[docs/protective-suits.md](../../../docs/protective-suits.md).

## Is it armor at all?

- **Armor** is an item in an armor slot that anyone (a player, an armor stand, a mob) can wear and take off. This skill.
- **A character's look** (Sylar, the Company's agents, story actors) is a skin drawn by a generator:
  `tools/GenerateCompanyTextures.java`,
  `tools/GenerateStoryActorTextures.java` (Noah, Claire, Peter, Ted, and Sylar for both the story and the boss). Change the generator, never the PNG. A boss's costume belongs here, not in
  an armor model (see the bosses skill, [../minecraft-bosses/SKILL.md](../minecraft-bosses/SKILL.md)).
- **A prop on the face or head** (glasses, a headset, a hat) is an item with a 3D model drawn by the head item layer,
  `Equipable` in the head slot: `item/HornRimmedGlassesItem` is the pattern. Painting it into an 8-pixel face reads as
  a mask ([docs/agents/story-and-scenes.md](../../../docs/agents/story-and-scenes.md), "Props on a face are items").
- **A change to the skin itself** (burning, frost creeping, stone spreading) repaints a copy of the player's skin
  (`client/BurnClient`, [hazards-and-suits.md](../../../docs/agents/hazards-and-suits.md)).

## 1. Design before code

Write down, for each piece (head, chest, legs, feet):

- **What is skin-tight** (painted on the thin base boxes) and **what stands off** (extra boxes).
  Pick three to six 3D details for the whole set; more reads as clutter at Minecraft's scale.
  Good ones: a raised emblem plate, fins or ear pieces, a belt, wrist or forearm bands, boot
  cuffs or thick soles, a respirator, a back tank, shoulder plates.
- **Colours**: one base colour and one trim colour reads best. If the base should be dyeable,
  the trim goes in an undyed overlay (see 4).
- **Originality**: take inspiration, never copy a real character's emblem or exact costume.
  Change the emblem's shape, the trim colour and the arrangement of details. Say what you
  changed and why when you show the result.

## 2. The thin base

Inflate each box (`CubeDeformation`, in model pixels) only just past the player's own outer skin
layer, so no skin pixel pokes through, and keep outer pieces over the leggings:

| Layer | Vanilla | Use | Must clear |
| --- | --- | --- | --- |
| head (helmet) | 1.0 | 0.55 | the hat layer, 0.5 |
| body, arms, boots (outer) | 1.0 | 0.42 | the jacket and sleeves, 0.25, and the leggings |
| leggings (inner) | 0.5 | 0.38 | the pants, 0.25 (legs are shrunk 0.1 by vanilla) |

Build from vanilla's mesh so the pivots and UVs stay standard:

```java
MeshDefinition mesh = HumanoidArmorModel.createBodyLayer(new CubeDeformation(0.42F));
PartDefinition root = mesh.getRoot();
root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
        .addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.55F)), PartPose.ZERO);
root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO); // frees the hat's texture area
```

## 3. 3D details

Add each detail as a **child of the part it belongs to**, in that part's own coordinates, so it
moves with the limb and shows only when that piece is worn (the armor layer shows the head for
helmets, body and arms for chestplates, body and legs for leggings, legs for boots):

```java
root.getChild("body").addOrReplaceChild("plate", CubeListBuilder.create().texOffs(50, 0)
        .addBox(-3, 1.5F, -2.95F, 6, 5, 1), PartPose.ZERO);              // raised chest plate
root.getChild("right_arm").addOrReplaceChild("band", CubeListBuilder.create().texOffs(32, 4)
        .addBox(-3, 7, -2, 4, 1, 4, new CubeDeformation(0.5F)), PartPose.ZERO);   // a band round the forearm
```

Part frames (pixels, y down): head box `(-4,-8,-4)` 8x8x8 from the neck; body `(-4,0,-2)` 8x12x4
from the neck; right arm `(-3,-2,-2)` 4x12x4 from the shoulder (left arm `(-1,-2,-2)`, mirrored);
legs `(-2,0,-2)` 4x12x4 from the hip. A detail that wraps a part is the part's box with a little
more deformation (a band at 0.5 over a body at 0.42); one that stands out is a box placed just
outside the surface (the chest plate's front is at z -2.95, the body's at -2.42). Mirror left-side
details with `.mirror()` so they share texture. Keep details thin: one pixel stands out clearly.

## 4. Textures

The armor texture is 64x32 in the vanilla layout (head 0,0; body 16,16; arm 40,16; leg 0,16).
Details take their texture from the **hat area, x 32-64, y 0-16**, freed in step 2, plus x 56-64,
y 16-32. A box at `texOffs(u, v)` sized w x h x d uses a (2d+2w) x (d+h) block:
top `(u+d, v)` w x d, bottom `(u+d+w, v)` w x d, right `(u, v+d)` d x h, front `(u+d, v+d)` w x h,
left `(u+d+w, v+d)` d x h, back `(u+2d+w, v+d)` w x h. Lay the blocks out on paper first and
check that none overlap.

Draw every texture with a Python script (standard library only, deterministic, like
`tools/generate-suit-textures.py`: `faces()`, `paint()`, `box()` and `extra()` do the layout). Run it with
`py -3 tools/generate-suit-textures.py` on Windows (`python3` elsewhere); it writes the worn layers to
`assets/heroesevolved/textures/models/armor/<name>_layer_1.png` (head, chest, boots), `_layer_2.png` (leggings) and
their `_overlay` versions, and the item icons beside the other item textures. Never edit those PNGs by hand.
For a dyeable set, register two `ArmorMaterial.Layer`s (`""` dyeable, `"_overlay"` not), give the
items a default `DataComponents.DYED_COLOR`, tag them `minecraft:dyeable`, register an item color
handler for layer 0, and paint the base in light grey with the trim in the overlay. A cut-out in
the overlay (an emblem's shape) shows the dye through it.

Item icons: a base and an overlay too, 16x16, from character masks (see `sprite()` and `trim()`).

## 5. Wiring

- Register the layer definitions in `EntityRenderersEvent.RegisterLayerDefinitions`, bake them
  lazily from `Minecraft.getEntityModels()`, and drop the baked models in `AddLayers` (a resource
  reload).
- Return them from an `IClientItemExtensions.getHumanoidArmorModel` registered in
  `RegisterClientExtensionsEvent`; NeoForge copies the body's pose onto the returned model.
  Leggings get the inner model; everything else the outer one.
- Armor values, durability and repair live in an `ArmorMaterial` (`hazard/SuitMaterials`); add the
  items to the `minecraft:{head,chest,leg,foot}_armor` and `enchantable/*` tags.
- The rest of a new set: the items (`hazard/SuitItems`, `SuitArmorItem`), item models and icons, `en_us.json` names, and
  a recipe (the Suit Fabricator's `SuitPatterns`, matched by tag so other mods' materials work, or a normal recipe).
  JEI's Suit Fabricator page reads `SuitPatterns`, so a new pattern shows there by itself; anything else goes through
  the checklist in [compat.md](../../../docs/agents/compat.md).
- If the set protects against something, add a `Suits.Kind` and answer through `Suits` (a quarter a piece); never add a
  special case in the hazard's own code.
- Mobs and armor stands draw the same model through the same `IClientItemExtensions`; check both in the preview.

## 6. Photograph it

Never judge armor from its texture alone. Stage it on the player and on armor stands (one dyed a
different colour), front, back and close, with `uitest/SuitPreview`
(`./gradlew runClient --init-script tools/suit-preview.init.gradle`, shots in
`build/suit-preview/screenshots/`), and look at every shot. Check: no skin pokes through, details
sit on the body (not floating, not sunk), mirrored details match, dyed and undyed both read, and
the trim isn't bulkier than the reference. Hide chat in previews
(`options.chatVisibility().set(ChatVisiblity.HIDDEN)`). The preview's flag is `heroes.preview.suits`; a finished run
prints `PREVIEW_DONE` (on Windows Gradle may still call it failed, and the shots are fine). Show the user the shots,
and say what was changed from any reference picture they gave and why.
