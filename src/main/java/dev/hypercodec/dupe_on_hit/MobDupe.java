package dev.hypercodec.dupe_on_hit;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MobDupe.MODID)
public class MobDupe {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "mobdupe";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SPAWNED_TIMESTAMP = "SpawnedTimestamp";

    public MobDupe() {
        // register serverside events
        MinecraftForge.EVENT_BUS.register(this);

        // register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onLivingTick(@NotNull LivingEvent.LivingTickEvent event) {
        if(Config.gracePeriod <= 0) return;

        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide || !entity.getTags().contains("duped")) return;

        CompoundTag persistentData = entity.getPersistentData();
        if(persistentData.contains(SPAWNED_TIMESTAMP)) {
            if(level.getGameTime() - persistentData.getLong(SPAWNED_TIMESTAMP) > Config.gracePeriod)
                endGracePeriod(entity);
            return;
        }
        LOGGER.warn("Duped entity {} missing spawn timestamp, assuming grace period end", entity.getDisplayName());
        endGracePeriod(entity);
    }

    private static void endGracePeriod(LivingEntity entity) {
        if(Config.graceEndInstakill) {
            entity.kill();
        } else {
            entity.forceAddEffect(
                    new MobEffectInstance(
                            MobEffects.WITHER,
                            1,
                            MobEffectInstance.INFINITE_DURATION
                    ),
                    null
            );
        }
    }

    @SubscribeEvent
    public void onDropLoot(@NotNull LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if(entity.level().isClientSide || (!Config.dupedEntitiesDropLoot && entity.getTags().contains("duped"))) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public void onHurt(@NotNull LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity entity = source.getEntity();
        if (!(entity instanceof LivingEntity living) ||
                entity.level().isClientSide ||
                !entity.isAlive() ||
                (!Config.sameTypeDuping && living.getType() == event.getEntity().getType()))
            return;

        dupeMob(living);
    }

    private @Nullable LivingEntity dupeMob(@NotNull LivingEntity source) {
        EntityType<?> type = source.getType();
        if(Config.entityBlacklist.contains(type) != Config.entityBlacklistIsWhitelist ||
                (Config.targetBlacklist.contains(type) != Config.targetBlacklistIsWhitelist))
            return null;

        Level level = source.level();
        LivingEntity copy = (LivingEntity) type.create(level);
        if (copy != null) {
            copy.copyPosition(source);

            CompoundTag tag = source.saveWithoutId(new CompoundTag());
            tag.put("ActiveEffects", new ListTag());
            tag.putBoolean("ignited", false);
            LOGGER.info("Saving entity data: {}", tag);
            copy.load(tag);

            CompoundTag persistentData = copy.getPersistentData();
            persistentData.putLong(SPAWNED_TIMESTAMP, level.getGameTime());

            // for some reason the method with "withoutId" in the name still copies over the id
            copy.setUUID(UUID.randomUUID());
            copy.addTag("duped");
            copy.resetFallDistance();
            copy.setNoActionTime(5);

            if(!Config.dupedEntitiesDropLoot)
                copy.skipDropExperience();

            for (MobEffectInstance instance : source.getActiveEffects()) {
                MobEffect effect = instance.getEffect();

                if (!effect.isInstantenous()) {
                    copy.addEffect(new MobEffectInstance(instance));
                }
            }

            if(copy instanceof Raider raider) {
                Raider sourceRaider = (Raider) source;
                Raid raid = sourceRaider.getCurrentRaid();

                if(raid != null)
                    raid.joinRaid(sourceRaider.getWave(), raider, null, false);
            }
            if(copy instanceof NeutralMob neutralMob) {
                NeutralMob sourceNeutralMob = (NeutralMob) source;
                neutralMob.setTarget(sourceNeutralMob.getTarget());
            }
            if(copy instanceof EnderDragon dragon) {
                EnderDragon sourceDragon = (EnderDragon) source;
                EndDragonFight fight = sourceDragon.getDragonFight();
                if(fight != null) {
                    dragon.setDragonFight(fight);
                    dragon.setFightOrigin(sourceDragon.getFightOrigin());
                }
            }

            level.addFreshEntity(copy);
        }

        return copy;
    }
}