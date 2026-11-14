package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Shapeshifting: which forms open when, the body a form gives, kin, the library, forced reverts, people's forms and chat. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ShapeshiftGameTests {
    private static ServerPlayer shifter(GameTestHelper h, int tier) {
        return GridGameTests.player(h, ModAbilities.SHAPESHIFTING_ID, tier);
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z))));
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void close(float a, float b, String message, GameTestHelper h) {
        h.assertTrue(Math.abs(a - b) < 1.0E-3, message + " (" + a + " vs " + b + ")");
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.forms")
    public static void formsOpenByPowerLevelAndKinGroupVariants(GameTestHelper h) {
        var level = h.getLevel();
        record Case(EntityType<?> type, int level) {}
        for (Case c : List.of(new Case(EntityType.COW, 1), new Case(EntityType.CHICKEN, 1), new Case(EntityType.ZOMBIE, 3),
                new Case(EntityType.SPIDER, 3), new Case(EntityType.VILLAGER, 3), new Case(EntityType.ENDERMAN, 6),
                new Case(EntityType.PILLAGER, 6), new Case(EntityType.IRON_GOLEM, 10), new Case(EntityType.RAVAGER, 10),
                new Case(EntityType.WARDEN, 15), new Case(EntityType.WITHER, 20), new Case(EntityType.ENDER_DRAGON, ShapeshiftForms.NEVER))) {
            int got = ShapeshiftForms.requiredLevel(ShapeshiftForms.example(level, c.type));
            h.assertTrue(got == c.level, c.type + " should open at " + c.level + ", not " + got);
        }
        h.assertTrue(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.HUSK))
                .equals(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.ZOMBIE))), "Husks are zombie kin");
        h.assertTrue(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.STRAY))
                .equals(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.SKELETON))), "Strays are skeleton kin");
        h.assertTrue(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.WITCH))
                .equals(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.VINDICATOR))), "Witches ride with raiders");
        h.assertTrue(!ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.ZOMBIE))
                .equals(ShapeshiftForms.kin(ShapeshiftForms.example(level, EntityType.SKELETON))), "Zombies and skeletons are not kin");
        h.assertTrue(ShapeshiftForms.size(ShapeshiftForms.example(level, EntityType.IRON_GOLEM)) == ShapeshiftForms.Size.LARGE
                && ShapeshiftForms.size(ShapeshiftForms.example(level, EntityType.WARDEN)) == ShapeshiftForms.Size.BOSS
                && ShapeshiftForms.size(ShapeshiftForms.example(level, EntityType.ZOMBIE)) == ShapeshiftForms.Size.SMALL, "Size classes");

        ServerPlayer novice = shifter(h, 1);
        ServerPlayer other = shifter(h, 5);
        try {
            h.assertTrue(ShapeshiftService.refusal(novice, mob(h, EntityType.ZOMBIE, 3, 6)).equals("too_big"), "A Level 1 shifter can't be a zombie");
            h.assertTrue(ShapeshiftService.refusal(novice, mob(h, EntityType.COW, 5, 6)) == null, "A Level 1 shifter can be a cow");
            h.assertTrue("too_big".equals(ShapeshiftService.refusal(novice, other)), "A person's shape waits for Level 6");
        } finally { ShapeshiftService.forget(novice); ShapeshiftService.forget(other); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.body")
    public static void aFormGivesItsBodyAndRevertingGivesYoursBack(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        try {
            p.setHealth(10);
            h.assertTrue(ShapeshiftService.morph(p, EntityType.IRON_GOLEM), "A Level 10 shifter can take an iron golem's shape");
            h.assertTrue(ShapeshiftService.form(p) == EntityType.IRON_GOLEM && MorphBodies.form(p) == EntityType.IRON_GOLEM, "The form is worn");
            close(p.getBbHeight(), EntityType.IRON_GOLEM.getDimensions().height(), "The hitbox is the golem's", h);
            close(p.getBbWidth(), EntityType.IRON_GOLEM.getDimensions().width(), "The hitbox is the golem's width", h);
            close(p.getMaxHealth(), 60, "Health is capped at 20 + 10 per tier", h);
            close(p.getHealth(), 30, "Half health stays half health", h);
            close((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), 15, "A golem hits like a golem", h);
            close((float) p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1, "A golem can't be knocked back", h);
            close((float) p.getAttributeValue(Attributes.STEP_HEIGHT), 1, "A golem steps up a whole block", h);
            h.assertTrue(ShapeshiftService.library(p).contains(EntityType.IRON_GOLEM), "Taking a shape learns it");

            h.assertTrue(ShapeshiftService.morph(p, EntityType.CHICKEN), "Changing straight to another form");
            close(p.getMaxHealth(), 4, "A chicken is frail", h);
            close(p.getHealth(), 2, "Still half health", h);
            h.assertTrue(p.getBbHeight() < 1, "A chicken is small");

            h.assertTrue(ShapeshiftService.revert(p), "Back to your own body");
            h.assertTrue(!ShapeshiftService.morphed(p) && MorphBodies.form(p) == null, "No form is worn");
            close(p.getMaxHealth(), 20, "Your own health", h);
            close(p.getHealth(), 10, "Half health again", h);
            close((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1, "Your own fists", h);
            close(p.getBbHeight(), Player.STANDING_DIMENSIONS.height(), "Your own height", h);
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.room")
    public static void aBigFormNeedsRoom(GameTestHelper h) {
        ServerPlayer p = shifter(h, 4);
        try {
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            h.assertTrue(!ShapeshiftService.morph(p, EntityType.IRON_GOLEM), "A golem doesn't fit under a two-block ceiling");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "A zombie does");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.kin", timeoutTicks = 120)
    public static void kinIgnoreYouUntilYouGiveYourselfAway(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        var seen = mob(h, EntityType.ZOMBIE, 3, 7);
        var hidden = mob(h, EntityType.ZOMBIE, 9, 3);
        for (int y = 2; y <= 5; y++) for (int z = 1; z <= 5; z++) h.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        seen.setTarget(p);
        hidden.setTarget(p);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            h.assertTrue(seen.getTarget() == p, "A zombie that watched you change keeps fighting");
            h.assertTrue(hidden.getTarget() == null, "A zombie that couldn't see you loses you");

            var husk = mob(h, EntityType.HUSK, 1, 7);
            husk.setTarget(p);
            h.assertTrue(husk.getTarget() == null, "Husks take you for kin");
            var skeleton = mob(h, EntityType.SKELETON, 5, 7);
            skeleton.setTarget(p);
            h.assertTrue(skeleton.getTarget() == p, "Skeletons aren't fooled by a zombie");

            husk.hurt(p.damageSources().playerAttack(p), 1);
            husk.setTarget(p);
            h.assertTrue(husk.getTarget() == p, "Hurting kin gives you away to it");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }

        // The watcher loses sight behind a wall and, after two seconds, loses you.
        for (int x = 1; x <= 6; x++) h.setBlock(new BlockPos(x, 2, 5), Blocks.STONE);
        for (int x = 1; x <= 6; x++) h.setBlock(new BlockPos(x, 3, 5), Blocks.STONE);
        for (int x = 1; x <= 6; x++) h.setBlock(new BlockPos(x, 4, 5), Blocks.STONE);
        h.runAfterDelay(ShapeshiftService.LOSE_SIGHT_TICKS + 10, () -> {
            try { h.assertTrue(seen.getTarget() == null, "Out of sight for two seconds, the watcher forgets you"); }
            finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.library")
    public static void theLibraryKeepsTheNewestShapes(GameTestHelper h) {
        ServerPlayer p = shifter(h, 1);
        try {
            int capacity = ShapeshiftForms.capacity(1, false);
            List<EntityType<?>> animals = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
                    EntityType.RABBIT, EntityType.FOX, EntityType.CAT);
            for (EntityType<?> type : animals) ShapeshiftService.learn(p, type);
            List<EntityType<?>> kept = ShapeshiftService.library(p);
            h.assertTrue(kept.size() == capacity, "A Level 1 library holds " + capacity + ", not " + kept.size());
            h.assertTrue(kept.equals(animals.subList(animals.size() - capacity, animals.size())), "The oldest shapes are forgotten first");
            h.assertTrue(!ShapeshiftService.learn(p, EntityType.CAT), "A shape already known isn't learned twice");

            h.assertTrue(ShapeshiftService.cycle(p), "Shift-press wears a remembered shape");
            h.assertTrue(ShapeshiftService.form(p) == EntityType.CAT, "Starting from the newest");
            h.assertTrue(ShapeshiftService.cycle(p) && ShapeshiftService.form(p) == kept.get(0), "Then wrapping round to the oldest");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.library")
    public static void theFormWheelOnlyWearsRememberedShapes(GameTestHelper h) {
        ServerPlayer p = shifter(h, 3);
        try {
            ShapeshiftService.learn(p, EntityType.COW);
            ShapeshiftService.learn(p, EntityType.PIG);
            h.assertTrue(!ShapeshiftService.choose(p, "minecraft:zombie"), "A shape never studied can't be picked");
            h.assertTrue(!ShapeshiftService.choose(p, "minecraft:not_a_creature"), "Nor can nonsense");
            h.assertTrue(!ShapeshiftService.morphed(p), "so nothing changed");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(ShapeshiftService.CHOICE_GAP + 1, () -> {
            try {
                h.assertTrue(ShapeshiftService.choose(p, "minecraft:pig") && ShapeshiftService.form(p) == EntityType.PIG, "Picking a remembered pig");
                h.assertTrue(!ShapeshiftService.choose(p, "minecraft:cow"), "A second pick straight after is ignored");
                h.assertTrue(ShapeshiftService.form(p) == EntityType.PIG, "so the pig stays");
            } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
            h.runAfterDelay(ShapeshiftService.CHOICE_GAP + 1, () -> {
                try {
                    h.assertTrue(ShapeshiftService.choose(p, "") && !ShapeshiftService.morphed(p), "The centre of the wheel gives your own body back");
                } finally { ShapeshiftService.forget(p); }
                h.succeed();
            });
        });
    }

    // ---- Phase 3: people ----

    private static ServerPlayer person(GameTestHelper h, int powerLevel) {
        ServerPlayer p = shifter(h, 6);
        PlayerProgressionService.setPowerLevel(p, powerLevel);
        return p;
    }

    /** Makes an operator at level 4: the game-test server's own {@code op} grants level 0. */
    private static void op(net.minecraft.server.players.PlayerList list, ServerPlayer player) {
        list.getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 4, false));
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.people")
    public static void aPersonsShapeIsTheirFaceAndAPlainBody(GameTestHelper h) {
        ServerPlayer shifter = person(h, 6), other = person(h, 1);
        try {
            other.getGameProfile().getProperties().put("textures", new Property("textures", "c2tpbg==", null));
            h.assertTrue(ShapeshiftService.refusal(shifter, other) == null, "At Level 6 a person can be studied");
            ShapeshiftForm form = ShapeshiftService.formOf(other);
            h.assertTrue(form.person() && form.type() == EntityType.PLAYER && form.key().equals("player:" + other.getUUID()), "The form is the person's");
            h.assertTrue(ShapeshiftService.morph(shifter, form), "Taking the person's shape");
            h.assertTrue(ShapeshiftService.posingAs(shifter) != null && ShapeshiftService.posingAs(shifter).getId().equals(other.getUUID()),
                    "The shapeshifter now poses as them");
            close(shifter.getBbHeight(), 1.8F, "A person's height", h);
            close(shifter.getMaxHealth(), 20, "and a plain body, whatever the person has", h);
            h.assertTrue(!ShapeshiftService.morph(shifter, ShapeshiftService.formOf(shifter)), "Nobody wears their own face as a form");

            // The library keeps the face and skin through a save.
            List<ShapeshiftForm> kept = ShapeshiftService.forms(shifter);
            ShapeshiftForm saved = kept.stream().filter(ShapeshiftForm::person).findFirst().orElseThrow();
            h.assertTrue(saved.personId().equals(other.getUUID()) && saved.name().equals(other.getGameProfile().getName()), "Remembered by id and name");
            h.assertTrue(!saved.personOrNull().getProperties().get("textures").isEmpty(), "and with their skin");
            h.assertTrue(ShapeshiftForm.load(saved.save()).key().equals(saved.key()), "A saved face loads back the same");
        } finally { ShapeshiftService.forget(shifter); ShapeshiftService.forget(other); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.people")
    public static void nobodyWearsAnOperatorsFace(GameTestHelper h) {
        ServerPlayer shifter = person(h, 6), admin = person(h, 1);
        var ops = h.getLevel().getServer().getPlayerList();
        try {
            ShapeshiftForm face = ShapeshiftService.formOf(admin);
            h.assertTrue(ShapeshiftService.morph(shifter, face), "Before they are an operator, the face can be worn");
            h.assertTrue(ShapeshiftChat.speaksAs(shifter) != null, "and spoken through");
            op(ops, admin);
            h.assertTrue(ShapeshiftChat.speaksAs(shifter) == null, "Once they are an operator, chat goes out under the real name");
            ShapeshiftService.revert(shifter);
            h.assertTrue("operator".equals(ShapeshiftService.refusal(shifter, admin)), "An operator can't be studied");
            h.assertTrue(!ShapeshiftService.morph(shifter, face), "and a remembered operator's face can't be worn");
        } finally {
            ops.getOps().remove(admin.getGameProfile());
            ShapeshiftService.forget(shifter); ShapeshiftService.forget(admin);
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.people")
    public static void eachViewerGetsTheLineTheyAreOwed(GameTestHelper h) {
        ServerPlayer shifter = person(h, 6), copied = person(h, 1), bystander = person(h, 1), admin = person(h, 1);
        var server = h.getLevel().getServer();
        var config = com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.shapeshiftChatImpersonation;
        boolean was = config.get();
        try {
            op(server.getPlayerList(), admin);
            h.assertTrue(ShapeshiftChat.speaksAs(shifter) == null, "In your own body you speak as yourself");
            h.assertTrue(ShapeshiftService.morph(shifter, ShapeshiftService.formOf(copied)), "Taking a face");
            GameProfile as = ShapeshiftChat.speaksAs(shifter);
            h.assertTrue(as != null && as.getId().equals(copied.getUUID()), "Public chat now goes out as them");

            Component said = Component.literal("meet me at the tower");
            Component plain = ShapeshiftChat.line(bystander, shifter, as, said);
            h.assertTrue(plain.getString().contains(as.getName()) && plain.toString().contains("meet me at the tower"), "A bystander sees their name");
            h.assertTrue(!plain.getString().contains("*") && !plain.toString().contains("not_you"), "and nothing that gives it away");
            h.assertTrue(ShapeshiftChat.line(copied, shifter, as, said).toString().contains("chat.not_you"), "The copied person's copy says not you");
            Component known = ShapeshiftChat.line(admin, shifter, as, said);
            h.assertTrue(known.getString().contains("*") && known.toString().contains("chat.spoken_by"), "An operator can see who spoke");
            h.assertTrue(ShapeshiftChat.line(shifter, shifter, as, said).toString().contains("chat.you"), "The speaker is reminded whose face they wear");

            config.set(false);
            h.assertTrue(ShapeshiftChat.speaksAs(shifter) == null, "With the server switch off, chat uses the real name");
            h.assertTrue(ShapeshiftService.morphed(shifter), "but the form stays");
        } finally {
            config.set(was);
            server.getPlayerList().getOps().remove(admin.getGameProfile());
            for (ServerPlayer p : List.of(shifter, copied, bystander, admin)) ShapeshiftService.forget(p);
        }
        h.succeed();
    }

    // ---- Phase 4: traits, hands and Instinct ----

    @GameTest(template = "rewind_empty", batch = "shapeshift.traits")
    public static void eachFormBringsItsGiftsAndWeaknesses(GameTestHelper h) {
        var level = h.getLevel();
        record Case(EntityType<?> type, Set<ShapeshiftTraits.Trait> has, Set<ShapeshiftTraits.Trait> lacks) {}
        var T = ShapeshiftTraits.Trait.class;
        for (Case c : List.of(
                new Case(EntityType.SPIDER, EnumSet.of(ShapeshiftTraits.Trait.CLIMB, ShapeshiftTraits.Trait.NIGHT_EYES), EnumSet.noneOf(T)),
                new Case(EntityType.ZOMBIE, EnumSet.of(ShapeshiftTraits.Trait.UNDEAD, ShapeshiftTraits.Trait.SUN_BURN), EnumSet.noneOf(T)),
                new Case(EntityType.HUSK, EnumSet.of(ShapeshiftTraits.Trait.UNDEAD), EnumSet.of(ShapeshiftTraits.Trait.SUN_BURN)),
                new Case(EntityType.BLAZE, EnumSet.of(ShapeshiftTraits.Trait.FIRE_PROOF, ShapeshiftTraits.Trait.WATER_HURTS, ShapeshiftTraits.Trait.SNOWBALL_HURTS), EnumSet.noneOf(T)),
                new Case(EntityType.ENDERMAN, EnumSet.of(ShapeshiftTraits.Trait.PROJECTILE_PROOF, ShapeshiftTraits.Trait.WATER_HURTS), EnumSet.noneOf(T)),
                new Case(EntityType.COD, EnumSet.of(ShapeshiftTraits.Trait.GILLS, ShapeshiftTraits.Trait.DRIES_OUT, ShapeshiftTraits.Trait.SWIM), EnumSet.noneOf(T)),
                new Case(EntityType.CHICKEN, EnumSet.of(ShapeshiftTraits.Trait.SLOW_FALL), EnumSet.of(ShapeshiftTraits.Trait.FLUTTER)),
                new Case(EntityType.BAT, EnumSet.of(ShapeshiftTraits.Trait.SLOW_FALL, ShapeshiftTraits.Trait.FLUTTER), EnumSet.noneOf(T)),
                new Case(EntityType.CAT, EnumSet.of(ShapeshiftTraits.Trait.CREEPER_REPELLENT), EnumSet.noneOf(T)),
                new Case(EntityType.WOLF, EnumSet.of(ShapeshiftTraits.Trait.SCENT), EnumSet.noneOf(T)),
                new Case(EntityType.WARDEN, EnumSet.of(ShapeshiftTraits.Trait.VIBRATIONS, ShapeshiftTraits.Trait.BLIND), EnumSet.noneOf(T)),
                new Case(EntityType.COW, EnumSet.noneOf(T), EnumSet.allOf(T)))) {
            var traits = ShapeshiftTraits.of(ShapeshiftForms.example(level, c.type));
            h.assertTrue(traits.containsAll(c.has), c.type + " should have " + c.has + ", has " + traits);
            for (var lack : c.lacks) h.assertTrue(!traits.contains(lack), c.type + " shouldn't have " + lack);
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.traits")
    public static void traitsComeAndGoWithTheForm(GameTestHelper h) {
        ServerPlayer p = person(h, 10);
        try {
            double gravity = p.getAttributeValue(Attributes.GRAVITY);
            h.assertTrue(ShapeshiftService.morph(p, EntityType.CHICKEN), "Taking a chicken's shape");
            h.assertTrue(p.getAttributeValue(Attributes.GRAVITY) < gravity * .5 && p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) == 0,
                    "A chicken falls gently and takes no fall damage");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.BAT) && p.getAbilities().mayfly, "At Level 10 a bat flutters");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.BLAZE) && !p.getAbilities().mayfly, "Changing on takes the wings away");
            var fire = new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().onFire(), 4));
            ShapeshiftTraitService.damage(fire);
            h.assertTrue(fire.isCanceled(), "Fire can't touch a blaze");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE) && p.isInvertedHealAndHarm(), "A zombie is healed by harm");
            h.assertTrue(!ShapeshiftHands.handless(p), "and keeps its hands");
            h.assertTrue(ShapeshiftService.morph(p, EntityType.COW) && ShapeshiftHands.handless(p), "A cow has none");
            ShapeshiftService.revert(p);
            var again = new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().onFire(), 4));
            ShapeshiftTraitService.damage(again);
            h.assertTrue(!again.isCanceled() && !p.isInvertedHealAndHarm() && !ShapeshiftHands.handless(p), "Your own body has none of it");
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.GRAVITY) - gravity) < 1.0E-6, "and falls as it always did");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.traits")
    public static void aFishOutOfWaterRunsOutOfAir(GameTestHelper h) {
        ServerPlayer p = person(h, 3);
        try {
            p.setAirSupply(p.getMaxAirSupply());
            h.assertTrue(ShapeshiftService.morph(p, EntityType.COD), "Taking a cod's shape on dry land");
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(20, () -> {
            try {
                h.assertTrue(p.getAirSupply() < p.getMaxAirSupply() - 60, "A fish out of water loses its breath (" + p.getAirSupply() + ")");
            } finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    private static <T extends Mob> T dummy(GameTestHelper h, EntityType<T> type) {
        T mob = mob(h, type, 3, 6);
        mob.setYRot(180);
        return mob;
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.instinct")
    public static void instinctIsTheFormsOwnAttack(GameTestHelper h) {
        ServerPlayer p = person(h, 10);
        var level = h.getLevel();
        try {
            h.assertTrue(ShapeshiftInstinct.move(p) == InstinctMove.LUNGE, "In your own body Instinct is Lunge");
            for (var c : List.of(
                    java.util.Map.entry(EntityType.ZOMBIE, InstinctMove.GRAB), java.util.Map.entry(EntityType.SKELETON, InstinctMove.BONE),
                    java.util.Map.entry(EntityType.SPIDER, InstinctMove.WEB), java.util.Map.entry(EntityType.CREEPER, InstinctMove.DETONATE),
                    java.util.Map.entry(EntityType.ENDERMAN, InstinctMove.BLINK), java.util.Map.entry(EntityType.WOLF, InstinctMove.POUNCE),
                    java.util.Map.entry(EntityType.IRON_GOLEM, InstinctMove.FLING), java.util.Map.entry(EntityType.WARDEN, InstinctMove.SONIC),
                    java.util.Map.entry(EntityType.COW, InstinctMove.STRIKE))) {
                InstinctMove got = InstinctMove.of(ShapeshiftForm.of(c.getKey()), ShapeshiftForms.example(level, c.getKey()));
                h.assertTrue(got == c.getValue(), c.getKey() + " should " + c.getValue() + ", not " + got);
            }
            h.assertTrue(InstinctMove.of(ShapeshiftService.formOf(person(h, 1)), null) == InstinctMove.FAMILIAR, "A person's face calls their pets");

            var target = dummy(h, EntityType.PILLAGER);
            float before = target.getHealth();
            h.assertTrue(ShapeshiftInstinct.use(p), "Lunging at the pillager in front");
            h.assertTrue(target.getHealth() <= before - 6, "Lunge strikes for 6 + tier (" + (before - target.getHealth()) + ")");
            h.assertTrue(!ShapeshiftInstinct.use(p), "and then rests a moment");
        } finally { ShapeshiftService.forget(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.instinct")
    public static void aZombiesGrabHoldsAndACreepersBlastEndsTheForm(GameTestHelper h) {
        ServerPlayer p = person(h, 10);
        var held = dummy(h, EntityType.PILLAGER);
        Vec3 at = held.position();
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE) && ShapeshiftInstinct.use(p), "A zombie grabs");
            h.assertTrue(ShapeshiftInstinct.held(held), "the pillager in reach");
            held.teleportTo(at.x + 2, at.y, at.z);
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(held.position().distanceTo(at) < .1, "The grab holds it where it was");
                float before = held.getHealth();
                h.assertTrue(ShapeshiftService.morph(p, EntityType.CREEPER) && ShapeshiftInstinct.use(p), "A creeper detonates");
                h.assertTrue(held.getHealth() < before, "hurting what is near");
                h.assertTrue(!ShapeshiftService.morphed(p), "and is thrown back into its own body");
            } finally { ShapeshiftService.forget(p); }
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "shapeshift.dampened")
    public static void aDampenerForcesYouBack(GameTestHelper h) {
        ServerPlayer p = shifter(h, 2);
        try {
            h.assertTrue(ShapeshiftService.morph(p, EntityType.ZOMBIE), "Taking a zombie's shape");
            p.addEffect(new MobEffectInstance(ModItems.DAMPENED, 100));
        } catch (RuntimeException e) { ShapeshiftService.forget(p); throw e; }
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(!ShapeshiftService.morphed(p), "A dampener round drops the form");
                close(p.getMaxHealth(), 20, "and gives your own body back", h);
            } finally { ShapeshiftService.forget(p); p.removeEffect(ModItems.DAMPENED); }
            h.succeed();
        });
    }
}
