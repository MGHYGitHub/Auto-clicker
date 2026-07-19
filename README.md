# ClickFlow · 自动化连点器

ClickFlow 是一个使用 **Java 17 + Swing** 编写的 Windows 桌面自动化工具。它可以按多个坐标顺序执行鼠标动作，也支持录制并回放鼠标、滚轮与键盘操作。项目提供扁平化、Mac 风格的桌面界面，适合个人重复性桌面操作、界面测试和流程演示。

> 请仅在你有权操作的软件、网页和设备上使用本工具，并遵守目标平台的使用规则。

## 功能概览

### 点位任务

- 使用当前鼠标位置创建任务点位，支持自定义名称。
- 点位可上移、下移、单独删除或一键清空。
- 双击点位可编辑独立参数，修改后保存并自动持久化。
- 每个点位都可设置：
  - 开始前延迟；
  - 连续执行间隔；
  - 此点执行次数；
  - 鼠标左键点击、鼠标右键点击、滚轮滚动或键盘快捷键动作；
  - 滚动量，支持 `-10000 ~ 10000` 的精细刻度设置（正数向下、负数向上）；
  - 例如 `Ctrl+C` 的组合快捷键录入。

### 录制与回放

- 录制鼠标移动、按下、释放与滚轮操作。
- 录制键盘按下、释放操作，并保留各动作之间的时间间隔。
- 录制动作列表支持双击编辑、上移、下移和删除。
- 回放时使用实际屏幕坐标，避免高 DPI 环境下出现明显坐标偏移。
- `Alt+Tab`、`Alt+Esc`、`Alt+F4` 与 Windows 键组合属于系统优先快捷键：会正常交给系统执行，但不会被写入录制内容。

### 任务与配置

- 支持“点位任务”和“录制回放”两种执行模式。
- 支持任务循环次数，`0` 表示无限循环。
- 全局设置窗口可配置新点位默认参数，并批量应用到已有点位。
- 自动保存当前配置；支持导入、导出、另存为与导出执行报告。
- 默认配置位于：`%USERPROFILE%\.clickflow\config.properties`。
- 日志文件位于：`%USERPROFILE%\.clickflow\clickflow.log`。

### 界面与应用功能

- Mac 风格的三色窗口控制区、自定义确认框与设置窗口。
- 支持拖动窗口以及通过边缘缩放窗口大小。
- 文件、设置、帮助菜单。
- 帮助菜单提供使用说明、打开日志文件、检查更新。
- 检查更新会读取 GitHub 仓库根目录中的 `version.properties`。

## 快捷键

| 快捷键 | 功能 |
| --- | --- |
| `F3` | 添加当前鼠标坐标为任务点位 |
| `F4` | 启动任务 |
| `F5` | 暂停 / 继续 |
| `F6` | 停止任务 |
| `F7` | 开始 / 停止录制 |

## 快速使用

1. 启动 ClickFlow。
2. 将鼠标移动到第一个目标位置，按 `F3` 添加点位。
3. 重复添加其他点位；需要修改时双击对应点位。
4. 在下方选择“点位任务”或“录制回放”。
5. 点击“启动”或按 `F4` 开始执行；按 `F5` 暂停、按 `F6` 立即停止。

录制模式下，按 `F7` 开始录制，完成鼠标/键盘操作后再次按 `F7` 停止。可在录制区域打开动作编辑器，逐项调整延迟、坐标和滚轮量。

## 普通用户：运行与安装

### 便携版

便携版目录中包含 `ClickFlow.exe`、`app` 和 `runtime`。必须保持完整目录结构，不能只复制其中的 exe。

### Windows 安装器

使用 `jpackage` 制作的 Windows 安装器会内置 Java 运行时。将单个安装器 exe 复制到其他电脑后双击安装即可，目标电脑无需另行安装 Java。

> 未进行代码签名的安装器可能触发 Windows SmartScreen 提示，这是 Windows 对未知发布者的常规提示。

## 开发者：二次开发环境

### 必要工具与版本

| 工具 | 建议版本 | 用途 |
| --- | --- | --- |
| JDK | **17**（项目当前使用 17.0.19） | 编译、运行、`jpackage` 打包 |
| Maven | 3.8+ | 依赖管理、编译与打包 |
| IntelliJ IDEA | 2023.3+，Community 或 Ultimate 均可 | 推荐编辑器 |
| Git | 2.40+ | 版本管理 |
| WiX Toolset | 3.11+（本项目验证过 3.14） | 生成 Windows 安装器 exe |

Windows 安装器构建完成后，WiX 的 `candle.exe` 与 `light.exe` 必须能在 `PATH` 中被找到。

### Maven 依赖

当前唯一业务依赖如下：

| 坐标 | 版本 | 作用 |
| --- | --- | --- |
| `com.github.kwhat:jnativehook` | `2.2.2` | 全局键盘、鼠标、滚轮事件监听 |

