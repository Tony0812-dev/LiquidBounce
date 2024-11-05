/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.utils.timing

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.utils.ClientUtils
import net.ccbluex.liquidbounce.utils.MinecraftInstance

object WaitTickUtils : MinecraftInstance(), Listenable {

    private val scheduledActions = mutableListOf<ScheduledAction>()

    fun schedule(ticks: Int, action: () -> Unit) = conditionalSchedule(ticks) { action(); true }

    fun conditionalSchedule(ticks: Int? = null, action: () -> Boolean) {
        if (ticks != null && ticks == 0) {
            action()

            return
        }

        scheduledActions += ScheduledAction(ClientUtils.runTimeTicks + (ticks ?: 0), action)
    }

    @EventTarget(priority = -1)
    fun onTick(event: GameTickEvent) {
        val currentTick = ClientUtils.runTimeTicks
        val iterator = scheduledActions.iterator()

        while (iterator.hasNext()) {
            val scheduledAction = iterator.next()

            if (currentTick >= scheduledAction.ticks && scheduledAction.action()) {
                iterator.remove()
            }
        }
    }

    private data class ScheduledAction(val ticks: Int, val action: () -> Boolean)

}