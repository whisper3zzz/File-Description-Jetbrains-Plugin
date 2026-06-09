package com.whispersong.jetbrains.filedesc.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

object FileUtil {

    data class CommentStyle(
        val start: String?,
        val end: String?,
        val linePrefix: String,
        val linePrefixRegex: String,
        val atSymbol: String,
        val startInsertAfter: String = ""
    )

    /** Cache compiled ignore regex patterns */
    private val ignoreRegexCache = ConcurrentHashMap<String, Regex>()
    private val envFileRegex = Regex("\\.env(\\.[^.]+)?")

    private val blockStyle = CommentStyle(
        start = "/**\n", end = " */\n",
        linePrefix = " * ", linePrefixRegex = " \\* ",
        atSymbol = "@", startInsertAfter = ""
    )
    private val compactBlockStyle = CommentStyle(
        start = "/*\n", end = " */\n",
        linePrefix = " * ", linePrefixRegex = " \\* ",
        atSymbol = "@", startInsertAfter = ""
    )
    private val phpStyle = CommentStyle(
        start = "/**\n", end = " */\n",
        linePrefix = " * ", linePrefixRegex = " \\* ",
        atSymbol = "@", startInsertAfter = "<?php\n"
    )
    private val htmlStyle = CommentStyle(
        start = "<!--\n", end = " -->\n",
        linePrefix = " * ", linePrefixRegex = " \\* ",
        atSymbol = "@", startInsertAfter = ""
    )
    private val propertyStyle = CommentStyle(
        start = "###\n", end = "###\n",
        linePrefix = " # ", linePrefixRegex = " # ",
        atSymbol = "@", startInsertAfter = ""
    )
    private val pythonStyle = CommentStyle(
        start = "\"\"\"\n", end = "\"\"\"\n",
        linePrefix = " ", linePrefixRegex = " ",
        atSymbol = "", startInsertAfter = ""
    )
    private val dartStyle = CommentStyle(
        start = null, end = null,
        linePrefix = "/// ", linePrefixRegex = "/// ",
        atSymbol = "", startInsertAfter = ""
    )

    fun getFileExtension(file: VirtualFile): String {
        var ext = file.extension ?: ""
        if (envFileRegex.matches(file.name)) {
            ext = "env"
        }
        return ext.lowercase()
    }

    fun getCommentStyle(file: VirtualFile, compactComment: Boolean = false): CommentStyle? =
        getCommentStyle(file.name, file.extension, compactComment)

    fun getCommentStyle(fileName: String, extension: String?, compactComment: Boolean = false): CommentStyle? {
        val ext = getFileExtension(fileName, extension)
        val commentType = when (ext) {
            "env", "yml" -> "property"
            "java", "kt", "css", "scss", "less", "ts", "tsx", "js", "jsx",
            "mjs", "mts", "cjs", "cts", "go", "cpp", "c", "hpp", "h", "cu", "cuh" -> "javascript"
            "php" -> "php"
            "html", "vue" -> "html"
            "py", "pyw", "pyi", "pxd", "pxi", "pyx" -> "python"
            "dart" -> "dart"
            else -> return null
        }

        return when (commentType) {
            "javascript" -> if (compactComment) compactBlockStyle else blockStyle
            "php" -> if (compactComment) compactBlockStyle else phpStyle
            "html" -> htmlStyle
            "property" -> propertyStyle
            "python" -> pythonStyle
            "dart" -> dartStyle
            else -> null
        }
    }

    fun isSupportedFile(file: VirtualFile): Boolean = getCommentStyle(file) != null

    fun getFileExtension(fileName: String, extension: String?): String {
        var ext = extension ?: ""
        if (envFileRegex.matches(fileName)) {
            ext = "env"
        }
        return ext.lowercase()
    }

    fun findProjectForFile(file: VirtualFile): Project? {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            if (project.basePath != null && file.path.startsWith(project.basePath!!)) {
                return project
            }
        }
        return null
    }

    fun getRelativePath(file: VirtualFile): String {
        val project = findProjectForFile(file) ?: return file.path
        val basePath = project.basePath ?: return file.path
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
        return VfsUtilCore.getRelativePath(file, baseDir as VirtualFile) ?: file.path
    }

    fun getFileCreationTime(virtualFile: VirtualFile, timeFormat: String): String {
        val path = Path.of(virtualFile.path)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val creationTime = attributes.creationTime().toInstant()
        val formatter = DateTimeFormatter.ofPattern(timeFormat)
        val localDateTime = LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault())
        return formatter.format(localDateTime)
    }

    fun checkIsFileIgnored(file: VirtualFile, ignorePatterns: List<String>): Boolean {
        if (ignorePatterns.isEmpty()) return false
        val filePath = getRelativePath(file)
        return ignorePatterns.any { pattern ->
            val trimmedPattern = pattern.trim()
            if (trimmedPattern.isEmpty()) return@any false

            val regex = ignoreRegexCache.getOrPut(trimmedPattern) {
                trimmedPattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".")
                    .toRegex()
            }
            regex.matches(filePath)
        }
    }

    fun getProjectDir(file: VirtualFile): Path? {
        val project = findProjectForFile(file) ?: return null
        val basePath = project.basePath ?: return null
        return Path.of(basePath)
    }
}
