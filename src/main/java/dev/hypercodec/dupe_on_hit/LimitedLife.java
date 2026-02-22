package dev.hypercodec.dupe_on_hit;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.jetbrains.annotations.NotNull;

public class LimitedLife extends MobEffect {
    public LimitedLife() {
        super(MobEffectCategory.HARMFUL, 0x550000);
    }

    @Override
    public boolean isInstantenous() {
        return false;
    }

    @Override
    public void removeAttributeModifiers(@NotNull LivingEntity entity, @NotNull AttributeMap attributes, int level) {
        super.removeAttributeModifiers(entity, attributes, level);

        if(!entity.level().isClientSide) {
            if(Config.graceEndInstakill)
                entity.kill();
//                entity.remove(Entity.RemovalReason.DISCARDED);
            else
                entity.forceAddEffect(new MobEffectInstance(
                        MobEffects.WITHER,
                        MobEffectInstance.INFINITE_DURATION,
                        1
                ), null);
        }
    }
}
