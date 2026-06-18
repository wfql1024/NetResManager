<h3 align="center"><img src="https://img.icons8.com/fluency/96/folder-invoices--v1.png" width="72px"><br>NetResManager</h3>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Windows%2011-blue">
  <img src="https://img.shields.io/badge/JDK-17.0.2-orange">
  <img src="https://img.shields.io/badge/JavaFX-17.0.6-blueviolet">
  <img src="https://img.shields.io/github/license/wfql1024/NetResManager"><br>
  <img src="https://img.shields.io/badge/DB-SQLite-brightgreen">
  <img src="https://img.shields.io/badge/Frontend-Vanilla%20JS-yellow">
  <img src="https://img.shields.io/badge/UI-JavaFX%20WebView-lightgrey">
</p>

# 网络资源管理器

一款 Windows 桌面端文件管理工具，类似 Windows 资源管理器的"库"功能。用户创建"项目"并添加多个本地目录路径，软件合并显示这些路径下的文件，支持导出（重命名+移动）、回收（重命名+回收站）、标签管理、操作历史、统计图表等功能。

## 实现原理

- 基于 **JavaFX WebView** 内嵌浏览器渲染前端界面，HTML/CSS/JS 从 classpath 加载
- 通过 `window.javaObject` 桥接模式实现 JS ↔ Java 双向通信（JSON 信封）
- 使用 **JNA** 调用 Windows Shell32 API 实现回收站操作
- **SQLite** 存储项目、标签、操作记录等数据
- 回收站撤销通过解析 `$Recycle.Bin\$I` 元数据文件定位并还原

## 核心功能

- **项目管理**：创建、编辑、删除项目，每个项目可配置多个本地路径
- **文件浏览**：递归扫描目录，按源路径分组展示，面包屑导航，多列排序
- **导出功能**：批量重命名文件并移动到导出目录，失败自动回滚
- **回收功能**：批量重命名文件并移入 Windows 回收站，支持一键撤销还原
- **标签管理**：为文件添加/删除标签，支持批量操作
- **操作历史**：按时间分组展示，支持撤销、隐藏、删除、不计入统计、详情查看
- **统计图表**：按类型/标签的饼图+明细表，导出/回收切换，包含撤销选项
- **数据导出/导入**：JSON 格式，支持导出到文件或从文件导入
- **深色模式**：浅色/深色/跟随系统三种主题
- **跨平台预留**：架构已预留非 Windows 平台的回收站降级方案

## 使用说明

- 首次使用请在"管理 → 全部"页面创建项目，填写名称并添加目录路径
- 选择一个项目标签页即可浏览该项目的文件
- 右键文件可导出、回收、添加标签、打开位置、复制路径
- 双击文件夹可进入子目录，分组头面包屑可快速返回上级或首页
- 历史页面可查看所有操作记录，支持按时间分组查看和撤销
- 统计页面可按类型和标签查看导出/回收的统计图表
- 设置页面可管理已隐藏/已删除的记录，调整外观主题

## 截图

<!-- 可在此处添加应用截图 -->

## 开发相关

### 技术栈

| 层级 | 技术 |
|------|------|
| UI 框架 | JavaFX 17 (WebView) |
| 前端 | Vanilla JS (ES5) + CSS Grid/Flexbox + Chart.js 4.4 |
| 后端 | Java 17, Maven |
| 数据库 | SQLite (via sqlite-jdbc) |
| 系统调用 | JNA 5.14 (Windows Shell32) |
| JSON | Gson 2.10 |

### 启动方式

```bash
run-dev.bat    # 开发模式（mvn javafx:run，有控制台日志）
run.bat        # 生产模式（自动编译 + 静默启动）
```

### 项目结构

```
├─ src/main/java/com/netresmanager/
│  ├─ Launcher.java              # 启动入口（解决 JavaFX 模块引导）
│  ├─ MainApp.java               # JavaFX Application, WebView 初始化
│  ├─ config/AppConfig.java      # 常量配置
│  ├─ db/DatabaseManager.java    # SQLite 连接、Schema 初始化、版本迁移
│  ├─ model/
│  │  ├─ Project.java            # 项目实体
│  │  ├─ FileEntry.java          # 文件/目录条目
│  │  ├─ FileEntryGroup.java     # 按源路径分组的文件列表
│  │  ├─ OperationRecord.java    # 操作记录（V4 可推导原则）
│  │  └─ ...
│  ├─ service/
│  │  ├─ ProjectService.java     # 项目 CRUD
│  │  ├─ FileScanService.java    # 递归文件扫描 + 异步文件夹大小计算
│  │  ├─ FileOperationService.java # 导出/回收/批量回滚
│  │  ├─ TagService.java         # 标签 CRUD + 级联删除
│  │  └─ StatisticsService.java  # 统计查询 + 操作历史
│  ├─ util/                      # 工具类
│  ├─ win32/Shell32RecycleBin.java # JNA 回收站操作 + 还原
│  └─ bridge/JsBridge.java       # JS ↔ Java 桥接（20+ 方法）
├─ src/main/resources/
│  ├─ db/schema.sql              # 数据库 DDL（参考）
│  ├─ db/migrations/             # 数据库迁移脚本
│  └─ web/                       # 前端资源
│     ├─ index.html              # SPA 外壳
│     ├─ css/main.css, theme.css
│     └─ js/
│        ├─ app.js               # 路由、全局状态、启动
│        ├─ bridge.js            # JS 端桥接封装
│        ├─ pages/               # manage, history, statistics, settings
│        └─ components/          # file-table, context-menu, modal, nav-sidebar...
├─ pom.xml
├─ run.bat, run-dev.bat
├─ packaging/jpackage.cfg
├─ CLAUDE.md                     # AI 开发指南
└─ 前端学习指南.md                # 前端技术教学文档
```

## 常见问题

- **回收功能依赖 Windows Shell32**，非 Windows 平台需要实现降级方案（架构已预留接口）
- **JavaFX WebView 不支持 ES2020+ 语法**（如 `?.`, `??`），前端代码需保持 ES5 兼容
- **部分 emoji 在 WebView 中乱码**，已替换为 HTML entity
- 编译时需用 `mvn clean compile` 确保资源正确拷贝
- 数据库位置：`C:\Users\<用户名>\.netresmanager\netresmanager.db`

## 作者

**wfql1024**

## 许可证

MIT License
