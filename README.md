# File Description

File Description 是一个 JetBrains IDE 插件，用于自动插入和更新文件头部注释。插件会在创建文件、保存文件或手动触发动作时，根据全局设置和项目级 `.fileDescription.json` 配置生成文件头信息。

## 功能

- 创建新文件时自动添加文件头注释。
- 保存文件时自动更新 `LastEditors`、`LastEditTime` 和 `FilePath`。
- 支持外部工具直接修改文件后的头部信息刷新。
- 支持 Git、SVN、P4 用户名自动检测。
- 支持手动指定作者，覆盖 VCS 自动检测结果。
- 支持项目级 `.fileDescription.json` 配置。
- 兼容 koroFileHeader 风格的 `customMade` 配置。
- 支持右键菜单和快捷键手动生成或更新注释。

## 支持的 IDE

当前插件配置：

- IntelliJ Platform: `2024.1`
- Since Build: `241`
- Until Build: `261.*`

## 支持的文件类型

| 类型 | 扩展名 |
| --- | --- |
| C / C++ / CUDA / Go / Java / Kotlin / JavaScript / TypeScript / CSS | `c`, `cpp`, `h`, `hpp`, `cu`, `cuh`, `go`, `java`, `kt`, `js`, `jsx`, `ts`, `tsx`, `mjs`, `mts`, `cjs`, `cts`, `css`, `scss`, `less` |
| PHP | `php` |
| HTML / Vue | `html`, `vue` |
| Python | `py`, `pyw`, `pyi`, `pxd`, `pxi`, `pyx` |
| Dart | `dart` |
| 配置文件 | `env`, `yml` |

`.env` 和 `.env.*` 文件会按 `env` 类型处理。

## 使用方式

### 自动生成

默认配置下，创建支持的文件类型时会自动插入头部注释。

启用“打开文件时检测并添加注释”后，打开支持的文件类型时，如果文件还没有头部注释，插件会自动插入。

保存文件时，插件会自动更新已有头部注释中的：

- `LastEditors`
- `LastEditTime`
- `FilePath`

如果文件没有头部注释，保存动作不会强制创建；新文件创建和手动动作会负责插入。

### 手动生成或更新

在编辑器或项目树中右键文件，选择：

```text
File Description -> 生成文件头部注释
```

也可以使用默认快捷键：

```text
Ctrl + Alt + I
```

如果文件已经存在插件生成的头部注释，手动动作会更新它；否则会插入新的头部注释。

## 插件设置

打开 IDE 设置：

```text
Settings / Preferences -> Tools -> File Description
```

可配置项：

| 配置 | 说明 |
| --- | --- |
| 创建文件时添加注释 | 新建支持的文件时自动插入头部注释 |
| 打开文件时检测并添加注释 | 打开支持的文件时，如果没有头部注释则自动插入 |
| 保存时自动更新 | 保存文件或检测到外部内容变更时更新已有头部注释 |
| 注释开始简短模式 | 对块注释文件使用 `/*` 开头；关闭时使用 `/**` 开头 |
| 忽略路径 | 一行一条或逗号分隔，支持 `*` 和 `?` 通配符 |
| 时间格式 | Java 时间格式，例如 `yyyy-MM-dd HH:mm:ss` |
| 手动指定作者 | 非空时覆盖 VCS 自动检测用户 |
| 自定义头部注释 | JSON 格式，定义要生成的头部字段 |
| 模板预览 | 使用示例文件和示例用户信息预览当前模板输出 |
| 刷新 VCS 缓存 | 清除 VCS 和项目配置缓存，重新检测用户信息 |

默认忽略路径：

```text
.git/*
.idea/*
.vscode/*
node_modules/*
```

默认头部注释配置：

```json
{
  "Copyright": "Copyright (c) ${now_year} ${git_name}. All rights reserved.",
  "Author": "auto:vcs",
  "Date": "",
  "LastEditors": "auto:vcs",
  "LastEditTime": "",
  "FilePath": "",
  "Description": ""
}
```

## 项目级配置

可以在项目根目录创建 `.fileDescription.json` 覆盖或补充全局设置：

