# Changelog

## 1.4.7

New: the Gunblade and the Boomerang now show drawn modifier overlays on the tool model instead of a bare icon in the slot.

**Added**
- 22 hand-drawn modifier overlays, mapped per tool through `modifier_maps` and `tinkering/modifiers/sprites/<tool>.json`.
- Gunblade (10): Diamond, Netherite, Sharpness, Luck, Fiery, Necrotic, Reinforced, Overforced, Unbreakable, Severing.
- Boomerang (12): Diamond, Netherite, Sharpness, Luck, Fiery, Necrotic, Reinforced, Overforced, Unbreakable, Emerald, Cooling, Bane of Sssss.

**Notes**
- Overlays share each tool's UV layout, so they follow the material colours of the parts you build.
- Modifiers without an overlay still apply normally, they just draw no sprite.
