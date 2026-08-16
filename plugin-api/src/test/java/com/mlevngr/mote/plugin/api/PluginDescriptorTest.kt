package com.mlevngr.mote.plugin.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDescriptorTest {
    @Test
    fun `compatible descriptor requires supported version id and action`() {
        assertTrue(descriptor().isCompatible())
        assertFalse(descriptor(apiVersion = 2).isCompatible(hostApiVersion = 1))
        assertFalse(descriptor(pluginId = "").isCompatible())
        assertFalse(descriptor(actions = emptyList()).isCompatible())
    }

    @Test
    fun `authorization changes when api or capabilities change`() {
        val base = descriptor()

        assertNotEquals(base.approvalKey("pkg"), base.copy(apiVersion = 2).approvalKey("pkg"))
        assertNotEquals(
            base.approvalKey("pkg"),
            base.copy(capabilities = base.capabilities + PluginCapability.NETWORK_ACCESS)
                .approvalKey("pkg")
        )
    }

    private fun descriptor(
        apiVersion: Int = 1,
        pluginId: String = "plugin",
        actions: List<PluginAction> = listOf(PluginAction("run", "Run", "Run it"))
    ) = PluginDescriptor(
        apiVersion = apiVersion,
        pluginId = pluginId,
        label = "Plugin",
        description = "Description",
        capabilities = setOf(PluginCapability.READ_FULL_NOTE),
        actions = actions
    )
}
