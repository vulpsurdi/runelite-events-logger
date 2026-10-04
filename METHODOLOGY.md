
## Determining the source of a hit splat

Runelite only has limited information about hit splats. We are given
the damage value, the type (regular, poison, burning, bleeding, venom, disease, shield, healing, etc), whether
it's ours, and who/what got hit (the actor).

What we do know is whether the hit splat is ours and what style it is.

### Types of damage
- **NPC attack** – Not just bosses but thralls.
- **Player attack**
- **Environmental damage** – traps, cold in north Trollheim, floor damage in boss encounters, no boots of stone in Karulm dungeon
- **Items** – rock cake, locator orb, sailing sealed crates, nightshade, overloads, divines
- **Delayed damage** – Zebak's bleed after a chomp, nightmare parasite birth, Olms "burn with me", which use standard hit splat style.
- **Reflected damage** – Nex's overhead in Zaros phase, vengeance

### Other things to think about:
- A single attack can cause multiple hit splats (scythe, claws).
- There's a limit to how many hit splats are shown at once.
- Timing and projectiles have some edge cases. For example, Armadyl spiritual mages and many GWD mage minions apply
  damage instantly when they begin the cast rather than when the projectile hits the player.

### Our best-effort process
There's no perfect method of determining the source of damage, but we can get it right
most of the time. To do that, we use the process of elimination and context clues.
- XP Drops can get us an estimate on the player's attack damage.
- The player location is especially helpful. We can narrow it down to certain types
  of damage just by knowing which boss the player is fighting, or quest area they are in.
- The damage value can help us eliminate options. Certain types of damage have a minimum or maximum.
  Sometimes it's even a constant or deterministic value (like rock cake). A hit for 20 damage
  cannot be a rock cake. We have to keep in mind that the minimum damage is never a true minimum since the
  player could have less HP than the minimum damage. Lastly, we also know a max hit hit splat must be the player's own hit.
- Actor animations are good clues as to what could have caused the damage. Muspah's spikes, for example, will cause
  the player to do a "hurt" animation. Overloads will cause the player to do a "zap" animation.
- Player chat message - On Oml, you say, "Burn with me!" or "I will burn with you!" when the burn damage is applied. Many
  traps will have the player say "Ow!" like pyramid plunder, Kephri dung (if you fail to move after the knockback), and various world traps.
- Attacker animations - we can also look at the animations of attackers like the boss or player.
- Sounds - Lots of damage sources have sounds. Spike traps, boss attacks.
- Projectiles are also good indicators. They may produce an effect or sound when they hit the player,
  so that could help us determine the damage type. It's worth noting that the sound or effect may differ
  based on whether it was accurate or the player prayed properly. For example, magic attacks make a splash
  sound when they miss.
- Item usage - If the player uses a divine potion, we should expect damage to follow.

What we won't try to solve due to complexity:
- Phoenix necklace.
- Stalls such as spellbook swap or hot dwarven rock cake.
- Chugging barrel since it can only be used at a bank anyways.

## Player damage

### Thralls

- Thrall should exist as an NPC
- Thrall animates when it attacks
- It fires a projectile
- There are three types of thralls (and now 3 alternate visual variants too)
- Thrall attacks emit a sound on attack if you are close enough to them.
- Damage is between [0, 3].

### Vengeance

- The player says, "Taste vengeance!" when it procs.
- Reflected damage amount is based on how much damage you took.

## Anywhere

### Divine potions

- Item usage
- Animation
- Damage value (10)

### Items (locator orbs, rock cake)

- Item usage
- Sound
- Possibly animation
- Damage value

## Bosses

### Vorkath

**Direct:**

- Melee

**Projectile:**

- Mage - projectile, attack sound
- Range - projectile, attack sound, hit sound
- Venom breath - projectile, attack sound, hit sound
- Dragonfire breath - projectile, attack sound, hit sound
- Smite breath - projectile, attack sound, hit sound
- Fireball - projectile, attack sound

**Other:**

- Minion
- Ground poison
