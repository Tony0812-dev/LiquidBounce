/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015-2024 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura

import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura.currentTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura.player
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura.world
import net.ccbluex.liquidbounce.utils.combat.getEntitiesBoxInRange
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object CrystalAuraDamageOptions : Configurable("Damage") {

    private val maxSelfDamage by float("MaxSelfDamage", 2.0F, 0.0F..10.0F)
    private val maxFriendDamage by float("MaxFriendDamage", 1.0F, 0.0F..10.0F)
    private val minEnemyDamage by float("MinEnemyDamage", 5.0F, 0.0F..10.0F)

    /**
     * Won't place / break crystals that would kill us.
     */
    private val antiSuicide by boolean("AntiSuicide", true)

    /**
     * Only places / breaks crystals that deal more damage to the enemy than to us.
     */
    val efficient by boolean("Efficient", true)

    /**
     * Doesn't include blocks that will get blown away in the exposure calculation used for the damage calculation.
     */
    val terrain by boolean("Terrain", true)

    val cacheMap = object : LinkedHashMap<DamageConstellation, DamageProvider>(
        64,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DamageConstellation, DamageProvider>): Boolean {
            return size > 64
        }
    }

    /**
     * Approximates how favorable an explosion of a crystal at [pos] in a given [world] would be
     */ // TODO by equal positions take self min damage
    internal fun approximateExplosionDamage(pos: Vec3d, requestingSubmodule: RequestingSubmodule): Float? {
        val target = currentTarget ?: return null
        val damageToTarget = target.getDamage(pos, requestingSubmodule, CheckedEntity.TARGET)
        val notEnoughDamage = damageToTarget.isSmallerThan(minEnemyDamage)
        if (notEnoughDamage) {
            return null
        }

        val selfDamage = player.getDamage(pos, requestingSubmodule, CheckedEntity.SELF)
        val willKill = antiSuicide && selfDamage.isAnyGreaterThanOrEqual(player.health + player.absorptionAmount)
        val tooMuchDamage = selfDamage.isGreaterThan(maxSelfDamage)
        if (willKill || tooMuchDamage) {
            return null
        }

        var tooMuchDamageForFriend = false
        if (maxFriendDamage < 10f) { // 10f is the maximum allowed by the setting
            val friends =
                world
                    .getEntitiesBoxInRange(pos, 6.0) { FriendManager.isFriend(it) && it.boundingBox.maxY > pos.y }
                    .filterIsInstance<LivingEntity>()

            if (friends.any {
                it.getDamage(pos, requestingSubmodule, CheckedEntity.OTHER).isGreaterThan(maxFriendDamage)
            }) {
                tooMuchDamageForFriend = true
            }
        }

        val isNotEfficient = efficient && damageToTarget.isSmallerThanOrEqual(selfDamage)
        if (tooMuchDamageForFriend || isNotEfficient) {
            return null
        }

        return damageToTarget.getFixed()
    }

    private fun LivingEntity.getDamage(
        crystal: Vec3d,
        requestingSubmodule: RequestingSubmodule,
        checkedEntity: CheckedEntity
    ): DamageProvider {
        val damageConstellation = DamageConstellation(this, blockPos, crystal, requestingSubmodule)
        return cacheMap.computeIfAbsent(damageConstellation) {
            val excludeNotBlastResistant = terrain &&
                (!requestingSubmodule.basePlace || SubmoduleBasePlace.terrain)
            checkedEntity.getDamage(
                this,
                requestingSubmodule,
                crystal,
                if (excludeNotBlastResistant) 9f else null,
                if (requestingSubmodule.basePlace) BlockPos.ofFloored(crystal).down() else null
            )
        }
    }

    @JvmRecord
    data class DamageConstellation(
        val entity: LivingEntity,
        val pos: BlockPos,
        val crystal: Vec3d,
        val requestingSubmodule: RequestingSubmodule // TODO optimize the cache, so that it caches the damage values
        // itself
    )

    enum class RequestingSubmodule(val basePlace: Boolean) {
        PLACE(false),
        DESTROY(false),
        BASE_PLACE(true);
    }

    private enum class CheckedEntity {

        SELF {
            override fun getDamage(
                entity: LivingEntity,
                requestingSubmodule: RequestingSubmodule,
                crystal: Vec3d,
                maxBlastResistance: Float?,
                include: BlockPos?
            ): DamageProvider {
                val ticks = when (requestingSubmodule) {
                    RequestingSubmodule.PLACE -> SelfPredict.placeTicks
                    RequestingSubmodule.DESTROY -> SelfPredict.destroyTicks
                    RequestingSubmodule.BASE_PLACE -> SelfPredict.basePlaceTicks
                }

                return SelfPredict.getDamage(entity as PlayerEntity, ticks, crystal, maxBlastResistance, include)
            }
        },
        TARGET {
            override fun getDamage(
                entity: LivingEntity,
                requestingSubmodule: RequestingSubmodule,
                crystal: Vec3d,
                maxBlastResistance: Float?,
                include: BlockPos?
            ): DamageProvider {
                if (entity !is PlayerEntity) {
                    return OTHER.getDamage(entity, requestingSubmodule, crystal, maxBlastResistance, include)
                }

                val ticks = when (requestingSubmodule) {
                    RequestingSubmodule.PLACE -> TargetPredict.placeTicks
                    RequestingSubmodule.DESTROY -> TargetPredict.destroyTicks
                    RequestingSubmodule.BASE_PLACE -> TargetPredict.basePlaceTicks
                }

                return TargetPredict.getDamage(entity, ticks, crystal, maxBlastResistance, include)
            }
        },
        OTHER {
            override fun getDamage(
                entity: LivingEntity,
                requestingSubmodule: RequestingSubmodule,
                crystal: Vec3d,
                maxBlastResistance: Float?,
                include: BlockPos?
            ): DamageProvider {
                return NormalDamageProvider(entity.getDamageFromExplosion(
                    crystal,
                    maxBlastResistance = maxBlastResistance,
                    include = include
                ))
            }
        };

        abstract fun getDamage(
            entity: LivingEntity,
            requestingSubmodule: RequestingSubmodule,
            crystal: Vec3d,
            maxBlastResistance: Float? = null,
            include: BlockPos? = null
        ): DamageProvider

    }

}