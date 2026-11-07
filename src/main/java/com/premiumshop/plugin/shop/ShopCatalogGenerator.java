package com.premiumshop.plugin.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Generates a complete, structured catalog of all survival-obtainable Minecraft
 * items into 10 balanced survival categories with tiered pricing and background stock.
 */
public final class ShopCatalogGenerator {

    private ShopCatalogGenerator() {}

    private static Boolean paperRegistryAvailable = null;

    private static void checkRegistryAvailability() {
        if (paperRegistryAvailable == null) {
            try {
                Material.STONE.isItem();
                paperRegistryAvailable = true;
            } catch (Throwable t) {
                paperRegistryAvailable = false;
            }
        }
    }

    /**
     * Safely checks whether a Material is an item, guarding against uninitialised
     * Paper registries in mock / testing environments.
     */
    public static boolean isItemSafe(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        if (name.startsWith("LEGACY_")) return false;
        if (name.equals("AIR") || name.endsWith("_AIR")) return false;

        // Filter technical non-item blocks
        if (name.contains("WALL_") || name.endsWith("_STEM") || name.endsWith("_PLANT")
                || (name.contains("CAULDRON") && !name.equals("CAULDRON"))
                || name.equals("WATER") || name.equals("LAVA") || name.equals("FIRE") || name.equals("SOUL_FIRE")
                || name.equals("NETHER_PORTAL") || name.equals("END_PORTAL") || name.equals("END_GATEWAY")
                || name.equals("PISTON_HEAD") || name.equals("MOVING_PISTON") || name.equals("REDSTONE_WIRE")
                || name.equals("TRIPWIRE") || name.equals("FROSTED_ICE") || name.contains("CANDLE_CAKE")) {
            return false;
        }

        checkRegistryAvailability();

        if (Boolean.TRUE.equals(paperRegistryAvailable)) {
            try {
                return mat.isItem();
            } catch (Throwable ignored) {
                return true;
            }
        }
        return true;
    }

    /**
     * Checks whether a Material is realistically obtainable in Survival mode.
     * Excludes technical blocks, operator/creative items, unbreakables, and non-items.
     */
    public static boolean isSurvivalObtainable(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        if (name.startsWith("LEGACY_") || name.equals("AIR") || name.endsWith("_AIR") || !isItemSafe(mat)) {
            return false;
        }

        // 1. Creative / Operator / Technical / Unobtainable blocks
        if (name.equals("BARRIER") || name.equals("LIGHT") || name.equals("DEBUG_STICK")
                || name.equals("STRUCTURE_BLOCK") || name.equals("STRUCTURE_VOID")
                || name.equals("JIGSAW") || name.equals("COMMAND_BLOCK")
                || name.equals("CHAIN_COMMAND_BLOCK") || name.equals("REPEATING_COMMAND_BLOCK")
                || name.equals("COMMAND_BLOCK_MINECART") || name.equals("KNOWLEDGE_BOOK")
                || name.equals("TRIAL_SPAWNER") || name.equals("VAULT")
                || name.startsWith("TEST_") || name.equals("PETRIFIED_OAK_SLAB")) {
            return false;
        }

        // 2. Unbreakable / World generation only
        if (name.equals("BEDROCK") || name.equals("END_PORTAL_FRAME")
                || name.equals("REINFORCED_DEEPSLATE") || name.equals("SPAWNER")
                || name.equals("BUDDING_AMETHYST") || name.equals("SUSPICIOUS_SAND")
                || name.equals("SUSPICIOUS_GRAVEL") || name.equals("DIRT_PATH")
                || name.equals("FARMLAND") || name.startsWith("INFESTED_")) {
            return false;
        }

        // 3. Spawn eggs
        if (name.endsWith("_SPAWN_EGG")) {
            return false;
        }

        // 4. Custom player head (untyped)
        if (name.equals("PLAYER_HEAD") || name.equals("PLAYER_WALL_HEAD")) {
            return false;
        }

        // 5. Potions / Tipped arrows with unassigned data or base air
        if (name.equals("TIPPED_ARROW")) {
            return false; // Plain ARROW and SPECTRAL_ARROW are supported
        }

        return true;
    }

    private static boolean isBundleCraftable() {
        return true; // 1.21.2+ bundle is craftable with leather and string
    }

    /**
     * Assigns a Material to one of the 10 curated categories.
     */
    public static String assignCategory(Material mat) {
        String name = mat.name();

        // 1. Rare & Valuables
        if (name.equals("NETHER_STAR") || name.equals("BEACON") || name.equals("ELYTRA")
                || name.equals("TOTEM_OF_UNDYING") || name.equals("HEAVY_CORE")
                || name.equals("DRAGON_EGG") || name.equals("DRAGON_HEAD")
                || name.equals("CONDUIT") || name.equals("HEART_OF_THE_SEA")
                || name.equals("NAUTILUS_SHELL") || name.equals("SPONGE") || name.equals("WET_SPONGE")
                || name.equals("ECHO_SHARD") || name.equals("RECOVERY_COMPASS")
                || name.equals("SNIFFER_EGG") || name.equals("ENCHANTED_GOLDEN_APPLE")
                || name.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE") || name.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")
                || name.startsWith("MUSIC_DISC_") || name.equals("DISC_FRAGMENT_5")
                || name.equals("TRIAL_KEY") || name.equals("OMINOUS_TRIAL_KEY") || name.equals("OMINOUS_BOTTLE")) {
            return "rare_valuables";
        }

