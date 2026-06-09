package com.whispersong.jetbrains.filedesc.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.whispersong.jetbrains.filedesc.config.FileDescSettings
import com.whispersong.jetbrains.filedesc.config.ProjectConfig
import com.whispersong.jetbrains.filedesc.utils.CommentGenerator
import com.whispersong.jetbrains.filedesc.utils.FileUtil

class FileOpenListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val state = FileDescSettings.getInstance()
        if (!state.openFileCheck) return
        if (file.isDirectory || !FileUtil.isSupportedFile(file)) return

        val project = source.project
        if (project.isDisposed) return

        val ignorePaths = ProjectConfig.getMergedIgnorePaths(project)
        if (FileUtil.checkIsFileIgnored(file, ignorePaths)) return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || !file.isValid) return@invokeLater
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@invokeLater
            CommentGenerator.checkAndAddHeader(document, file, project)
        }
    }
}
