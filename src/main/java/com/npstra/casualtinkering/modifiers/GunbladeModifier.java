package com.npstra.casualtinkering.modifiers;

import com.npstra.casualtinkering.entity.GunbladeBullet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
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
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.entity.ThrownTool;

import java.util.List;

public class GunbladeModifier extends Modifier implements GeneralInteractionModifierHook, MeleeHitModifierHook, TooltipModifierHook {
    private static final ResourceLocation KEY_CHARGES = ResourceLocation.tryBuild("casualtinkering", "gunblade_charges");
    private static final ResourceLocation KEY_AMMO = ResourceLocation.tryBuild("casualtinkering", "gunblade_ammo");
    private static final TagKey<Item> TAG_AMMO = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.tryBuild("tconstruct", "throwable"));
    private static final int BASE_CHARGES = 3;
    private static final int COOLDOWN_TICKS = 10;
    private static final float BASE_ATTACK_SPEED = 1.4F;
    private static final float MIN_COOLDOWN_SPEED = 0.5F;
    private static final float MAX_COOLDOWN_SPEED = 2.0F;
    private static final float SHOT_DAMAGE_RATIO = 0.5F;
    private static final float CHAMBER_COOLDOWN_MULT = 1.25F;
    private static final float BULLET_SPEED = 3.0F;

    private static int getMaxCharges(ModifierEntry modifier) {
        return BASE_CHARGES + (int) modifier.getLevel() - 1;
    }

    private static int getCooldown(IToolStackView tool, LivingEntity holder) {
        float speed = Mth.clamp(ConditionalStatModifierHook.getModifiedStat(tool, holder, ToolStats.ATTACK_SPEED), MIN_COOLDOWN_SPEED, MAX_COOLDOWN_SPEED);
        return Math.max(1, Math.round(COOLDOWN_TICKS * BASE_ATTACK_SPEED / speed));
    }

    private static ItemStack getAmmo(ModDataNBT data) {
        CompoundTag tag = data.get(KEY_AMMO, CompoundTag::getCompound);
        return tag.contains("id") ? ItemStack.of(tag) : ItemStack.EMPTY;
    }

    private static void setAmmo(ModDataNBT data, ItemStack stack) {
        if (stack.isEmpty()) data.remove(KEY_AMMO);
        else data.put(KEY_AMMO, stack.save(new CompoundTag()));
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
        if (player.isShiftKeyDown()) return handleChamberSwap(data, player, hand);
        ItemStack gunblade = player.getItemInHand(hand);
        ItemStack ammo = getAmmo(data);
        if (ammo.isEmpty()) {
            int charges = data.getInt(KEY_CHARGES);
            if (charges <= 0) {
                if (player.level().isClientSide) player.playSound(SoundEvents.DISPENSER_FAIL, 0.5F, 1.5F);
                return InteractionResult.FAIL;
            }
            return fire(player.level(), player, hand, gunblade, tool, data, charges - 1, ItemStack.EMPTY);
        }
        return fire(player.level(), player, hand, gunblade, tool, data, data.getInt(KEY_CHARGES), ammo);
    }

    /** 发射：ammo 为空时消耗 1 装填并射出枪刃弹丸，否则将弹舱弹射物本体掷出（消耗 1 枚）。 */
    private InteractionResult fire(Level level, Player player, InteractionHand hand, ItemStack gunblade,
                                   IToolStackView tool, ModDataNBT data, int newCharges, ItemStack ammo) {
        int cooldown = getCooldown(tool, player);
        if (!ammo.isEmpty()) cooldown = Math.round(cooldown * CHAMBER_COOLDOWN_MULT);
        player.getCooldowns().addCooldown(gunblade.getItem(), cooldown);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        data.putInt(KEY_CHARGES, newCharges);
        if (ammo.isEmpty()) {
            float speed = ConditionalStatModifierHook.getModifiedStat(tool, player, ToolStats.VELOCITY) * BULLET_SPEED;
            level.addFreshEntity(new GunbladeBullet(level, player, gunblade, tool,
                    tool.getStats().get(ToolStats.ATTACK_DAMAGE) * SHOT_DAMAGE_RATIO, speed));
        } else if (throwChamberAmmo(level, player, hand, ammo)) {
            ammo.shrink(1);
            setAmmo(data, ammo);
        }
        ToolDamageUtil.damage(tool, 1, player, gunblade);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, ammo.isEmpty() ? 1.15F : 1.35F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.8));
            serverLevel.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);
            serverLevel.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        }
        return InteractionResult.SUCCESS;
    }

    /** 将弹舱弹射物本体掷出：蓄力型工具按官方满蓄力投掷，其余走物品原生 use。返回是否实际掷出。 */
    private boolean throwChamberAmmo(Level level, Player player, InteractionHand hand, ItemStack ammo) {
        ItemStack stack = ammo.copyWithCount(1);
        ToolStack ammoTool = ToolStack.from(stack);
        if (isChargeThrower(ammoTool)) {
            float velocity = ConditionalStatModifierHook.getModifiedStat(ammoTool, player, ToolStats.VELOCITY);
            ThrownTool thrown = new ThrownTool(level, player, stack, 1.0F, velocity,
                    ConditionalStatModifierHook.getModifiedStat(ammoTool, player, ToolStats.WATER_INERTIA));
            thrown.setOriginalSlot(player.getInventory().selected);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity * 2.0F,
                    ModifierUtil.getInaccuracy(ammoTool, player));
            if (player.getAbilities().instabuild) thrown.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            thrown.onRelease(player, PersistentDataCapability.getOrWarn(thrown));
            level.addFreshEntity(thrown);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        ItemStack original = player.getItemInHand(hand);
        player.setItemInHand(hand, stack);
        InteractionResultHolder<ItemStack> result = stack.getItem().use(level, player, hand);
        player.setItemInHand(hand, original);
        return result.getResult().consumesAction();
    }

    private static boolean isChargeThrower(IToolStackView tool) {
        for (ModifierEntry entry : tool.getModifierList()) {
            GeneralInteractionModifierHook hook = entry.getHook(ModifierHooks.GENERAL_INTERACT);
            if (hook != null && hook.getUseDuration(tool, entry) > 0) return true;
        }
        return false;
    }

    /** 潜行右键：副手持有合法弹射物时装入弹舱并换出旧弹药，否则弹出弹舱内容。 */
    private InteractionResult handleChamberSwap(ModDataNBT data, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(other);
        ItemStack current = getAmmo(data);
        if (!held.isEmpty() && held.is(TAG_AMMO)) {
            if (player.level().isClientSide) {
                player.playSound(SoundEvents.SHULKER_BOX_OPEN, 0.6F, 1.4F);
                return InteractionResult.SUCCESS;
            }
            setAmmo(data, held.copy());
            player.setItemInHand(other, current);
        } else if (!current.isEmpty()) {
            if (player.level().isClientSide) {
                player.playSound(SoundEvents.SHULKER_BOX_CLOSE, 0.6F, 1.2F);
                return InteractionResult.SUCCESS;
            }
            setAmmo(data, ItemStack.EMPTY);
            player.setItemInHand(other, current);
        } else {
            return InteractionResult.FAIL;
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
        ModDataNBT data = tool.getPersistentData();
        int charges = data.getInt(KEY_CHARGES);
        tooltip.add(Component.translatable("modifier.casualtinkering.gunblade.charges", charges, getMaxCharges(modifier)).withStyle(ChatFormatting.GRAY));
        ItemStack ammo = getAmmo(data);
        Component ammoName = ammo.isEmpty()
                ? Component.translatable("modifier.casualtinkering.gunblade.chamber.empty").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal("").append(ammo.getHoverName()).append(" x" + ammo.getCount()).withStyle(ChatFormatting.GRAY);
        tooltip.add(Component.translatable("modifier.casualtinkering.gunblade.chamber", ammoName).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("modifier.casualtinkering.gunblade.chamber.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
