package dev.hypercodec.dupe_on_hit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MobDupe.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DUPE_BLACKLIST = BUILDER
            .comment("A list of entities to exclude from duping")
            .defineListAllowEmpty("dupe_blacklist", List.of(), Config::validateEntityType);
    private static final ForgeConfigSpec.ConfigValue<Boolean> DUPE_BLACKLIST_IS_WHITELIST = BUILDER
            .comment("Whether to treat dupe_blacklist as a whitelist")
            .define("dupe_blacklist_is_whitelist", false);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TARGET_BLACKLIST = BUILDER
            .comment("A list of entities that don't dupe others when they get hit.")
            .defineListAllowEmpty("target_blacklist", List.of(), Config::validateEntityType);
    private static final ForgeConfigSpec.ConfigValue<Boolean> TARGET_BLACKLIST_IS_WHITELIST = BUILDER
            .comment("Whether to treat target_blacklist as a whitelist")
            .define("target_blacklist_is_whitelist", false);
    private static final ForgeConfigSpec.ConfigValue<Boolean> DUPED_ENEMIES_DROP_LOOT = BUILDER
            .comment("Whether duped entities drop items and xp")
            .define("duped_entities_drop_loot", false);
    private static final ForgeConfigSpec.ConfigValue<Integer> DUPED_GRACE_PERIOD = BUILDER
            .comment("The number of ticks a duped entity can live before it is automatically killed. Set to a number <=0 to disable automatic killing entirely.")
            .define("grace_period", 20 * 60);
    private static final ForgeConfigSpec.ConfigValue<Boolean> GRACE_END_INSTAKILL = BUILDER
            .comment("Whether to instakill entities after the grace period has passed. If false, it will apply infinite wither II instead.")
            .define("grace_end_instakill", true);
    private static final ForgeConfigSpec.ConfigValue<Boolean> SAME_TYPE_DUPING = BUILDER
            .comment("Whether to dupe when hitting an entity of the same type. Disabling this prevents things like skeletons from duping after shooting duped skeletons, for example.")
            .define("same_type_duping", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static Set<EntityType<?>> entityBlacklist;
    public static boolean entityBlacklistIsWhitelist;
    public static Set<EntityType<?>> targetBlacklist;
    public static boolean targetBlacklistIsWhitelist;
    public static boolean dupedEntitiesDropLoot;
    public static int gracePeriod;
    public static boolean graceEndInstakill;
    public static boolean sameTypeDuping;

    private static boolean validateEntityType(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ENTITY_TYPES.containsKey(ResourceLocation.tryParse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        entityBlacklist = DUPE_BLACKLIST.get().stream().map(s -> ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(s))).collect(Collectors.toSet());
        entityBlacklistIsWhitelist = DUPE_BLACKLIST_IS_WHITELIST.get();
        targetBlacklist = TARGET_BLACKLIST.get().stream().map(s -> ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(s))).collect(Collectors.toSet());
        targetBlacklistIsWhitelist = TARGET_BLACKLIST_IS_WHITELIST.get();
        dupedEntitiesDropLoot = DUPED_ENEMIES_DROP_LOOT.get();
        gracePeriod = DUPED_GRACE_PERIOD.get();
        graceEndInstakill = GRACE_END_INSTAKILL.get();
        sameTypeDuping = SAME_TYPE_DUPING.get();
    }
}
