package com.whispersong.jetbrains.filedesc.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class VcsType { GIT, SVN, P4, NONE }

object VcsDetector {

    private val vcsCache = ConcurrentHashMap<String, VcsType>()
    private val usernameCache = ConcurrentHashMap<String, String>()
    private val usernameEmailCache = ConcurrentHashMap<String, String>()

    fun detectVcs(projectDir: Path): VcsType {
        return vcsCache.getOrPut(projectDir.toString()) {
            detectVcsInternal(projectDir)
        }
    }

    fun getVcsUsername(vcs: VcsType, projectDir: Path? = null): String? {
        val key = cacheKey(vcs, projectDir)
        usernameCache[key]?.let { return it }

        val value = when (vcs) {
            VcsType.GIT -> getGitUsername(projectDir)
            VcsType.SVN -> getSvnUsername(projectDir)
            VcsType.P4 -> getP4Username(projectDir)
            VcsType.NONE -> getSystemUsername()
        } ?: return null

        usernameCache[key] = value
        return value
    }

    fun getVcsUsernameEmail(vcs: VcsType, projectDir: Path? = null): String? {
        val key = cacheKey(vcs, projectDir)
        usernameEmailCache[key]?.let { return it }

        val value = when (vcs) {
            VcsType.GIT -> getGitUsernameEmail(projectDir)
            VcsType.SVN -> getSvnUsername(projectDir)
            VcsType.P4 -> getP4Username(projectDir)
            VcsType.NONE -> getSystemUsername()
        } ?: return null

        usernameEmailCache[key] = value
        return value
    }

    /** Clear all caches, forces re-detection on next access */
    fun clearCache() {
        vcsCache.clear()
        usernameCache.clear()
        usernameEmailCache.clear()
    }

    private fun detectVcsInternal(projectDir: Path): VcsType {
        if (Files.isDirectory(projectDir.resolve(".git"))) return VcsType.GIT
        if (Files.isDirectory(projectDir.resolve(".svn"))) return VcsType.SVN
        if (runCommand(projectDir, "p4", "info") != null) return VcsType.P4
        return VcsType.NONE
    }

    private fun getGitUsername(projectDir: Path?): String? =
        runCommand(projectDir, "git", "config", "user.name")

    private fun getGitUsernameEmail(projectDir: Path?): String? {
        val name = runCommand(projectDir, "git", "config", "user.name") ?: return null
        val email = runCommand(projectDir, "git", "config", "user.email")
        return if (email != null) "$name $email" else name
    }

    private fun getSvnUsername(projectDir: Path?): String? {
        val info = runCommand(projectDir, "svn", "info") ?: return null
        for (line in info.lines()) {
            if (line.startsWith("Last Changed Author:")) {
                return line.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun getP4Username(projectDir: Path?): String? {
        val info = runCommand(projectDir, "p4", "info") ?: return null
        for (line in info.lines()) {
            if (line.startsWith("User name:")) {
                return line.substringAfter(":").trim()
            }
        }
        return null
    }

    private fun getSystemUsername(): String? =
        System.getenv("USERNAME") ?: System.getenv("USER")

    private fun cacheKey(vcs: VcsType, projectDir: Path?): String {
        val path = projectDir?.toAbsolutePath()?.normalize()?.toString() ?: "<global>"
        return "$path|$vcs"
    }

    private fun runCommand(projectDir: Path?, vararg command: String): String? {
        return try {
            val processBuilder = ProcessBuilder(*command)
            if (projectDir != null && Files.isDirectory(projectDir)) {
                processBuilder.directory(projectDir.toFile())
            }
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            reader.close()
            if (output.isEmpty() || process.exitValue() != 0) null else output
        } catch (e: Exception) {
            null
        }
    }
}
