package com.whispersong.jetbrains.filedesc.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.whispersong.jetbrains.filedesc.utils.CommentGenerator
import com.whispersong.jetbrains.filedesc.utils.FileUtil

class GenerateHeaderAction : AnAction() {
    private val log = Logger.getInstance(GenerateHeaderAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.getData(CommonDataKeys.PROJECT)
        if (file == null) {
            log.warn("GenerateHeaderAction: no virtual file in context")
            return
        }
        if (!FileUtil.isSupportedFile(file)) {
            log.warn("GenerateHeaderAction: unsupported file type: ${file.extension}")
            return
        }

        log.info("GenerateHeaderAction triggered for: ${file.name}")

        try {
            val document = FileDocumentManager.getInstance().getDocument(file)
            if (document == null) {
                log.warn("GenerateHeaderAction: cannot get document for: ${file.name}")
                return
            }

            val content = document.text
            // Simple check: if file already has a header block, update it; otherwise insert
            if (CommentGenerator.hasHeaderComment(file, content)) {
                log.info("Header exists, updating: ${file.name}")
                CommentGenerator.updateHeader(document, file, project)
            } else {
                log.info("No header found, inserting: ${file.name}")
                CommentGenerator.checkAndAddHeader(document, file, project)
            }
        } catch (e: Exception) {
            log.error("GenerateHeaderAction failed", e)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = file != null && !file.isDirectory && FileUtil.isSupportedFile(file)
        e.presentation.isEnabledAndVisible = visible
    }
}