        // 2. Minerals & Ores
        if (name.endsWith("_ORE") || name.endsWith("_INGOT") || name.endsWith("_NUGGET")
                || name.startsWith("RAW_") || name.equals("DIAMOND") || name.equals("EMERALD")
                || name.equals("COAL") || name.equals("CHARCOAL") || name.equals("ANCIENT_DEBRIS")
                || name.equals("NETHERITE_SCRAP") || name.equals("AMETHYST_SHARD")
                || name.equals("QUARTZ") || name.equals("LAPIS_LAZULI") || name.equals("FLINT")) {
            return "minerals_ores";
        }

        // 3. Farming & Food
        if (name.startsWith("COOKED_") || name.startsWith("RAW_") && isMeat(name)
                || name.equals("BREAD") || name.equals("APPLE") || name.equals("GOLDEN_APPLE")
                || name.equals("GOLDEN_CARROT") || name.equals("CARROT") || name.equals("POTATO")
                || name.equals("BAKED_POTATO") || name.equals("POISONOUS_POTATO")
                || name.equals("BEETROOT") || name.equals("BEETROOT_SOUP") || name.equals("MUSHROOM_STEW")
                || name.equals("RABBIT_STEW") || name.equals("SUSPICIOUS_STEW")
                || name.equals("MELON_SLICE") || name.equals("SWEET_BERRIES") || name.equals("GLOW_BERRIES")
                || name.equals("CHORUS_FRUIT") || name.equals("HONEY_BOTTLE") || name.equals("HONEYCOMB")
                || name.equals("WHEAT") || name.endsWith("_SEEDS") || name.equals("SUGAR_CANE")
                || name.equals("COCOA_BEANS") || name.equals("EGG") || name.equals("MILK_BUCKET")
                || name.equals("COOKIE") || name.equals("CAKE") || name.equals("PUMPKIN_PIE")
                || name.equals("DRIED_KELP") || name.equals("HAY_BLOCK") || name.equals("HONEY_BLOCK")
                || name.equals("HONEYCOMB_BLOCK") || name.equals("PUMPKIN") || name.equals("MELON")
                || name.equals("CARVED_PUMPKIN") || name.equals("JACK_O_LANTERN")) {
            return "farming_food";
        }

        // 4. Mob Drops
        if (name.equals("ROTTEN_FLESH") || name.equals("BONE") || name.equals("BONE_MEAL")
                || name.equals("GUNPOWDER") || name.equals("STRING") || name.equals("SPIDER_EYE")
                || name.equals("ENDER_PEARL") || name.equals("EYE_OF_ENDER") || name.equals("BLAZE_ROD")
                || name.equals("BLAZE_POWDER") || name.equals("MAGMA_CREAM") || name.equals("SLIME_BALL")
                || name.equals("SLIME_BLOCK") || name.equals("GHAST_TEAR") || name.equals("LEATHER")
                || name.equals("FEATHER") || name.equals("RABBIT_FOOT") || name.equals("RABBIT_HIDE")
                || name.equals("SHULKER_SHELL") || name.equals("PHANTOM_MEMBRANE") || name.equals("INK_SAC")
                || name.equals("GLOW_INK_SAC") || name.equals("PRISMARINE_SHARD") || name.equals("PRISMARINE_CRYSTALS")
                || name.equals("SCUTE") || name.equals("TURTLE_SCUTE") || name.equals("ARMADILLO_SCUTE")
                || name.equals("BREEZE_ROD") || name.equals("WIND_CHARGE")
                || name.endsWith("_HEAD") || name.endsWith("_SKULL")) {
            return "mob_drops";
        }

        // 5. Tools & Combat
        if (name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("SHIELD") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("MACE") || name.equals("ARROW")
                || name.equals("SPECTRAL_ARROW") || name.equals("FISHING_ROD") || name.equals("SHEARS")
                || name.equals("FLINT_AND_STEEL") || name.equals("SPYGLASS") || name.equals("BRUSH")
                || name.endsWith("_HORSE_ARMOR") || name.equals("WOLF_ARMOR") || name.equals("LEAD")
                || name.equals("SADDLE") || name.equals("NAME_TAG") || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK")) {
            return "tools_combat";
        }

