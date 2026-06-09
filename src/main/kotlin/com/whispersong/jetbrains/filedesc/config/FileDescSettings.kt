package com.whispersong.jetbrains.filedesc.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.whispersong.jetbrains.filedesc.FileDescSettings",
    storages = [Storage("fileDescription.xml", roamingType = RoamingType.DEFAULT)]
)
class FileDescSettings : PersistentStateComponent<FileDescSettings> {
    var createFileAdd: Boolean = true
    var openFileCheck: Boolean = false
    var changeUpdate: Boolean = true
    var compactComment: Boolean = false
    var customHeaderComment: String = DEFAULT_HEADER
    var ignorePaths: String = ".git/*\n.idea/*\n.vscode/*\nnode_modules/*"
    var timeFormat: String = "yyyy-MM-dd HH:mm:ss"
    var manualAuthor: String = ""

    override fun getState(): FileDescSettings = this

    override fun loadState(state: FileDescSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): FileDescSettings {
            return ApplicationManager.getApplication().getService(FileDescSettings::class.java)
        }

        val DEFAULT_HEADER = """{"Copyright":"Copyright (c) ${'$'}{now_year} ${'$'}{git_name}. All rights reserved.","Author":"auto:vcs","Date":"","LastEditors":"auto:vcs","LastEditTime":"","FilePath":"","Description":""}"""
    }
}
