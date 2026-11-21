package com.npstra.casualtinkering.modifiers;

import com.npstra.casualtinkering.entity.GunbladeBullet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.List;

public class GunbladeModifier extends Modifier implements GeneralInteractionModifierHook, MeleeHitModifierHook, TooltipModifierHook {
    private static final ResourceLocation KEY_CHARGES = ResourceLocation.tryBuild("casualtinkering", "gunblade_charges");
    private static final int BASE_CHARGES = 3;
    private static final int COOLDOWN_TICKS = 10;
    private static final float SHOT_DAMAGE_RATIO = 0.5F;
    private static final float BULLET_SPEED = 3.0F;

    private static int getMaxCharges(ModifierEntry modifier) {
        return BASE_CHARGES + (int) modifier.getLevel() - 1;
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.GENERAL_INTERACT, ModifierHooks.MELEE_HIT, ModifierHooks.TOOLTIP);
    }

    @Override
    public Component getDisplayName(int level) {
        return getDisplayName();
    }

    @Override
    public InteractionResult onToolUse(IToolStackView tool, ModifierEntry modifier, Player player, InteractionHand hand, InteractionSource source) {
        if (source != InteractionSource.RIGHT_CLICK || tool.isBroken()) return InteractionResult.PASS;
        ModDataNBT data = tool.getPersistentData();
        int charges = data.getInt(KEY_CHARGES);
        if (charges <= 0) {
            if (player.level().isClientSide) player.playSound(SoundEvents.DISPENSER_FAIL, 0.5F, 1.5F);
            return InteractionResult.FAIL;
        }
        Level level = player.level();
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        if (level.isClientSide) return InteractionResult.SUCCESS;

        data.putInt(KEY_CHARGES, charges - 1);
        ToolDamageUtil.damage(tool, 1, player, stack);
        float damage = tool.getStats().get(ToolStats.ATTACK_DAMAGE) * SHOT_DAMAGE_RATIO;
        float speed = ConditionalStatModifierHook.getModifiedStat(tool, player, ToolStats.VELOCITY) * BULLET_SPEED;
        level.addFreshEntity(new GunbladeBullet(level, player, stack, tool, damage, speed));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 1.15F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.8));
            serverLevel.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);
            serverLevel.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        if (context.isProjectile() || context.isExtraAttack() || damage <= 0) return;
        ModDataNBT data = tool.getPersistentData();
        int charges = data.getInt(KEY_CHARGES);
        if (charges < getMaxCharges(modifier)) data.putInt(KEY_CHARGES, charges + 1);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier, Player player, List<Component> tooltip, TooltipKey key, TooltipFlag flag) {
        int charges = tool.getPersistentData().getInt(KEY_CHARGES);
        tooltip.add(Component.translatable("modifier.casualtinkering.gunblade.charges", charges, getMaxCharges(modifier)).withStyle(ChatFormatting.GRAY));
    }
}
