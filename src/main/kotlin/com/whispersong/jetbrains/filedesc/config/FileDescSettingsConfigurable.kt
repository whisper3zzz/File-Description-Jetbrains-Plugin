package com.whispersong.jetbrains.filedesc.config

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class FileDescSettingsConfigurable : Configurable {
    private var component: FileDescSettingsComponent? = null

    override fun createComponent(): JComponent {
        component = FileDescSettingsComponent()
        return component!!.panel
    }

    override fun isModified(): Boolean = component?.isModified ?: false

    override fun apply() {
        component?.apply()
    }

    override fun reset() {
        component?.reset()
    }

    override fun disposeUIResources() {
        component = null
    }

    override fun getDisplayName(): String = "File Description"
}
