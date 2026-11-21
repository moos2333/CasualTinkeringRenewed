package com.npstra.casualtinkering.util;

import com.npstra.casualtinkering.entity.EntityMagicSword;
import com.npstra.casualtinkering.init.ModEntities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class MagicSwordHelper {
    private MagicSwordHelper() {}

    public static void spawnMagicSwords(Level level, LivingEntity shooter, LivingEntity target, int count, float magicDamage) {
        if (level.isClientSide) return;
        for (int i = 0; i < count; i++) {
            level.addFreshEntity(new EntityMagicSword(ModEntities.MAGIC_SWORD.get(), level, shooter, target, magicDamage));
        }
    }
}
