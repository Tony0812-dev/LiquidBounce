/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2016 - 2021 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EntityTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.*
import net.ccbluex.liquidbounce.utils.extensions.exactPosition
import net.ccbluex.liquidbounce.utils.extensions.findEnemy
import net.ccbluex.liquidbounce.utils.extensions.upwards
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

/**
 * Criticals module
 *
 * Automatically crits every time you attack someone
 */
object ModuleCriticals : Module("Criticals", Category.COMBAT) {

    private object CritModeConfigurable : ModeConfigurable(this, "Mode", "Packet", {
        NoneMode(CritModeConfigurable)
        PacketCrit
        JumpCrit
    })

    private object PacketCrit : Mode("Packet", CritModeConfigurable) {

        val attackHandler = handler<AttackEvent> { event ->
            val (x, y, z) = player.exactPosition

            network.sendPacket(PlayerMoveC2SPacket.PositionOnly(x, y + 0.11, z, false))
            network.sendPacket(PlayerMoveC2SPacket.PositionOnly(x, y + 0.1100013579, z, false))
            network.sendPacket(PlayerMoveC2SPacket.PositionOnly(x, y + 0.0000013579, z, false))
        }

    }

    private object JumpCrit : Mode("Jump", CritModeConfigurable) {

        // There are diffrent possible jump heights to crit enemy
        //   Hop: 0.1 (like in Wurst-Client)
        //   LowJump: 0.3425 (for some weird AAC version)
        //
        val height by float("Height", 0.42f, 0.1f..0.42f)

        // Jump crit should just be active until an enemy is in your reach to be attacked
        val range by float("Range", 4f, 1f..6f)

        val tickHandler = handler<EntityTickEvent> {
            val (enemy, distance) = world.findEnemy(range) ?: return@handler
            println("$enemy (distance: $distance)")

            if (player.isOnGround) {
                player.upwards(height)
            }
        }

    }

    /**
     * Just some visuals.
     */
    private object VisualsConfigurable : ListenableConfigurable(this, "Visuals", true) {

        val critParticles by int("CritParticles", 1, 0..20)
        val magicParticles by int("MagicParticles", 0, 0..20)

        val attackHandler = handler<AttackEvent> { event ->
            repeat(critParticles) {
                player.addCritParticles(event.enemy)
            }

            repeat(magicParticles) {
                player.addEnchantedHitParticles(event.enemy)
            }
        }

    }

    init {
        CritModeConfigurable.initialize()
        tree(CritModeConfigurable)
        tree(VisualsConfigurable)
    }

}
