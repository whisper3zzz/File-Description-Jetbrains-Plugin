package com.whispersong.jetbrains.filedesc.listeners

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.whispersong.jetbrains.filedesc.config.FileDescSettings
import com.whispersong.jetbrains.filedesc.config.ProjectConfig
import com.whispersong.jetbrains.filedesc.utils.CommentGenerator
import com.whispersong.jetbrains.filedesc.utils.FileUtil
import java.util.concurrent.ConcurrentHashMap

class FileSaveListener : FileDocumentManagerListener {

    /**
     * 防重入：同一次 Ctrl+S 操作中，beforeAllDocumentsSaving 和 beforeDocumentSaving
     * 都可能触发，用 document 引用做去重，避免同一文档被 updateHeader 两次。
     */
    private val processedInThisSave = ConcurrentHashMap.newKeySet<Document>()

    /**
     * 用户按 Ctrl+S 时总会触发（无论文档是否有修改）。
     *
     * beforeDocumentSaving 只在文档有未保存修改时才触发，无修改时 Ctrl+S 不走那里。
     * 本方法覆盖 VSCode 风格场景：用户对未修改文件按 Ctrl+S，仍然刷新 header。
     * 只处理当前活跃编辑器的文件。
     */
    override fun beforeAllDocumentsSaving() {
        val state = FileDescSettings.getInstance()
        if (!state.changeUpdate) return

        processedInThisSave.clear()

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val editorManager = FileEditorManager.getInstance(project) ?: continue
            val files = editorManager.selectedFiles
            for (file in files) {
                if (!FileUtil.isSupportedFile(file)) continue

                val document = FileDocumentManager.getInstance().getDocument(file) ?: continue
                if (!processedInThisSave.add(document)) continue

                val ignorePaths = ProjectConfig.getMergedIgnorePaths(project)
                if (FileUtil.checkIsFileIgnored(file, ignorePaths)) continue

                CommentGenerator.updateHeader(document, file, project)
            }
        }
    }

    /**
     * 文档有修改时触发（IDE 内编辑后 Ctrl+S 保存）。
     * 更新 header 中 LastEditors / LastEditTime / FilePath。
     */
    override fun beforeDocumentSaving(document: Document) {
        val state = FileDescSettings.getInstance()
        if (!state.changeUpdate) return

        // 如果 beforeAllDocumentsSaving 已经处理过，跳过
        if (document in processedInThisSave) return

        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!FileUtil.isSupportedFile(file)) return

        val project = FileUtil.findProjectForFile(file)
        val ignorePaths = ProjectConfig.getMergedIgnorePaths(project)
        if (FileUtil.checkIsFileIgnored(file, ignorePaths)) return

        CommentGenerator.updateHeader(document, file, project)
    }
}