```json
{
  "fileheader": {
    "customMade": {
      "Author": "auto:vcs",
      "Date": "",
      "LastEditors": "auto:vcs",
      "LastEditTime": "",
      "FilePath": "",
      "Description": ""
    },
    "copyright": "Copyright ${now_year} ${git_name_email}",
    "ignorePaths": [
      "dist/*",
      "build/*",
      "generated/*"
    ],
    "timeFormat": "yyyy-MM-dd HH:mm:ss",
    "compactComment": false
  }
}
```

合并规则：

- `customMade` 会覆盖或补充全局头部注释 JSON 中的同名字段。
- `copyright` 非空时优先使用项目级版权配置。
- `ignorePaths` 会追加到全局忽略路径之后。
- `timeFormat` 会覆盖全局时间格式。
- `.fileDescription.json` 会缓存 30 秒；可在设置页点击“刷新 VCS 缓存”清除缓存。

## 字段和占位符

内置字段生成顺序：

```text
Copyright
Author
Date
LastEditors
LastEditTime
FilePath
Description
```

字段值支持：

| 值 | 说明 |
| --- | --- |
| `auto:vcs` | 使用手动作者；如未配置，则读取 Git/SVN/P4 用户信息 |
| `git ...` | 执行指定 Git 命令并使用命令输出 |
| `${now_year}` | 当前年份 |
| `${git_name}` | VCS 用户名 |
| `${git_email}` | VCS 邮箱 |
| `${git_name_email}` | VCS 用户名和邮箱 |

自定义字段规则：

- 字段名首字母为大写时，按 `字段名: 值` 输出。
- 字段名首字母不是大写时，只输出字段值本身。

## 示例

TypeScript 文件示例：

```ts
/**
 * @Copyright: Copyright 2026 whisper3zzz <user@example.com>
 * @Author: whisper3zzz <user@example.com>
 * @Date: 2026-06-09 14:30:00
 * @LastEditors: whisper3zzz <user@example.com>
 * @LastEditTime: 2026-06-09 14:40:00
 * @FilePath: \src\main.ts
 * @Description:
 */
```

Python 文件示例：

```python
"""
 Author: whisper3zzz <user@example.com>
 Date: 2026-06-09 14:30:00
 LastEditors: whisper3zzz <user@example.com>
 LastEditTime: 2026-06-09 14:40:00
 FilePath: \src\main.py
 Description:
"""
```

## 本地开发

### 环境要求

- JDK 17
- Gradle Wrapper
- IntelliJ IDEA 2024.1 或兼容版本

### 常用命令

编译 Kotlin：

```powershell
.\gradlew.bat compileKotlin
```

运行单元测试：

```powershell
.\gradlew.bat test
```

校验插件配置：

```powershell
.\gradlew.bat verifyPluginConfiguration
```

构建插件包：

```powershell
.\gradlew.bat buildPlugin
```

生成的插件包位于：

```text
build/distributions/
```

### 本地安装

在 JetBrains IDE 中打开：

```text
Settings / Preferences -> Plugins -> Install Plugin from Disk...
```

选择 `build/distributions/` 下生成的插件压缩包，安装后重启 IDE。

## GitHub Actions 打包

仓库已配置自动打包流程：

- push 到 `main`：编译、运行测试、校验插件配置并执行 `buildPlugin`。
- pull request 到 `main`：执行同样的测试、编译和打包校验。
- 手动触发 `Build Plugin` workflow：生成插件压缩包。
- 推送 `v*` 标签：生成插件压缩包，并自动创建 GitHub Release。

打包产物会上传到 workflow artifact，文件来自：

```text
build/distributions/*.zip
```

发布新版本示例：

```powershell
git tag v1.0.0
git push origin v1.0.0
```

## 仓库结构

```text
src/main/kotlin/com/whispersong/jetbrains/filedesc/
  actions/      手动生成或更新头部注释的 IDE Action
  config/       插件持久化设置、设置面板、项目级配置读取
  listeners/    文件创建、保存和内容变更监听器
  utils/        注释生成、文件类型判断、VCS 用户检测

src/main/resources/META-INF/plugin.xml
  插件声明、菜单动作、监听器和设置页注册
```
