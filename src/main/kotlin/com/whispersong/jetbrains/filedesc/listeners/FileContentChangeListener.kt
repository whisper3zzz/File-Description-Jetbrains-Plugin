package com.whispersong.jetbrains.filedesc.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.whispersong.jetbrains.filedesc.config.FileDescSettings
import com.whispersong.jetbrains.filedesc.config.ProjectConfig
import com.whispersong.jetbrains.filedesc.utils.CommentGenerator
import com.whispersong.jetbrains.filedesc.utils.FileUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * 监听 VFS 内容变更事件，处理外部编辑器（如 AI 工具）修改文件后的 header 刷新。
 *
 * IntelliJ 的 FileDocumentManagerListener.beforeDocumentSaving 仅在 IDE 内保存时触发，
 * 外部工具直接写磁盘不会走该流程。本 listener 监听 VFileContentChangeEvent，
 * 当检测到外部修改时，在 IDE 线程上刷新 header。
 *
 * updateHeader 是幂等的（newContent != content 才写入），与 FileSaveListener 不会冲突。
 */
class FileContentChangeListener : BulkFileListener {
    private val scheduledFiles = ConcurrentHashMap.newKeySet<String>()

    override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
            if (event !is VFileContentChangeEvent) continue

            val file = event.file
            if (file.isDirectory) continue
            if (CommentGenerator.isInternalUpdate(file)) continue

            val state = FileDescSettings.getInstance()
            if (!state.changeUpdate) continue

            if (!FileUtil.isSupportedFile(file)) continue

            val project = FileUtil.findProjectForFile(file) ?: continue
            val ignorePaths = ProjectConfig.getMergedIgnorePaths(project)
            if (FileUtil.checkIsFileIgnored(file, ignorePaths)) continue

            if (!scheduledFiles.add(file.path)) continue

            ApplicationManager.getApplication().invokeLater {
                try {
                    if (project.isDisposed) return@invokeLater
                    if (!file.isValid) return@invokeLater
                    if (CommentGenerator.isInternalUpdate(file)) return@invokeLater

                    val document = FileDocumentManager.getInstance().getDocument(file) ?: return@invokeLater
                    CommentGenerator.updateHeader(document, file, project)
                } finally {
                    scheduledFiles.remove(file.path)
                }
            }
        }
    }
}
