package dev.hypercodec.dupe_on_hit;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MobDupe.MODID)
public class MobDupe {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "mobdupe";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final RegistryObject<MobEffect> LIMITED_LIFE = EFFECTS.register("limited_life", LimitedLife::new);

    public MobDupe() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EFFECTS.register(modEventBus);

        // register serverside events
        MinecraftForge.EVENT_BUS.register(this);

        // register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onDropLoot(@NotNull LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if(entity.level().isClientSide || (!Config.dupedEntitiesDropLoot && entity.getTags().contains("duped"))) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public void onEntityJoin(@NotNull EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide) return;

        Entity entity = event.getEntity();
        if(entity instanceof AreaEffectCloud cloud) {
            // prevent creepers from inflicting other mobs with limited life
            MobEffectInstance[] newEffects = cloud.getPotion().getEffects()
                    .stream()
                    .filter(effect -> effect.getEffect() != LIMITED_LIFE.get())
                    .toArray(MobEffectInstance[]::new);

            if(newEffects.length == 0) {
                // remove the cloud entirely if it has no effects
                event.setCanceled(true);
                return;
            }

            cloud.setPotion(new Potion(newEffects));
        }
    }

    @SubscribeEvent
    public void onHurt(@NotNull LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity entity = source.getEntity();
        if (!(entity instanceof LivingEntity living) ||
                entity.level().isClientSide ||
                !entity.isAlive() ||
                (!Config.sameTypeDuping && living.getType() == entity.getType()))
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

            // for some reason the method with "withoutId" in the name still copies over the id
            copy.setUUID(UUID.randomUUID());
            copy.addTag("duped");
            copy.resetFallDistance();
            copy.setNoActionTime(5);

            if(!Config.dupedEntitiesDropLoot)
                copy.skipDropExperience();

            for (MobEffectInstance instance : source.getActiveEffects()) {
                MobEffect effect = instance.getEffect();

                if (!effect.isInstantenous()
                        && effect != LIMITED_LIFE.get()) {

                    copy.addEffect(new MobEffectInstance(instance));
                }
            }
            if(Config.gracePeriod > 0)
                copy.forceAddEffect(new MobEffectInstance(LIMITED_LIFE.get(), Config.gracePeriod, 0, false, false), null);

//            for (EquipmentSlot slot : EquipmentSlot.values())
//                copy.setItemSlot(slot, source.getItemBySlot(slot));

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
//            if(copy instanceof AgeableMob ageable) {
//                AgeableMob sourceAgeable = (AgeableMob) source;
//                ageable.setAge(sourceAgeable.getAge());
//            }
//            if(copy instanceof VariantHolder variantHolder) {
//                VariantHolder sourceVariantHolder = (VariantHolder) source;
//                variantHolder.setVariant(sourceVariantHolder.getVariant());
//            }
//            if(copy instanceof Warden warden) {
//                Warden sourceWarden = (Warden) source;
//                AngerManagement sourceAnger = sourceWarden.getAngerManagement();
//                LivingEntity suspect = sourceAnger.getActiveEntity().orElse(null);
//                if(suspect != null) {
//                    AngerManagement copyAnger = warden.getAngerManagement();
//                    copyAnger.increaseAnger(suspect, sourceAnger.getActiveAnger(suspect));
//                }
//            }
//            if(copy instanceof TamableAnimal tamable) {
//                TamableAnimal sourceTamable = (TamableAnimal) source;
//                LivingEntity owner = sourceTamable.getOwner();
//                if(owner instanceof Player player)
//                    tamable.tame(player);
//            }

            level.addFreshEntity(copy);
        }

        return copy;
    }
}