package com.enmodify.mixin;

import com.enmodify.ModConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

@Mixin(LivingEntity.class)
public abstract class EPFmodify extends Entity {

    public EPFmodify(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow public abstract boolean hasEffect(Holder<MobEffect> effect);
    @Shadow public abstract MobEffectInstance getEffect(Holder<MobEffect> effect);

    /**
     * @author MuHinf
     * @reason 允许自定义 EPF 分母和上限
     */
    @Overwrite
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return amount;
        }

        float denominator = ModConfig.defenseDenominator;

        // 1. 抗性提升 (Resistance) 逻辑
        if (this.hasEffect(MobEffects.RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            int level = this.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1;
            float reduction = level * 5.0F; 
            float remaining = Math.max(denominator - reduction, 0.0F);
            float originalAmount = amount;
            
            amount = Math.max(amount * remaining / denominator, 0.0F);

            float prevented = originalAmount - amount;
            if (prevented > 0.0f) {
                if ((Object)this instanceof ServerPlayer) {
                    ((ServerPlayer)(Object)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(prevented * 10.0F));
                } else if (source.getEntity() instanceof ServerPlayer) {
                    ((ServerPlayer)source.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(prevented * 10.0F));
                }
            }
        }

        // 2. 附魔保护 (EPF) 逻辑
        Level world = this.level();
        if (world instanceof ServerLevel) {
            float epf = EnchantmentHelper.getDamageProtection((ServerLevel)world, (LivingEntity)(Object)this, source);
            if (epf > 0.0F) {
                // 确定最终上限
                float finalLimit = (ModConfig.maxEpfLimit >= 0) ? ModConfig.maxEpfLimit : (denominator - 1.0F);
                
                // 应用上限并计算
                float clampedEpf = Math.min(epf, finalLimit);
                amount *= (1.0F - clampedEpf / denominator);
            }
        }

        return amount;
    }
}