        // 6. Redstone & Tech
        if (name.equals("REDSTONE") || name.equals("REDSTONE_TORCH") || name.equals("REDSTONE_BLOCK")
                || name.equals("REPEATER") || name.equals("COMPARATOR") || name.equals("OBSERVER")
                || name.equals("PISTON") || name.equals("STICKY_PISTON") || name.equals("HOPPER")
                || name.equals("DISPENSER") || name.equals("DROPPER") || name.equals("CRAFTER")
                || name.equals("DAYLIGHT_DETECTOR") || name.equals("TARGET") || name.equals("LIGHTNING_ROD")
                || name.equals("NOTE_BLOCK") || name.equals("JUKEBOX") || name.equals("TNT")
                || name.equals("TRIPWIRE_HOOK") || name.equals("LEVER") || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_COPPER_BULB") || name.equals("CHEST") || name.equals("TRAPPED_CHEST")
                || name.equals("BARREL") || name.endsWith("_SHULKER_BOX") || name.equals("SHULKER_BOX")
                || name.endsWith("_MINECART") || name.equals("MINECART") || name.endsWith("_RAIL")) {
            return "redstone_tech";
        }

        // 7. Brewing & Alchemy
        if (name.equals("BREWING_STAND") || name.equals("CAULDRON") || name.equals("GLASS_BOTTLE")
                || name.equals("POTION") || name.equals("SPLASH_POTION") || name.equals("LINGERING_POTION")
                || name.equals("NETHER_WART") || name.equals("FERMENTED_SPIDER_EYE") || name.equals("DRAGON_BREATH")
                || name.equals("GLISTERING_MELON_SLICE")) {
            return "brewing_alchemy";
        }

        // 8. Dyes & Decoration
        if (name.endsWith("_DYE") || name.endsWith("_WOOL") || name.endsWith("_CARPET")
                || name.endsWith("_CONCRETE") || name.endsWith("_CONCRETE_POWDER")
                || name.endsWith("_TERRACOTTA") || name.endsWith("_BED") || name.endsWith("_BANNER")
                || name.endsWith("_STAINED_GLASS") || name.endsWith("_STAINED_GLASS_PANE")
                || name.equals("GLASS") || name.equals("GLASS_PANE") || name.equals("TINTED_GLASS")
                || name.equals("PAINTING") || name.equals("ITEM_FRAME") || name.equals("GLOW_ITEM_FRAME")
                || name.equals("ARMOR_STAND") || name.equals("FLOWER_POT") || name.equals("TORCH")
                || name.equals("SOUL_TORCH") || name.equals("LANTERN") || name.equals("SOUL_LANTERN")
                || name.endsWith("_CANDLE") || name.equals("CANDLE") || name.equals("SEA_LANTERN")
                || name.equals("GLOWSTONE") || name.equals("SHROOMLIGHT") || name.equals("FROG_SPAWN")
                || name.endsWith("_FROGLIGHT") || name.equals("END_ROD") || name.equals("BELL")
                || name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE") || name.endsWith("_BANNER_PATTERN")) {
            return "dyes_decor";
        }

