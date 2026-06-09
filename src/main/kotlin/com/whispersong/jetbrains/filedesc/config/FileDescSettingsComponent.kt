package com.whispersong.jetbrains.filedesc.config

import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.whispersong.jetbrains.filedesc.utils.VcsDetector
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class FileDescSettingsComponent {
    private val state = FileDescSettings.getInstance()

    private val createFileAdd = JBCheckBox("创建文件时添加注释", state.createFileAdd)
    private val openFileCheck = JBCheckBox("打开文件时检测并添加注释", state.openFileCheck)
    private val changeUpdate = JBCheckBox("保存时自动更新", state.changeUpdate)
    private val compactComment = JBCheckBox("注释开始简短模式（/*）", state.compactComment)
    private val ignorePaths = JBTextArea(state.ignorePaths)
    private val timeFormat = JBTextArea(state.timeFormat)
    private val customHeaderComment = JBTextArea(state.customHeaderComment)
    private val manualAuthor = JBTextArea(state.manualAuthor)

    private val refreshVcsBtn = JButton("刷新 VCS 缓存").apply {
        addActionListener {
            VcsDetector.clearCache()
            ProjectConfig.clearCache()
            // Re-detect and show result
            val projectDir = findFirstOpenProjectDir()
            if (projectDir != null) {
                val vcs = VcsDetector.detectVcs(projectDir)
                val user = VcsDetector.getVcsUsernameEmail(vcs, projectDir) ?: VcsDetector.getVcsUsername(vcs, projectDir) ?: "未知"
                vcsStatus.text = "VCS: $vcs | 用户: $user"
            } else {
                vcsStatus.text = "缓存已清除，下次插入注释时重新检测"
            }
        }
    }

    private val vcsStatus = JBLabel(run {
        val projectDir = findFirstOpenProjectDir()
        if (projectDir != null) {
            val vcs = VcsDetector.detectVcs(projectDir)
            val user = VcsDetector.getVcsUsernameEmail(vcs, projectDir) ?: VcsDetector.getVcsUsername(vcs, projectDir) ?: "未知"
            "VCS: $vcs | 用户: $user"
        } else {
            "未检测到项目"
        }
    })

    init {
        customHeaderComment.wrapStyleWord = true
        customHeaderComment.lineWrap = true
        ignorePaths.wrapStyleWord = true
        ignorePaths.lineWrap = true
    }

    private val vcsButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        add(refreshVcsBtn)
        add(vcsStatus)
    }

    val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(TitledSeparator("功能配置"))
        .addComponent(createFileAdd)
        .addComponent(openFileCheck)
        .addComponent(changeUpdate)
        .addComponent(compactComment)
        .addLabeledComponentFillVertically("忽略路径", ignorePaths)
        .addComponent(makeDescription("一行一条，例如 node_modules/* .idea/*"))
        .addLabeledComponentFillVertically("时间格式", timeFormat)
        .addComponent(makeDescription("例如 yyyy-MM-dd HH:mm:ss"))
        .addLabeledComponentFillVertically("手动指定作者", manualAuthor)
        .addComponent(makeDescription("留空则自动从版本控制（Git/SVN/P4）获取用户名，填写则覆盖自动检测"))
        .addComponent(TitledSeparator("VCS 信息"))
        .addComponent(vcsButtonPanel)
        .addComponent(TitledSeparator("头部注释配置"))
        .addLabeledComponentFillVertically("自定义头部注释（JSON）", customHeaderComment)
        .addComponent(makeDescription("JSON 格式，兼容 koroFileHeader 配置。使用 \"auto:vcs\" 自动从版本控制获取用户名"))
        .addComponentFillVertically(JPanel(), 0)
        .setAlignLabelOnRight(true)
        .panel

    private fun makeDescription(text: String): JBLabel {
        return JBLabel(text).apply {
            setComponentStyle(UIUtil.ComponentStyle.SMALL)
            fontColor = UIUtil.FontColor.BRIGHTER
            border = JBUI.Borders.emptyTop(1)
        }
    }

    val isModified: Boolean
        get() = state.openFileCheck != openFileCheck.isSelected ||
                state.createFileAdd != createFileAdd.isSelected ||
                state.changeUpdate != changeUpdate.isSelected ||
                state.compactComment != compactComment.isSelected ||
                state.customHeaderComment != customHeaderComment.text ||
                state.ignorePaths != ignorePaths.text ||
                state.timeFormat != timeFormat.text ||
                state.manualAuthor != manualAuthor.text

    fun apply() {
        if (customHeaderComment.text.isNotEmpty() &&
            ProjectConfig.parseHeaderJson(customHeaderComment.text) == null
        ) {
            throw ConfigurationException("自定义头部注释配置 JSON 格式不正确", "错误")
        }
        state.openFileCheck = openFileCheck.isSelected
        state.createFileAdd = createFileAdd.isSelected
        state.changeUpdate = changeUpdate.isSelected
        state.compactComment = compactComment.isSelected
        state.ignorePaths = ignorePaths.text
        state.timeFormat = timeFormat.text
        state.customHeaderComment = customHeaderComment.text
        state.manualAuthor = manualAuthor.text
    }

    fun reset() {
        openFileCheck.isSelected = state.openFileCheck
        createFileAdd.isSelected = state.createFileAdd
        changeUpdate.isSelected = state.changeUpdate
        compactComment.isSelected = state.compactComment
        ignorePaths.text = state.ignorePaths
        timeFormat.text = state.timeFormat
        customHeaderComment.text = state.customHeaderComment
        manualAuthor.text = state.manualAuthor
    }

    private fun findFirstOpenProjectDir(): java.nio.file.Path? {
        val projects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects
        val basePath = projects.firstOrNull { it.basePath != null }?.basePath ?: return null
        return java.nio.file.Path.of(basePath)
    }
}
