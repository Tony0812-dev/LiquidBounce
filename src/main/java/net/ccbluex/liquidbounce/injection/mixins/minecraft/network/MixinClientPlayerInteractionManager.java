/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.injection.mixins.minecraft.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.*;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoBow;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoClicker;
import net.ccbluex.liquidbounce.utils.client.SilentHotbar;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    /**
     * Hook attacking entity
     */
    @Inject(method = "attackEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;syncSelectedSlot()V", shift = At.Shift.AFTER))
    private void hookAttack(PlayerEntity player, Entity target, CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new AttackEntityEvent(target));
    }

    /**
     * Hook into updateBlockBreakingProgress method at HEAD and call BlockBreakingProgress event.
     */
    @Inject(method = "updateBlockBreakingProgress", at = @At(value = "HEAD"))
    private void hookBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        final BlockBreakingProgressEvent blockBreakingProgressEvent = new BlockBreakingProgressEvent(pos);
        EventManager.INSTANCE.callEvent(blockBreakingProgressEvent);
    }

    /**
     * Hook into cancel block breaking at HEAD and call cancel block breaking event, which is able to cancel the execution.
     */
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void hookCancelBlockBreaking(CallbackInfo callbackInfo) {
        final CancelBlockBreakingEvent cancelEvent = new CancelBlockBreakingEvent();
        EventManager.INSTANCE.callEvent(cancelEvent);

        if (cancelEvent.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void hookAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        var attackEvent = new BlockAttackEvent(pos);
        EventManager.INSTANCE.callEvent(attackEvent);
        if (attackEvent.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * @author superblaubeere27
     */
    @ModifyExpressionValue(method = "syncSelectedSlot", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I"))
    private int hookCustomSelectedSlot(int original) {
        return SilentHotbar.INSTANCE.getServersideSlot();
    }

    @Inject(method = "hasLimitedAttackSpeed", at = @At("HEAD"), cancellable = true)
    private void injectAutoClicker(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleAutoClicker.INSTANCE.getRunning() && ModuleAutoClicker.Left.INSTANCE.getEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    private void hookItemInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        final PlayerInteractedItem cancelEvent = new PlayerInteractedItem(player, hand, cir.getReturnValue());
        EventManager.INSTANCE.callEvent(cancelEvent);
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void stopUsingItem(PlayerEntity player, CallbackInfo callbackInfo) {
        ModuleAutoBow.onStopUsingItem();
    }

    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void setGameMode(GameMode gameMode, CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new GameModeChangeEvent(gameMode));
    }

    @Inject(method = "setGameModes", at = @At("RETURN"))
    private void setGameModes(GameMode gameMode, GameMode previousGameMode, CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new GameModeChangeEvent(gameMode));
    }

}
