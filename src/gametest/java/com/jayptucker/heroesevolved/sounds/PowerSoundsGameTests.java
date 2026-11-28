package com.jayptucker.heroesevolved.sounds;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Every power has its palette of cues, and {@link PowerSounds} plays them on presses and on the power's blows. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerSoundsGameTests {
    private static ServerPlayer user(GameTestHelper h, ResourceLocation power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "sound-test"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        if (power != null) p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        return p;
    }

    private static AbilityAction action(String path, Runnable body) {
        return SimpleAction.builder(path, 1).activate(c -> { body.run(); return true; }).build();
    }

    @GameTest(template = "rewind_empty", batch = "power.sounds")
    public static void everyPowerHasItsCuesRegisteredAndShipped(GameTestHelper h) {
        JsonObject sounds;
        try (InputStream in = PowerSoundsGameTests.class.getResourceAsStream("/assets/heroesevolved/sounds.json")) {
            h.assertTrue(in != null, "sounds.json is on the classpath");
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        // Every power a player can have has a palette (Power Amplification had none until November 2026, and played
        // no power cue at all).
        for (ResourceLocation power : AbilityRegistry.ABILITIES.keySet())
            if (com.jayptucker.heroesevolved.ability.registry.ModAbilities.rollable(power))
                h.assertTrue(PowerCue.POWERS.contains(power.getPath()), "Every power has a sound palette: " + power.getPath() + " has none");
        for (String power : PowerCue.POWERS) {
            h.assertTrue(AbilityRegistry.ABILITIES.get(ResourceLocation.fromNamespaceAndPath("heroesevolved", power)) != null,
                    "PowerCue names a real power: " + power);
            for (PowerCue cue : PowerCue.values()) {
                if (!PowerCue.has(power, cue)) continue;
                String event = PowerCue.event(power, cue);
                h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.fromNamespaceAndPath("heroesevolved", event)),
                        "Registered: " + event);
                h.assertTrue(sounds.has(event), "sounds.json lists " + event);
                for (var name : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                    String file = "/assets/heroesevolved/sounds/" + name.getAsString().substring("heroesevolved:".length()) + ".ogg";
                    h.assertTrue(PowerSoundsGameTests.class.getResource(file) != null, "Shipped: " + file);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "power.sounds")
    public static void theServersSoundsAreCounted(GameTestHelper h) {
        var p = user(h, null);
        int mark = PowerSounds.mark();
        h.getLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1);
        h.assertTrue(PowerSounds.mark() == mark + 1, "A sound played at a point is counted");
        h.getLevel().playSound(null, p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1);
        h.assertTrue(PowerSounds.mark() == mark + 2, "A sound played on an entity is counted");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "power.sounds")
    public static void aMoveSoundsLikeItsPowerAndHandMadeSoundsAreLeftAlone(GameTestHelper h) {
        var p = user(h, ModAbilities.PYROKINESIS_ID);
        Runnable bell = () -> h.getLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1);

        int mark = PowerSounds.mark();
        PowerSounds.used(p, ModAbilities.PYROKINESIS_ID, action("pyro_sound_test", () -> {}), PowerSounds.Phase.PRESS, mark);
        h.assertTrue(PowerSounds.mark() == mark + 1, "A silent fire move gets the fire cast");

        mark = PowerSounds.mark();
        bell.run();
        PowerSounds.used(p, ModAbilities.PYROKINESIS_ID, action("pyro_sound_test", () -> {}), PowerSounds.Phase.PRESS, mark);
        h.assertTrue(PowerSounds.mark() == mark + 2, "A fire move that already sounds is layered with its cue");

        mark = PowerSounds.mark();
        bell.run();
        PowerSounds.used(p, ModAbilities.TIME_MANIPULATION_ID, action("time_sound_test", () -> {}), PowerSounds.Phase.PRESS, mark);
        h.assertTrue(PowerSounds.mark() == mark + 1, "A Space-Time move with its own sound is left alone");

        mark = PowerSounds.mark();
        PowerSounds.used(p, ModAbilities.TIME_MANIPULATION_ID, action("time_sound_test", () -> {}), PowerSounds.Phase.PRESS, mark);
        h.assertTrue(PowerSounds.mark() == mark + 1, "A silent Space-Time move is given the clockwork cue");

        mark = PowerSounds.mark();
        PowerSounds.used(p, ModAbilities.PYROKINESIS_ID, action("pyro_sound_test", () -> {}), PowerSounds.Phase.RELEASE, mark);
        h.assertTrue(PowerSounds.mark() == mark, "A release that did nothing makes no sound");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "power.sounds")
    public static void aPowersBlowLandsWithItsImpact(GameTestHelper h) {
        var plain = user(h, null);
        var pyro = user(h, ModAbilities.PYROKINESIS_ID);
        Pillager first = EntityType.PILLAGER.create(h.getLevel()), second = EntityType.PILLAGER.create(h.getLevel());
        for (Pillager mob : new Pillager[]{first, second}) {
            mob.setNoAi(true);
            mob.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
            h.getLevel().addFreshEntity(mob);
        }
        try {
            int mark = PowerSounds.mark();
            first.hurt(plain.damageSources().indirectMagic(plain, plain), 4);
            int ordinary = PowerSounds.mark() - mark;
            mark = PowerSounds.mark();
            second.hurt(pyro.damageSources().indirectMagic(pyro, pyro), 4);
            h.assertTrue(PowerSounds.mark() - mark == ordinary + 1,
                    "A firebender's blow adds the fire impact to the hurt sound (" + ordinary + " vs " + (PowerSounds.mark() - mark) + ")");
            h.succeed();
        } finally {
            first.discard();
            second.discard();
        }
    }
}
