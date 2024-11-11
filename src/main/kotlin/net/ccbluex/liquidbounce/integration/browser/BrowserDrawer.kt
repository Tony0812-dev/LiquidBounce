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
package net.ccbluex.liquidbounce.integration.browser

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.browser.supports.IBrowser
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import net.minecraft.util.TriState
import net.minecraft.util.Util
import java.util.function.Function

class BrowserDrawer(val browser: () -> IBrowser?) : Listenable {

    private val tabs
        get() = browser()?.getTabs() ?: emptyList()

    @Suppress("unused")
    val preRenderHandler = handler<GameRenderEvent> {
        browser()?.drawGlobally()

        for (tab in tabs) {
            tab.drawn = false
        }
    }

    @Suppress("unused")
    val windowResizeWHandler = handler<FrameBufferResizeEvent> { ev ->
        for (tab in tabs) {
            tab.resize(ev.width, ev.height)
        }
    }

    @Suppress("unused")
    val handleScreenRender = handler<ScreenRenderEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE
    ) { event ->
        for (tab in tabs) {
            if (tab.drawn) {
                continue
            }

            val scaleFactor = mc.window.scaleFactor.toFloat()
            val x = tab.position.x.toFloat() / scaleFactor
            val y = tab.position.y.toFloat() / scaleFactor
            val w = tab.position.width.toFloat() / scaleFactor
            val h = tab.position.height.toFloat() / scaleFactor

            renderTexture(event.context, tab.getTexture(), x, y, w, h)
            tab.drawn = true
        }
    }

    private var shouldReload = false

    @Suppress("unused")
    val onReload = handler<ResourceReloadEvent> {
        shouldReload = true
    }

    @Suppress("unused")
    val handleOverlayRender = handler<OverlayRenderEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE
    ) { event ->
        if (this.shouldReload) {
            for (tab in tabs) {
                tab.forceReload()
            }

            this.shouldReload = false
        }

        for (tab in tabs) {
            if (tab.drawn) {
                continue
            }

            if (tab.preferOnTop && mc.currentScreen != null) {
                continue
            }

            val scaleFactor = mc.window.scaleFactor.toFloat()
            val x = tab.position.x.toFloat() / scaleFactor
            val y = tab.position.y.toFloat() / scaleFactor
            val w = tab.position.width.toFloat() / scaleFactor
            val h = tab.position.height.toFloat() / scaleFactor

            renderTexture(event.context, tab.getTexture(), x, y, w, h)
            tab.drawn = true
        }
    }

    private val browserTextureLayer: Function<Identifier, RenderLayer> = Util.memoize { texture ->
        RenderLayer.of(
            "browser_textured",
            VertexFormats.POSITION_TEXTURE_COLOR,
            VertexFormat.DrawMode.QUADS,
            786432,
            RenderLayer.MultiPhaseParameters.builder()
                .texture(RenderPhase.Texture(texture, TriState.FALSE, false))
                .program(RenderPhase.POSITION_TEXTURE_COLOR_PROGRAM)
                .transparency(browserTransparency)
                .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                .build(false)
        )
    }

    private val browserTransparency: RenderPhase.Transparency = RenderPhase.Transparency("browser_transparency", {
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA)
    }, {
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
    })

    private fun renderTexture(context: DrawContext, texture: Identifier, x: Float, y: Float, width: Float, height: Float) {
        context.drawTexture(browserTextureLayer, texture, x.toInt(), y.toInt(), 0f, 0f, width.toInt(),
            height.toInt(), width.toInt(), height.toInt())
    }

}
