package com.whispersong.jetbrains.filedesc.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.whispersong.jetbrains.filedesc.config.FileDescSettings
import com.whispersong.jetbrains.filedesc.config.ProjectConfig
import com.whispersong.jetbrains.filedesc.utils.CommentGenerator
import com.whispersong.jetbrains.filedesc.utils.FileUtil

class FileCreateListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
            if (event is VFileCreateEvent) {
                val file = event.file ?: continue
                if (file.isDirectory) continue

                val state = FileDescSettings.getInstance()
                if (!state.createFileAdd) continue

                val project = FileUtil.findProjectForFile(file)
                val ignorePaths = ProjectConfig.getMergedIgnorePaths(project)
                if (FileUtil.checkIsFileIgnored(file, ignorePaths)) continue

                if (!FileUtil.isSupportedFile(file)) continue

                val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
                ApplicationManager.getApplication().invokeLater {
                    CommentGenerator.checkAndAddHeader(document, file, project)
                }
            }
        }
    }
}