`JNativeHook` 负责监听全局输入事件；实际鼠标和键盘回放由 Java AWT 的 `Robot` 完成。

### 使用 IDEA 导入

1. 选择 **File → Open**，打开项目根目录的 `pom.xml`。
2. 在 IDEA 的 Project Structure 中将 Project SDK 设为 **JDK 17**。
3. 等待 Maven 下载依赖并完成索引。
4. 打开 `src/main/java/com/example/autoclicker/AutoClickerApp.java`。
5. 运行 `AutoClickerApp.main()`。

### 命令行编译与运行

在项目根目录执行：

```powershell
mvn clean compile
mvn exec:java
```

如果只需要编译验证：

```powershell
mvn compile
```

首次构建需要网络下载 Maven 插件和依赖；依赖缓存完成后可在大多数情况下离线构建。

## 构建可运行 JAR

项目使用 `maven-shade-plugin` 将依赖合并到可运行 JAR 中：

```powershell
mvn package
```

输出文件：

```text
target\ClickFlow-1.0.0.jar
```

在本机可使用：

```powershell
java -jar target\ClickFlow-1.0.0.jar
```

该 JAR 需要目标电脑已安装 Java 17，因此面向普通用户分发时建议继续制作 Windows 安装器。

## 构建无需 Java 的 Windows 安装器

1. 先执行 `mvn package` 生成 `target\ClickFlow-1.0.0.jar`。
2. 安装 WiX Toolset，并将 WiX 的 `bin` 目录加入 `PATH`。
3. 在项目根目录执行：

```powershell
jpackage --type exe `
  --dest installer `
  --name ClickFlow `
  --input target `
  --main-jar ClickFlow-1.0.0.jar `
  --main-class com.example.autoclicker.AutoClickerApp `
  --app-version 1.0.0 `
  --vendor MGHYGitHub `
  --description "ClickFlow 自动化连点器" `
  --icon src\main\resources\icons\clickflow-icon.ico `
  --win-dir-chooser --win-menu --win-shortcut --win-per-user-install
```

输出安装器：

```text
installer\ClickFlow-1.0.0.exe
```

该 exe 内置 Java 运行时，可单独分发。

## 更新检查与发布流程

应用读取以下地址检查版本：

```text
https://raw.githubusercontent.com/MGHYGitHub/Auto-clicker/main/version.properties
```

发布新版本时，请同步修改：

1. `pom.xml` 中的 `<version>`；
2. `AutoClickerApp.java` 中的 `APP_VERSION`；
3. 根目录 `version.properties` 中的 `version`、`downloadUrl` 与 `notes`；
4. 使用新版本号重新构建 JAR 和安装器；
5. 在 GitHub Releases 上传安装器，并将 `downloadUrl` 指向 Releases 页面或具体下载文件。

`version.properties` 示例：

```properties
version=1.0.1
downloadUrl=https://github.com/MGHYGitHub/Auto-clicker/releases/latest
notes=新增功能说明写在这里。
```

## 项目结构

```text
AutoClicker/
├─ src/
│  └─ main/
│     ├─ java/com/example/autoclicker/AutoClickerApp.java  # 主程序与界面逻辑
│     └─ resources/icons/                                  # 应用图标
├─ pom.xml                                                  # Maven 依赖与构建配置
├─ version.properties                                       # 在线更新版本信息
└─ README.md                                                # 项目文档
```

## 二次开发建议

- 将 `AutoClickerApp` 按 UI、任务调度、配置读写、录制回放拆分为独立类，便于功能继续增长。
- 新增动作类型时，同时更新 `PointActionType`、点位编辑器、执行逻辑与配置序列化/反序列化。
- 新增录制事件时，同时更新 `RecordedActionType`、录制监听、回放逻辑和配置兼容处理。
- 修改配置格式时保留旧格式读取逻辑，避免用户已有配置失效。
- 涉及全局快捷键和 `Robot` 回放的改动，应在多显示器、高 DPI、不同缩放比例环境中手动测试。
- 自动化执行前应始终保留 `F6` 停止机制，避免任务失控。

## 常见问题

### 为什么录制不会写入 Alt+Tab？

`Alt+Tab` 是 Windows 系统级窗口切换快捷键。ClickFlow 会让系统优先执行它，并主动从录制任务中排除，避免回放时误触发窗口切换。

### 为什么滚轮方向与预期相反？

点位滚动量采用 Java `Robot.mouseWheel` 方向：**正数向下，负数向上**。录制动作会记录系统返回的实际滚动量。

### 为什么安装器出现 SmartScreen？

安装器没有商业代码签名证书时，Windows 可能显示未知发布者提示。对外正式发布建议使用受信任的代码签名证书进行签名。