        // 9. Wood & Flora
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
                || name.startsWith("STRIPPED_") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING")
                || name.equals("CHERRY_SAPLING") || name.equals("MANGROVE_PROPAGULE") || name.equals("BAMBOO")
                || name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT") || name.equals("BAMBOO_RAFT")
                || name.equals("BAMBOO_CHEST_RAFT") || name.equals("VINE") || name.equals("WEEPING_VINES")
                || name.equals("TWISTING_VINES") || name.equals("GLOW_LICHEN") || name.equals("LILY_PAD")
                || name.equals("SEAGRASS") || name.equals("KELP") || name.equals("SHORT_GRASS") || name.equals("TALL_GRASS")
                || name.equals("FERN") || name.equals("LARGE_FERN") || name.equals("DANDELION") || name.equals("POPPY")
                || name.equals("BLUE_ORCHID") || name.equals("ALLIUM") || name.equals("AZURE_BLUET")
                || name.equals("RED_TULIP") || name.equals("ORANGE_TULIP") || name.equals("WHITE_TULIP")
                || name.equals("PINK_TULIP") || name.equals("OXEYE_DAISY") || name.equals("CORNFLOWER")
                || name.equals("LILY_OF_THE_VALLEY") || name.equals("WITHER_ROSE") || name.equals("SUNFLOWER")
                || name.equals("LILAC") || name.equals("ROSE_BUSH") || name.equals("PEONY") || name.equals("TORCHFLOWER")
                || name.equals("PITCHER_PLANT") || name.equals("PITCHER_POD") || name.equals("SPORE_BLOSSOM")
                || name.equals("AZALEA") || name.equals("FLOWERING_AZALEA") || name.equals("MOSS_BLOCK")
                || name.equals("MOSS_CARPET") || name.equals("BIG_DRIPLEAF") || name.equals("SMALL_DRIPLEAF")
                || name.equals("BROWN_MUSHROOM") || name.equals("RED_MUSHROOM") || name.equals("CRIMSON_FUNGUS")
                || name.equals("WARPED_FUNGUS") || name.equals("CRIMSON_ROOTS") || name.equals("WARPED_ROOTS")
                || name.equals("NETHER_SPROUTS") || name.equals("CACTUS") || name.equals("HANGING_ROOTS")) {
            return "wood_flora";
        }

        // 10. Default: Building Blocks
        return "building_blocks";
    }

    private static boolean isMeat(String name) {
        return name.contains("BEEF") || name.contains("PORKCHOP") || name.contains("CHICKEN")
                || name.contains("MUTTON") || name.contains("RABBIT") || name.contains("COD") || name.contains("SALMON");
    }

    /**
     * Calculates realistic, tiered buy and sell prices based on material and category,
     * carefully calibrated to actual Minecraft Survival item rarity.
     * Returns double[] { baseBuyPrice, baseSellPrice }.
     */
    public static double[] calculateTieredPrices(Material mat, String category) {
        String name = mat.name();

        // 1. Ultra-Rare Boss, Structure & Trophy Items
        if (name.equals("DRAGON_EGG")) return new double[]{10000.0, 3500.0};
        if (name.equals("DRAGON_HEAD")) return new double[]{8000.0, 3000.0};
        if (name.equals("MACE")) return new double[]{6500.0, 2400.0};
        if (name.equals("HEAVY_CORE")) return new double[]{6000.0, 2200.0};
        if (name.equals("ELYTRA")) return new double[]{5000.0, 1800.0};
        if (name.equals("ENCHANTED_GOLDEN_APPLE")) return new double[]{4500.0, 1600.0};
        if (name.equals("BEACON")) return new double[]{4500.0, 1500.0};
        if (name.equals("NETHER_STAR")) return new double[]{3500.0, 1200.0};
        if (name.equals("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE")) return new double[]{4000.0, 1500.0};
        if (name.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) return new double[]{1500.0, 500.0};
        if (name.endsWith("_SMITHING_TEMPLATE")) return new double[]{1000.0, 350.0};
        if (name.equals("TOTEM_OF_UNDYING")) return new double[]{2000.0, 700.0};
        if (name.equals("CONDUIT") || name.equals("HEART_OF_THE_SEA")) return new double[]{2000.0, 700.0};
        if (name.equals("TRIDENT")) return new double[]{1500.0, 500.0};
        if (name.equals("SNIFFER_EGG")) return new double[]{1200.0, 400.0};
        if (name.equals("RECOVERY_COMPASS") || name.equals("ECHO_SHARD")) return new double[]{800.0, 280.0};
        if (name.equals("OMINOUS_TRIAL_KEY")) return new double[]{800.0, 280.0};
        if (name.equals("OMINOUS_BOTTLE")) return new double[]{600.0, 200.0};
        if (name.equals("TRIAL_KEY")) return new double[]{400.0, 140.0};
        if (name.endsWith("_SHULKER_BOX") || name.equals("SHULKER_BOX")) return new double[]{750.0, 260.0};
        if (name.equals("SHULKER_SHELL")) return new double[]{350.0, 120.0};
        if (name.equals("WITHER_SKELETON_SKULL")) return new double[]{700.0, 250.0};
        if (name.endsWith("_HEAD") || name.endsWith("_SKULL")) return new double[]{500.0, 175.0};

        // Music Discs
        if (name.equals("MUSIC_DISC_5") || name.equals("MUSIC_DISC_PIGSTEP") || name.equals("MUSIC_DISC_OTHERSIDE")
                || name.equals("MUSIC_DISC_RELIC") || name.equals("MUSIC_DISC_CREATOR")) {
            return new double[]{1500.0, 500.0};
        }
        if (name.startsWith("MUSIC_DISC_") || name.equals("DISC_FRAGMENT_5")) {
            return new double[]{500.0, 160.0};
        }

        // 2. Netherite Tier
        if (name.equals("NETHERITE_BLOCK")) return new double[]{28800.0, 9900.0};
        if (name.equals("NETHERITE_CHESTPLATE")) return new double[]{4800.0, 1600.0};
        if (name.equals("NETHERITE_LEGGINGS")) return new double[]{4500.0, 1500.0};
        if (name.equals("NETHERITE_HELMET") || name.equals("NETHERITE_BOOTS")) return new double[]{3800.0, 1300.0};
        if (name.equals("NETHERITE_SWORD") || name.equals("NETHERITE_PICKAXE") || name.equals("NETHERITE_AXE")) return new double[]{3800.0, 1300.0};
        if (name.equals("NETHERITE_SHOVEL") || name.equals("NETHERITE_HOE")) return new double[]{3200.0, 1100.0};
        if (name.equals("NETHERITE_INGOT")) return new double[]{3200.0, 1100.0};
        if (name.equals("NETHERITE_SCRAP")) return new double[]{750.0, 250.0};
        if (name.equals("ANCIENT_DEBRIS")) return new double[]{650.0, 220.0};

        // 3. Diamond Tier
        if (name.equals("DIAMOND_BLOCK")) return new double[]{2250.0, 800.0};
        if (name.equals("DIAMOND_CHESTPLATE")) return new double[]{2000.0, 650.0};
        if (name.equals("DIAMOND_LEGGINGS")) return new double[]{1750.0, 580.0};
        if (name.equals("DIAMOND_HELMET") || name.equals("DIAMOND_BOOTS")) return new double[]{1000.0, 350.0};
        if (name.equals("DIAMOND_PICKAXE") || name.equals("DIAMOND_AXE")) return new double[]{750.0, 250.0};
        if (name.equals("DIAMOND_SWORD") || name.equals("DIAMOND_HOE") || name.equals("DIAMOND_SHOVEL")) return new double[]{350.0, 120.0};
        if (name.equals("DIAMOND_HORSE_ARMOR")) return new double[]{1500.0, 500.0};
        if (name.equals("DIAMOND_ORE") || name.equals("DEEPSLATE_DIAMOND_ORE")) return new double[]{350.0, 120.0};
        if (name.equals("DIAMOND")) return new double[]{250.0, 90.0};

        // 4. Emerald & Gold
        if (name.equals("EMERALD_BLOCK")) return new double[]{720.0, 250.0};
        if (name.equals("EMERALD_ORE") || name.equals("DEEPSLATE_EMERALD_ORE")) return new double[]{150.0, 50.0};
        if (name.equals("EMERALD")) return new double[]{80.0, 28.0};
        if (name.equals("GOLD_BLOCK") || name.equals("RAW_GOLD_BLOCK")) return new double[]{360.0, 125.0};
        if (name.equals("GOLDEN_CHESTPLATE")) return new double[]{250.0, 80.0};
        if (name.startsWith("GOLDEN_") && (name.endsWith("_HELMET") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SWORD") || name.endsWith("_SHOVEL") || name.endsWith("_HOE"))) {
            return new double[]{120.0, 40.0};
        }
        if (name.equals("GOLD_INGOT") || name.equals("RAW_GOLD")) return new double[]{40.0, 14.0};
        if (name.equals("GOLD_ORE") || name.equals("DEEPSLATE_GOLD_ORE") || name.equals("NETHER_GOLD_ORE")) return new double[]{50.0, 16.0};
        if (name.equals("GOLD_NUGGET")) return new double[]{4.5, 1.5};
        if (name.equals("GOLDEN_APPLE")) return new double[]{250.0, 80.0};
        if (name.equals("GOLDEN_CARROT")) return new double[]{35.0, 12.0};
        if (name.equals("GLISTERING_MELON_SLICE")) return new double[]{25.0, 8.0};

        // 5. Iron & Chainmail Tier
        if (name.equals("IRON_BLOCK") || name.equals("RAW_IRON_BLOCK")) return new double[]{180.0, 60.0};
        if (name.equals("IRON_CHESTPLATE")) return new double[]{160.0, 55.0};
        if (name.equals("IRON_LEGGINGS")) return new double[]{140.0, 48.0};
        if (name.equals("IRON_HELMET") || name.equals("IRON_BOOTS") || name.equals("IRON_PICKAXE") || name.equals("IRON_AXE")) return new double[]{60.0, 20.0};
        if (name.equals("IRON_SWORD") || name.equals("IRON_SHOVEL") || name.equals("IRON_HOE") || name.equals("SHEARS") || name.equals("SHIELD")) return new double[]{35.0, 12.0};
        if (name.startsWith("CHAINMAIL_")) return new double[]{150.0, 50.0};
        if (name.equals("IRON_INGOT") || name.equals("RAW_IRON")) return new double[]{20.0, 7.0};
        if (name.equals("IRON_ORE") || name.equals("DEEPSLATE_IRON_ORE")) return new double[]{25.0, 8.5};
        if (name.equals("IRON_NUGGET")) return new double[]{2.2, 0.7};

        // 6. Mid-Tier Minerals & Materials
        if (name.equals("LAPIS_BLOCK")) return new double[]{135.0, 45.0};
        if (name.equals("LAPIS_LAZULI") || name.contains("LAPIS")) return new double[]{15.0, 5.0};
        if (name.equals("AMETHYST_BLOCK") || name.equals("AMETHYST_CLUSTER")) return new double[]{60.0, 20.0};
        if (name.equals("AMETHYST_SHARD")) return new double[]{15.0, 5.0};
        if (name.equals("QUARTZ_BLOCK")) return new double[]{60.0, 20.0};
        if (name.equals("QUARTZ") || name.equals("NETHER_QUARTZ_ORE")) return new double[]{15.0, 5.0};
        if (name.equals("COPPER_BLOCK") || name.equals("RAW_COPPER_BLOCK")) return new double[]{90.0, 28.0};
        if (name.equals("COPPER_INGOT") || name.equals("RAW_COPPER") || name.contains("COPPER")) return new double[]{10.0, 3.2};
        if (name.equals("COAL_BLOCK")) return new double[]{72.0, 22.0};
        if (name.equals("COAL") || name.equals("CHARCOAL") || name.contains("COAL")) return new double[]{8.0, 2.5};
        if (name.equals("FLINT")) return new double[]{6.0, 2.0};

        // 7. Exotic, Deep Dark, Ocean & Nether Blocks
        if (name.equals("SPONGE") || name.equals("WET_SPONGE")) return new double[]{300.0, 100.0};
        if (name.equals("SCULK_CATALYST") || name.equals("SCULK_SHRIEKER")) return new double[]{250.0, 80.0};
        if (name.equals("SCULK_SENSOR") || name.equals("CALIBRATED_SCULK_SENSOR")) return new double[]{180.0, 60.0};
        if (name.equals("SCULK") || name.equals("SCULK_VEIN")) return new double[]{25.0, 8.0};
        if (name.equals("CRYING_OBSIDIAN")) return new double[]{45.0, 15.0};
        if (name.equals("OBSIDIAN")) return new double[]{25.0, 8.0};
        if (name.endsWith("_FROGLIGHT")) return new double[]{40.0, 14.0};
        if (name.equals("SEA_LANTERN")) return new double[]{35.0, 12.0};
        if (name.equals("PRISMARINE_BRICKS") || name.equals("DARK_PRISMARINE")) return new double[]{25.0, 8.0};
        if (name.equals("PRISMARINE") || name.equals("PRISMARINE_SHARD") || name.equals("PRISMARINE_CRYSTALS")) return new double[]{15.0, 5.0};
        if (name.equals("PURPUR_BLOCK") || name.equals("PURPUR_PILLAR")) return new double[]{12.0, 4.0};
        if (name.equals("END_STONE") || name.equals("END_STONE_BRICKS")) return new double[]{8.0, 2.5};
        if (name.equals("NETHER_BRICKS") || name.equals("RED_NETHER_BRICKS")) return new double[]{6.0, 2.0};
        if (name.equals("GLOWSTONE") || name.equals("SHROOMLIGHT")) return new double[]{20.0, 7.0};

        // 8. Mob Drops
        if (name.equals("NAUTILUS_SHELL")) return new double[]{250.0, 80.0};
        if (name.equals("TURTLE_SCUTE") || name.equals("ARMADILLO_SCUTE") || name.equals("SCUTE")) return new double[]{60.0, 20.0};
        if (name.equals("GHAST_TEAR")) return new double[]{90.0, 30.0};
        if (name.equals("BREEZE_ROD") || name.equals("WIND_CHARGE")) return new double[]{60.0, 20.0};
        if (name.equals("BLAZE_ROD") || name.equals("BLAZE_POWDER")) return new double[]{40.0, 14.0};
        if (name.equals("PHANTOM_MEMBRANE")) return new double[]{40.0, 14.0};
        if (name.equals("ENDER_PEARL") || name.equals("EYE_OF_ENDER")) return new double[]{35.0, 12.0};
        if (name.equals("SLIME_BALL") || name.equals("MAGMA_CREAM")) return new double[]{30.0, 10.0};
        if (name.equals("SLIME_BLOCK") || name.equals("HONEY_BLOCK")) return new double[]{270.0, 90.0};
        if (name.equals("GLOW_INK_SAC")) return new double[]{25.0, 8.0};
        if (name.equals("RABBIT_FOOT")) return new double[]{50.0, 18.0};
        if (name.equals("DRAGON_BREATH")) return new double[]{150.0, 50.0};
        if (name.equals("GUNPOWDER")) return new double[]{12.0, 4.0};
        if (name.equals("LEATHER")) return new double[]{10.0, 3.5};
        if (name.equals("INK_SAC")) return new double[]{10.0, 3.0};
        if (name.equals("STRING") || name.equals("SPIDER_EYE") || name.equals("BONE") || name.equals("BONE_MEAL")) return new double[]{8.0, 2.5};
        if (name.equals("ROTTEN_FLESH") || name.equals("FEATHER") || name.equals("RABBIT_HIDE")) return new double[]{5.0, 1.5};

        // 9. Tech & Redstone
        if (name.equals("CRAFTER") || name.equals("HOPPER")) return new double[]{90.0, 30.0};
        if (name.equals("PISTON") || name.equals("STICKY_PISTON")) return new double[]{45.0, 15.0};
        if (name.equals("TNT")) return new double[]{50.0, 16.0};
        if (name.equals("DISPENSER") || name.equals("DROPPER") || name.equals("OBSERVER")) return new double[]{35.0, 12.0};
        if (name.equals("DAYLIGHT_DETECTOR") || name.equals("TARGET") || name.equals("LIGHTNING_ROD")) return new double[]{30.0, 10.0};
        if (name.equals("REPEATER") || name.equals("COMPARATOR")) return new double[]{25.0, 8.0};
        if (name.equals("POWERED_RAIL") || name.equals("DETECTOR_RAIL") || name.equals("ACTIVATOR_RAIL")) return new double[]{30.0, 10.0};
        if (name.equals("RAIL")) return new double[]{8.0, 2.5};
        if (name.endsWith("_MINECART")) return new double[]{50.0, 16.0};
        if (name.equals("MINECART")) return new double[]{35.0, 12.0};
        if (name.equals("REDSTONE") || name.equals("REDSTONE_TORCH")) return new double[]{10.0, 3.2};
        if (name.equals("REDSTONE_BLOCK")) return new double[]{90.0, 28.0};
        if (name.equals("NOTE_BLOCK") || name.equals("JUKEBOX")) return new double[]{40.0, 14.0};
        if (name.equals("TRIPWIRE_HOOK") || name.equals("LEVER") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")) return new double[]{8.0, 2.5};

        // 10. Alchemy & Brewing
        if (name.equals("BREWING_STAND") || name.equals("CAULDRON")) return new double[]{75.0, 25.0};
        if (name.equals("FERMENTED_SPIDER_EYE")) return new double[]{25.0, 8.0};
        if (name.equals("NETHER_WART")) return new double[]{15.0, 5.0};
        if (name.equals("GLASS_BOTTLE")) return new double[]{4.0, 1.2};
        if (name.contains("POTION")) return new double[]{50.0, 16.0};

        // 11. Specialty Plants & Flora
        if (name.equals("WITHER_ROSE")) return new double[]{150.0, 50.0};
        if (name.equals("SPORE_BLOSSOM")) return new double[]{200.0, 70.0};
        if (name.equals("TORCHFLOWER") || name.equals("PITCHER_PLANT") || name.equals("PITCHER_POD")) return new double[]{50.0, 16.0};

        // 12. Food & Farming
        if (name.equals("PUMPKIN_PIE") || name.equals("CAKE")) return new double[]{25.0, 8.0};
        if (name.equals("HONEY_BOTTLE")) return new double[]{20.0, 6.0};
        if (name.equals("HONEYCOMB")) return new double[]{15.0, 5.0};
        if (name.startsWith("COOKED_")) return new double[]{8.0, 2.5};
        if (name.startsWith("RAW_") && isMeat(name)) return new double[]{4.0, 1.2};
        if (name.equals("BREAD")) return new double[]{5.0, 1.5};
        if (name.equals("COOKIE")) return new double[]{3.0, 0.8};
        if (name.equals("APPLE")) return new double[]{6.0, 2.0};
        if (name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO") || name.equals("BEETROOT")) return new double[]{3.0, 0.8};
        if (name.equals("SWEET_BERRIES") || name.equals("GLOW_BERRIES")) return new double[]{3.0, 0.8};
        if (name.equals("SUGAR_CANE") || name.equals("BAMBOO") || name.equals("CACTUS") || name.equals("KELP")) return new double[]{2.5, 0.7};
        if (name.endsWith("_SEEDS") || name.equals("COCOA_BEANS") || name.equals("EGG")) return new double[]{2.5, 0.7};
        if (name.equals("MILK_BUCKET")) return new double[]{25.0, 8.0};

        // 13. Wood & Trees
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) return new double[]{5.0, 1.5};
        if (name.endsWith("_SAPLING") || name.contains("PROPAGULE")) return new double[]{8.0, 2.5};
        if (name.endsWith("_PLANKS")) return new double[]{1.5, 0.4};
        if (name.endsWith("_LEAVES")) return new double[]{2.0, 0.5};
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT")) return new double[]{15.0, 5.0};
        if (name.contains("VINE") || name.contains("LICHEN") || name.equals("LILY_PAD")) return new double[]{4.0, 1.2};

        // 14. Dyes & Decor
        if (name.endsWith("_DYE")) return new double[]{6.0, 2.0};
        if (name.endsWith("_WOOL") || name.endsWith("_CARPET")) return new double[]{5.0, 1.5};
        if (name.endsWith("_BED")) return new double[]{20.0, 6.0};
        if (name.endsWith("_BANNER")) return new double[]{25.0, 8.0};
        if (name.contains("LANTERN")) return new double[]{20.0, 7.0};
        if (name.contains("CANDLE")) return new double[]{15.0, 5.0};
        if (name.equals("PAINTING") || name.equals("ITEM_FRAME") || name.equals("GLOW_ITEM_FRAME")) return new double[]{15.0, 5.0};
        if (name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")) return new double[]{20.0, 7.0};
        if (name.contains("GLASS")) return new double[]{4.0, 1.2};

        // 15. Tools & Combat (Wood, Stone, Leather)
        if (category.equals("tools_combat")) {
            if (name.contains("STONE_")) return new double[]{15.0, 4.0};
            if (name.contains("WOODEN_")) return new double[]{8.0, 2.0};
            if (name.contains("LEATHER_")) return new double[]{25.0, 8.0};
            if (name.equals("BOW") || name.equals("CROSSBOW")) return new double[]{45.0, 15.0};
            return new double[]{20.0, 6.0};
        }

        // 16. Building Blocks
        if (name.contains("BRICK") || name.contains("POLISHED") || name.contains("TILES")
                || name.contains("TERRACOTTA") || name.contains("CONCRETE") || name.contains("CUT_")) {
            return new double[]{4.0, 1.2};
        }
        if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("WALL")) {
            return new double[]{2.0, 0.6};
        }
        if (name.equals("STONE") || name.equals("DEEPSLATE") || name.equals("GRANITE") || name.equals("DIORITE")
                || name.equals("ANDESITE") || name.equals("TUFF") || name.equals("CALCITE") || name.equals("SANDSTONE")) {
            return new double[]{2.5, 0.7};
        }

        // 17. Default cheap base block (Dirt, Cobblestone, Sand, Gravel, Netherrack, Basalt, Cobbled Deepslate)
        return new double[]{1.0, 0.2};
    }

    /**
     * Calculates the background max stock for an item based on its rarity and category.
     */
    public static int calculateMaxStock(Material mat, String category) {
        String name = mat.name();

        if (category.equals("rare_valuables") || name.startsWith("NETHERITE_") || name.equals("MACE") || name.equals("ELYTRA")) {
            return 20;
        }
        if (name.contains("DIAMOND") || name.equals("TOTEM_OF_UNDYING") || name.equals("SHULKER_SHELL")) {
            return 100;
        }
        if (name.contains("GOLD") || name.contains("IRON") || category.equals("brewing_alchemy")) {
            return 500;
        }
        if (category.equals("farming_food") || category.equals("mob_drops") || category.equals("redstone_tech")) {
            return 1000;
        }
        // Building blocks, Wood & Flora, Dyes & Decor
        return 3000;
    }

    /**
     * Formats a Material enum name into a readable display name, e.g. DIAMOND_PICKAXE -> "Diamond Pickaxe".
     */
    public static String formatDisplayName(Material mat) {
        String[] parts = mat.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
              .append(parts[i].substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Category Metadata definition.
     */
    public record CategoryMeta(String id, String displayName, Material icon, int slot) {}

    public static final List<CategoryMeta> CATEGORIES = List.of(
            new CategoryMeta("building_blocks", "&a&lBuilding Blocks", Material.BRICKS, 11),
            new CategoryMeta("minerals_ores", "&b&lMinerals & Ores", Material.DIAMOND, 12),
            new CategoryMeta("farming_food", "&e&lFarming & Food", Material.GOLDEN_CARROT, 13),
            new CategoryMeta("mob_drops", "&c&lMob Drops", Material.ENDER_PEARL, 14),
            new CategoryMeta("tools_combat", "&6&lTools & Combat", Material.NETHERITE_SWORD, 15),
            new CategoryMeta("redstone_tech", "&4&lRedstone & Tech", Material.REDSTONE, 20),
            new CategoryMeta("brewing_alchemy", "&d&lBrewing & Alchemy", Material.BREWING_STAND, 21),
            new CategoryMeta("dyes_decor", "&5&lDyes & Decoration", Material.PAINTING, 22),
            new CategoryMeta("wood_flora", "&2&lWood & Flora", Material.OAK_SAPLING, 23),
            new CategoryMeta("rare_valuables", "&3&lRare & Valuables", Material.NETHER_STAR, 24)
    );

    /**
     * Generates a complete default Shop instance with all survival items.
     */
    public static Shop generateDefaultShop() {
        Shop shop = new Shop("main", "&6&lPremium Shop");

        Map<String, List<ShopItem>> categoryItems = new LinkedHashMap<>();
        for (CategoryMeta meta : CATEGORIES) {
            categoryItems.put(meta.id(), new ArrayList<>());
        }

        // Iterate all materials in the Bukkit registry
        for (Material mat : Material.values()) {
            if (!isSurvivalObtainable(mat)) continue;

            String catId = assignCategory(mat);
            List<ShopItem> itemList = categoryItems.get(catId);
            if (itemList == null) {
                itemList = categoryItems.get("building_blocks");
            }

            double[] prices = calculateTieredPrices(mat, catId);
            int maxStock = calculateMaxStock(mat, catId);
            String itemId = mat.name().toLowerCase(Locale.ROOT);
            String displayName = formatDisplayName(mat);

            ItemStack displayItem;
            try {
                displayItem = new ItemStack(mat);
            } catch (Throwable t) {
                continue;
            }

            ShopItem item = new ShopItem(
                    itemId,
                    displayItem,
                    prices[0],
                    prices[1],
                    maxStock,
                    maxStock, // initial stock matches maxStock
                    0L,
                    0,
                    true,
                    true
            );
            itemList.add(item);
        }

        // Construct ShopCategory objects
        for (CategoryMeta meta : CATEGORIES) {
            List<ShopItem> items = categoryItems.getOrDefault(meta.id(), Collections.emptyList());
            ItemStack icon = new ItemStack(meta.icon());
            ShopCategory category = new ShopCategory(
                    meta.id(),
                    meta.displayName(),
                    icon,
                    items,
                    Collections.emptyMap(),
                    new int[0],
                    null
            );
            category.setSlot(meta.slot());
            shop.addCategory(category);
        }

        return shop;
    }
}
