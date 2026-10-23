package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Telepathy, after Matt Parkman: hearing thoughts, planting suggestions,
 * linking minds, puppeteering, and trapping a mind in a nightmare. It wins
 * by manipulating creatures rather than by raw force.
 */
public final class TelepathyService {
    private static final int TELEPATHY_COLOR_TINT = 0x40A070FF;
    private static final int NIGHTMARE_TINT = 0xB0100018;

    // Hostile mobs told to forget a player: mob -> (player, until).
    private static final Map<UUID, Suggestion> FORGETTING = new HashMap<>();
    // Passive creatures charmed into following a player.
    private static final Map<UUID, Suggestion> FOLLOWING = new HashMap<>();
    private static final Map<UUID, Link> LINKS = new HashMap<>();
    private static final Map<UUID, Puppet> PUPPETS = new HashMap<>();
    private static final Map<UUID, Nightmare> NIGHTMARES = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    private TelepathyService() {
    }

    // ------------------------------------------------------------------
    // Read Mind
    // ------------------------------------------------------------------

    public static LivingEntity mindInSight(ServerPlayer player, double range) {
        return Targeting.livingEntityInSight(player, range,
                target -> !(target instanceof TemporalEchoEntity));
    }

    /** Hear what a creature is thinking; what it hunts glows for you. */
    public static boolean readMind(ServerPlayer player, int powerLevel) {
        LivingEntity target = mindInSight(player, 24.0D);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "You reach out... but there is no mind there."), true);
            return false;
        }

        int glowTicks = 20 * (4 + powerLevel);
        PrivateGlow.glow(player, target, glowTicks);
        if (target instanceof Mob mob && mob.getTarget() != null) {
            PrivateGlow.glow(player, mob.getTarget(), glowTicks);
        }

        // Some minds hold a memory of a future that hasn't happened yet.
        if (!FutureVisions.tryReveal(player, target)) {
            player.sendSystemMessage(Component.literal("[" + target.getName().getString() + "] ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(thoughtsOf(target, player))
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
        whisper(player, 0.9F);
        AbilityEffects.send(player.serverLevel(), Kind.MIND_LINK,
                player.getEyePosition(), target.getEyePosition(), 0.6D);
        MasteryService.markPowerUse(player, ModAbilities.TELEPATHY_ID);
        return true;
    }

    /** A creature's surface thoughts, as Matt might hear them. */
    public static String thoughtsOf(LivingEntity target, ServerPlayer reader) {
        if (target instanceof com.jayptucker.heroesevolved.story.sylar.SylarEntity sylar) {
            return sylar.hunting() ? "A patient, terrible hunger. He is studying the pattern of your ability."
                    : "A watchmaker counting heartbeats. He knows you are different. He wants to understand why.";
        }
        int health = Math.round(100.0F * target.getHealth() / target.getMaxHealth());
        String condition = health > 75 ? "" : health > 35
                ? " It hurts... (" + health + "%)" : " So much pain... (" + health + "%)";

        if (target instanceof Player other) {
            String held = other.getMainHandItem().isEmpty()
                    ? "nothing in particular"
                    : "their " + other.getMainHandItem().getHoverName().getString();
            return "\"...\" They're thinking about " + held + "." + condition;
        }
        if (target instanceof Mob mob) {
            LivingEntity prey = mob.getTarget();
            if (prey == reader) {
                return (target instanceof Creeper
                        ? "\"Closer... closer... ssss...\""
                        : "\"Kill " + reader.getName().getString() + ". Kill.\"") + condition;
            }
            if (prey != null) {
                return "\"" + prey.getName().getString() + "... must get "
                        + prey.getName().getString() + ".\"" + condition;
            }
            if (target instanceof Enemy) {
                return "\"Hungry. Waiting. Something will come.\"" + condition;
            }
            if (target instanceof Villager villager) {
                return "\"" + (villager.isBaby() ? "Play! Play!"
                        : "Work, trade, sleep. Hope the monsters stay away.") + "\"" + condition;
            }
            if (target instanceof Animal animal) {
                return "\"" + (animal.isBaby() ? "Mama? Food?"
                        : animal.isInLove() ? "Love is in the air."
                        : "Grass. Sun. Peace.") + "\"" + condition;
            }
        }
        return "\"...\" A quiet, simple mind." + condition;
    }

    // ------------------------------------------------------------------
    // Suggestion
    // ------------------------------------------------------------------

    /**
     * "You don't see me." A hostile mob loses all interest in you; a
     * peaceful creature follows you for a while.
     */
    public static boolean suggestion(ServerPlayer player, int powerLevel) {
        LivingEntity target = mindInSight(player, 16.0D);
        if (!(target instanceof Mob mob)) {
            player.displayClientMessage(Component.literal(target instanceof Player
                    ? "Their mind is too guarded to command."
                    : "There is no mind there to suggest anything to."), true);
            return false;
        }

        long until = player.serverLevel().getGameTime() + 20L * (10 + 4 * powerLevel);
        if (mob instanceof Enemy || mob.getTarget() == player) {
            FORGETTING.put(mob.getUUID(), new Suggestion(mob, player.getUUID(), until));
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            player.displayClientMessage(Component.literal(
                    "\"You don't see me.\"").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else {
            FOLLOWING.put(mob.getUUID(), new Suggestion(mob, player.getUUID(), until));
            player.displayClientMessage(Component.literal(
                    "\"Come with me.\"").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        if (TemporalEntityEchoService.isReplaying(mob)) {
            TemporalEntityEchoService.disturb(mob, player);
        }
        AbilityEffects.send(player.serverLevel(), Kind.MIND_LINK,
                player.getEyePosition(), mob.getEyePosition(), 0.8D);
        whisper(player, 1.1F);
        MasteryService.markPowerUse(player, ModAbilities.TELEPATHY_ID);
        return true;
    }

    /** True when a suggestion forbids this mob from targeting this entity. */
    public static boolean isForbiddenTarget(Mob mob, LivingEntity newTarget) {
        Suggestion suggestion = FORGETTING.get(mob.getUUID());
        if (suggestion != null && newTarget != null
                && newTarget.getUUID().equals(suggestion.playerId)) {
            return true;
        }
        // A puppet never turns on players while its strings are held.
        return PUPPETS.values().stream().anyMatch(puppet -> puppet.mob == mob)
                && newTarget instanceof Player;
    }

    // ------------------------------------------------------------------
    // Mind Link
    // ------------------------------------------------------------------

    public static boolean hasLink(ServerPlayer player) {
        return LINKS.containsKey(player.getUUID());
    }

    /**
     * Join minds with every player nearby. Linked minds see each other
     * through walls and feel it when one of them is hurt.
     */
    public static boolean mindLink(ServerPlayer player, int powerLevel) {
        List<UUID> members = new ArrayList<>();
        members.add(player.getUUID());
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && !other.isSpectator()
                    && other.distanceToSqr(player) <= 32.0D * 32.0D) {
                members.add(other.getUUID());
            }
        }
        if (members.size() < 2) {
            player.displayClientMessage(Component.literal(
                    "There are no other minds close enough to link with."), true);
            return false;
        }

        long until = player.serverLevel().getGameTime() + 20L * (30 + 10 * (powerLevel - 3));
        LINKS.put(player.getUUID(), new Link(members, until));
        for (UUID memberId : members) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.displayClientMessage(Component.literal(
                        "Your mind is linked with " + (members.size() - 1) + " other"
                                + (members.size() > 2 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                ScreenTintPayload.send(member, TELEPATHY_COLOR_TINT, 20,
                        ScreenTintPayload.Style.VIGNETTE);
                whisper(member, 1.3F);
            }
        }
        MasteryService.markPowerUse(player, ModAbilities.TELEPATHY_ID);
        return true;
    }

    /** Warn the rest of a linked group when one of them is hurt. */
    public static void onLinkedMemberHurt(ServerPlayer victim, float damage) {
        MinecraftServer server = victim.server;
        for (Link link : LINKS.values()) {
            if (!link.members.contains(victim.getUUID())) {
                continue;
            }
            for (UUID memberId : link.members) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member == null || member == victim) {
                    continue;
                }
                member.displayClientMessage(Component.literal(
                        victim.getName().getString() + " is hurt! ("
                                + Math.round(victim.getHealth()) + " health left)")
                        .withStyle(ChatFormatting.RED), true);
                ScreenTintPayload.send(member, 0x40B01020, 8,
                        ScreenTintPayload.Style.VIGNETTE);
                PrivateGlow.glow(member, victim, 40);
            }
        }
    }

    // ------------------------------------------------------------------
    // Puppet
    // ------------------------------------------------------------------

    /** Seize a hostile mob's mind and turn it on its own kind. */
    public static boolean puppet(ServerPlayer player, int powerLevel) {
        LivingEntity target = mindInSight(player, 20.0D);
        if (!(target instanceof Mob mob) || !(mob instanceof Enemy)) {
            player.displayClientMessage(Component.literal(
                    "You need a hostile mind to take hold of."), true);
            return false;
        }

        if (TemporalEntityEchoService.isReplaying(mob)) {
            TemporalEntityEchoService.disturb(mob, player);
        }
        FORGETTING.remove(mob.getUUID());
        PUPPETS.put(player.getUUID(), new Puppet(mob,
                player.serverLevel().getGameTime() + 20L * (15 + powerLevel)));
        if (mob.getTarget() instanceof Player) {
            mob.setTarget(null);
        }
        player.serverLevel().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(Component.literal(
                "Its mind is yours. Point it at your enemies.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        MasteryService.markPowerUse(player, ModAbilities.TELEPATHY_ID);
        return true;
    }

    public static boolean hasPuppet(ServerPlayer player) {
        return PUPPETS.containsKey(player.getUUID());
    }

    public static void releasePuppet(ServerPlayer player) {
        Puppet puppet = PUPPETS.remove(player.getUUID());
        if (puppet != null && puppet.mob.isAlive()) {
            puppet.mob.setTarget(null);
            player.displayClientMessage(Component.literal(
                    "You let its mind go."), true);
        }
    }

    // ------------------------------------------------------------------
    // Nightmare
    // ------------------------------------------------------------------

    /**
     * Parkman's mental prison: the target is trapped in a nightmare. Mobs
     * flee in blind terror; players are plunged into darkness and whispers.
     */
    public static boolean nightmare(ServerPlayer player, int powerLevel) {
        LivingEntity target = mindInSight(player, 20.0D);
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "There is no mind there to trap."), true);
            return false;
        }

        int durationTicks = 20 * (6 + powerLevel);
        NIGHTMARES.put(target.getUUID(), new Nightmare(target, player.getUUID(),
                player.serverLevel().getGameTime() + durationTicks,
                1.0F + powerLevel * 0.5F));

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                durationTicks, 1));
        if (target instanceof ServerPlayer victim) {
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, durationTicks, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            ScreenTintPayload.send(victim, NIGHTMARE_TINT, durationTicks,
                    ScreenTintPayload.Style.PULSE);
            victim.sendSystemMessage(Component.literal(
                    "You are trapped inside your worst nightmare.")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else if (target instanceof Mob mob) {
            mob.setTarget(null);
        }

        player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.PLAYERS, 1.0F, 0.7F);
        AbilityEffects.send(player.serverLevel(), Kind.MIND_LINK,
                player.getEyePosition(), target.getEyePosition(), 1.2D);
        MasteryService.markPowerUse(player, ModAbilities.TELEPATHY_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Passive, awakening, overexertion
    // ------------------------------------------------------------------

    /**
     * Hostile thoughts: when something out of sight starts hunting the
     * telepath, they hear it and roughly where it is.
     */
    public static void hearHostileThoughts(ServerPlayer telepath, Mob hunter) {
        long gameTime = telepath.serverLevel().getGameTime();
        Long last = LAST_WARNING.get(telepath.getUUID());
        if ((last != null && gameTime - last < 60) || telepath.hasLineOfSight(hunter)) {
            return;
        }
        LAST_WARNING.put(telepath.getUUID(), gameTime);

        telepath.displayClientMessage(Component.literal(
                "You hear hostile thoughts... a " + hunter.getName().getString()
                        + " is hunting you, " + direction(telepath, hunter) + ".")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        whisper(telepath, 0.7F);
        PrivateGlow.glow(telepath, hunter, 40);
    }

    /** Overexertion: every voice at once, all of them too loud. */
    public static void overload(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        for (int voice = 0; voice < 4; voice++) {
            whisper(player, 0.6F + voice * 0.2F);
        }
        ScreenTintPayload.send(player, 0x80402060, 60, ScreenTintPayload.Style.PULSE);
        player.sendSystemMessage(Component.literal(
                "Too many voices at once!").withStyle(ChatFormatting.DARK_RED));
    }

    public static void stopAll(ServerPlayer player) {
        releasePuppet(player);
        LINKS.remove(player.getUUID());
        FORGETTING.values().removeIf(s -> s.playerId.equals(player.getUUID()));
        FOLLOWING.values().removeIf(s -> s.playerId.equals(player.getUUID()));
        LAST_WARNING.remove(player.getUUID());
        PrivateGlow.clearFor(player);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();

        FORGETTING.values().removeIf(s -> !s.mob.isAlive() || gameTime >= s.until);
        tickFollowers(server, gameTime);
        tickLinks(server, gameTime);
        tickPuppets(server, gameTime);
        tickNightmares(server, gameTime);
    }

    private static void tickFollowers(MinecraftServer server, long gameTime) {
        Iterator<Suggestion> iterator = FOLLOWING.values().iterator();
        while (iterator.hasNext()) {
            Suggestion suggestion = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(suggestion.playerId);
            if (player == null || !suggestion.mob.isAlive() || gameTime >= suggestion.until
                    || suggestion.mob.level() != player.level()) {
                iterator.remove();
                continue;
            }
            if (gameTime % 10 == 0 && suggestion.mob.distanceToSqr(player) > 9.0D) {
                suggestion.mob.getNavigation().moveTo(player, 1.15D);
                suggestion.mob.getLookControl().setLookAt(player);
            }
            if (gameTime % 20 == 0) {
                ((ServerLevel) suggestion.mob.level()).sendParticles(ParticleTypes.HEART,
                        suggestion.mob.getX(), suggestion.mob.getEyeY() + 0.3D,
                        suggestion.mob.getZ(), 1, 0.2D, 0.1D, 0.2D, 0.0D);
            }
        }
    }

    private static void tickLinks(MinecraftServer server, long gameTime) {
        Iterator<Map.Entry<UUID, Link>> iterator = LINKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Link link = iterator.next().getValue();
            if (gameTime >= link.until) {
                iterator.remove();
                continue;
            }
            if (gameTime % 20 != 0) {
                continue;
            }
            // Every linked mind sees every other, through anything.
            for (UUID viewerId : link.members) {
                ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
                if (viewer == null) {
                    continue;
                }
                for (UUID memberId : link.members) {
                    ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                    if (member != null && member != viewer && member.level() == viewer.level()) {
                        PrivateGlow.glow(viewer, member, 30);
                    }
                }
            }
        }
    }

    private static void tickPuppets(MinecraftServer server, long gameTime) {
        Iterator<Map.Entry<UUID, Puppet>> iterator = PUPPETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Puppet> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Puppet puppet = entry.getValue();
            Mob mob = puppet.mob;
            if (player == null || !mob.isAlive() || mob.level() != player.level()
                    || gameTime >= puppet.until
                    || EclipseService.arePowersSuppressed(player)) {
                iterator.remove();
                if (mob.isAlive()) {
                    mob.setTarget(null);
                }
                continue;
            }

            if (gameTime % 10 == 0) {
                // Turn it on the nearest other hostile creature.
                LivingEntity enemy = mob.level().getEntitiesOfClass(LivingEntity.class,
                                mob.getBoundingBox().inflate(16.0D),
                                other -> other != mob && other.isAlive()
                                        && other instanceof Enemy)
                        .stream()
                        .min((a, b) -> Double.compare(a.distanceToSqr(mob), b.distanceToSqr(mob)))
                        .orElse(null);
                if (enemy != null && mob.getTarget() != enemy) {
                    mob.setTarget(enemy);
                }
                PrivateGlow.glow(player, mob, 15);
                AbilityEffects.send((ServerLevel) mob.level(), Kind.MIND_LINK,
                        player.getEyePosition(), mob.getEyePosition(), 0.4D);
            }
            if (gameTime % 5 == 0) {
                ((ServerLevel) mob.level()).sendParticles(ParticleTypes.WITCH,
                        mob.getX(), mob.getEyeY() + 0.3D, mob.getZ(),
                        2, 0.2D, 0.1D, 0.2D, 0.0D);
            }
        }
    }

    private static void tickNightmares(MinecraftServer server, long gameTime) {
        Iterator<Nightmare> iterator = NIGHTMARES.values().iterator();
        while (iterator.hasNext()) {
            Nightmare nightmare = iterator.next();
            LivingEntity target = nightmare.target;
            if (!target.isAlive() || gameTime >= nightmare.until) {
                iterator.remove();
                continue;
            }

            ServerLevel level = (ServerLevel) target.level();
            ServerPlayer caster = server.getPlayerList().getPlayer(nightmare.casterId);
            if (gameTime % 20 == 0) {
                target.hurt(caster == null
                        ? level.damageSources().magic()
                        : level.damageSources().indirectMagic(caster, caster),
                        nightmare.damagePerSecond);
                if (target instanceof ServerPlayer victim) {
                    whisper(victim, 0.5F + level.getRandom().nextFloat() * 0.4F);
                }
            }

            // Mobs flee blindly from whoever trapped them.
            if (gameTime % 10 == 0 && target instanceof PathfinderMob mob && caster != null) {
                mob.setTarget(null);
                Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 6, caster.position());
                if (away != null) {
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4D);
                }
            }
            if (gameTime % 4 == 0) {
                level.sendParticles(ParticleTypes.SQUID_INK, target.getX(),
                        target.getEyeY(), target.getZ(), 2, 0.3D, 0.2D, 0.3D, 0.0D);
                level.sendParticles(ParticleTypes.SOUL, target.getX(),
                        target.getEyeY() + 0.4D, target.getZ(), 1, 0.3D, 0.2D, 0.3D, 0.02D);
            }
        }
    }

    // ------------------------------------------------------------------

    /** A private whisper only this player hears. */
    public static void whisper(ServerPlayer player, float pitch) {
        SoundEvent sound = player.getRandom().nextBoolean()
                ? SoundEvents.ILLUSIONER_CAST_SPELL : SoundEvents.VEX_AMBIENT;
        player.playNotifySound(sound, SoundSource.PLAYERS, 0.35F, pitch);
    }

    /** Where the other entity is, relative to where the player faces. */
    private static String direction(ServerPlayer player, LivingEntity other) {
        Vec3 offset = other.position().subtract(player.position());
        float bearing = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
        float relative = Mth.wrapDegrees(bearing - player.getYRot());
        if (Math.abs(relative) <= 45.0F) {
            return "ahead of you";
        }
        if (Math.abs(relative) >= 135.0F) {
            return "behind you";
        }
        // Minecraft yaw grows clockwise, so a positive bearing is rightward.
        return relative > 0 ? "to your right" : "to your left";
    }

    private record Suggestion(Mob mob, UUID playerId, long until) {
    }

    private record Link(List<UUID> members, long until) {
    }

    private record Puppet(Mob mob, long until) {
    }

    private record Nightmare(
            LivingEntity target,
            UUID casterId,
            long until,
            float damagePerSecond
    ) {
    }
}
