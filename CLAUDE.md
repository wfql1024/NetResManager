# NetResManager — 网络资源管理器

## 项目概述
Windows 桌面应用，管理多个目录中的文件（类似 Windows 资源管理器"库"功能）。用户创建"项目"并添加多个本地路径，软件合并显示这些路径下的内容，支持导出（重命名+移动）、回收（重命名+回收站）、标签管理、操作历史、统计图表。

- **JDK**: 17.0.2 (Oracle)
- **构建**: Maven 3.9.6（通过 `.mvn/wrapper/maven-home/` 手动部署，非 mvnw）
- **平台**: Windows 11，未来可能跨平台（架构已预留扩展点）
- **数据库**: SQLite，位置 `~/.netresmanager/netresmanager.db`
- **用户**: 熟悉 Java/Python，前端经验少，使用 AI 编码助手开发

## 技术架构

```
JavaFX WebView (HTML/CSS/JS)
    ↕ window.javaObject 桥接
JsBridge.java → Service层 → DatabaseManager → SQLite
```

- **UI**: JavaFX 窗口内嵌 WebView，前端 HTML/CSS/JS 从 `src/main/resources/web/` 类路径加载
- **通信**: JS 调用 `window.javaObject.method(args)` → Java 返回 JSON 信封 `{success, data, error}`
- **回收站**: Windows 用 `jna-platform` 的 `ShellAPI.SHFILEOPSTRUCT`；非 Windows 平台降级
- **打包**: `jpackage` 生成 .exe（配置文件 `packaging/jpackage.cfg`）

## 项目结构

```
NetResManager/
├── pom.xml                        # Maven 配置（OpenJFX 17.0.6, JNA 5.14, SQLite 3.45, Gson 2.10）
├── run.bat                        # 生产启动（编译+静默运行）
├── run-dev.bat                    # 开发启动（mvn javafx:run，带控制台）
├── CLAUDE.md                      # 本文档
├── packaging/jpackage.cfg
├── src/main/java/com/netresmanager/
│   ├── Launcher.java              # 启动入口（解决 JavaFX 模块引导问题）
│   ├── MainApp.java               # JavaFX Application，WebView 初始化，JS 桥接注册
│   ├── config/AppConfig.java      # 常量：窗口尺寸(1100×700/900×600)、扫描深度、DB路径
│   ├── db/DatabaseManager.java    # SQLite 连接、Schema 初始化、版本迁移
│   ├── model/
│   │   ├── Project.java           # 项目实体（id,name,paths[],export_dir等）
│   │   ├── FileEntry.java         # 文件/目录条目
│   │   ├── FileEntryGroup.java    # 按源路径分组的文件列表
│   │   ├── OperationRecord.java   # 统一操作记录（导出+回收）
│   │   ├── TagPair.java           # 路径-标签对
│   │   ├── StatEntry.java         # 统计聚合条目
│   │   ├── StatsSummary.java      # 统计摘要
│   │   └── BatchResult.java       # 批量操作结果
│   ├── service/
│   │   ├── ProjectService.java    # 项目 CRUD
│   │   ├── FileScanService.java   # 递归文件扫描（30s 内存缓存，目录优先排序）
│   │   ├── FileOperationService.java  # 导出/回收 + 批量回滚 + 统一 operation_records 写入
│   │   ├── TagService.java        # 标签 CRUD + 级联删除
│   │   └── StatisticsService.java # 统计查询 + 操作历史查询 + 撤回
│   ├── util/
│   │   ├── PathValidator.java     # 路径校验/规范化/类型检测/安全边界检查
│   │   └── JsonUtil.java          # Gson 封装 + JSON 信封响应
│   ├── win32/Shell32RecycleBin.java  # JNA 回收站调用
│   └── bridge/JsBridge.java       # 暴露给 JS 的全部方法（18+ 个）
├── src/main/resources/
│   ├── db/schema.sql              # 最新 DDL（V2，仅作参考）
│   └── db/migrations/001_v1_to_v2.sql  # V1→V2 数据迁移
│   └── web/
│       ├── index.html             # SPA 外壳（Grid 布局）
│       ├── css/main.css, theme.css
│       └── js/
│           ├── bridge.js          # JS 端桥接封装
│           ├── app.js             # 路由、全局状态(NRM.state)、启动
│           ├── lib/chart.umd.min.js  # Chart.js 4.4.0
│           ├── pages/             # manage.js, history.js, statistics.js, settings.js
│           └── components/        # nav-sidebar, project-tabs, breadcrumb,
│                                  # file-table, context-menu, modal-dialog,
│                                  # pie-chart, stats-list
```

## 数据库 Schema (V4)

遵循"可推导则不必要"原则，移除冗余列。`record_id`（格式：`timestamp-uuid-seq`）作为主键，timestamp 可推导操作时间，uuid-seq 标识批次。

### operation_records

| 字段 | 类型 | 说明 |
|------|------|------|
| record_id | TEXT PK | `timestamp-uuid-seq`，含批次和时间信息 |
| project_id | INTEGER FK | 关联 projects |
| operation_type | TEXT | 'export' 或 'recycle' |
| source_path | TEXT | 原始完整路径（→推导 originalName/location） |
| dest_path | TEXT | 目标路径（→推导 newName/location）；空=失败 |
| file_type | TEXT | 文件类型 |
| file_size | INTEGER | 字节数（→推导 fileSizeFormatted） |
| tags_json | TEXT | 操作时标签快照 JSON |
| success_time | TEXT | 操作完成时间 |
| hidden | INTEGER | 隐藏（历史页不显示） |
| exclude_from_stats | INTEGER | 不计入统计 |
| deleted | INTEGER | 软删除标记 |
| rollback_failure_reason | TEXT | 撤销状态：""=未尝试，"success"=成功，其他=失败原因 |

### 迁移链
`001_v1_to_v2.sql` → V2: 合并 export/recycle 表
`002_v2_to_v3.sql` → V3: rollback_failure_reason 语义变更（''/'success'/失败原因）
`003_v3_to_v4.sql` → V4: 移除 id/batch_id/original_name/new_name/operation_time/status，record_id 为主键

`DatabaseManager.initializeSchema()` 流程：① `PRAGMA user_version` → ② `ensureCoreTables()` 硬编码建表 → ③ 按需执行迁移 SQL → ④ 更新版本号。**旧数据永不删除**。

## JS Bridge API

所有方法在 `JsBridge.java` 中，JS 通过 `NRM.bridge.xxx()` 调用。返回 JSON 信封 `{success, data, error}`。

| 分类 | 方法 | 参数 |
|------|------|------|
| 项目 | getAllProjects, getProject, createProject, updateProject, deleteProject | - |
| 扫描 | scanProject, refreshScan | projectId, dir |
| 操作 | exportFiles, recycleFiles | pathsJson, projectId |
| 标签 | addTag, removeTag, getTagsForFile, getAllTags | - |
| 统计 | getExportStatsByType, getExportStatsByTag, getRecycleStatsByType, getRecycleStatsByTag, getStatsSummary | projectId, includeRollback |
| 历史 | getHistory, getHiddenRecords, getDeletedRecords, setRecordHidden, setRecordExcludeFromStats, setRecordDeleted, rollbackRecord | recordId(String) |
| 导出导入 | exportAllRecords, importRecords, saveExportFile, openImportFile | - |
| 工具 | pickDirectory, openFileExplorer, showMessage | - |

## 前端状态管理

`NRM.state`（app.js）：currentPage, currentProjectId, currentDirectory, projects[], files[], selectedFiles(Set)

## 页面功能

| 页面 | 功能 |
|------|------|
| 管理 | "全部"标签：项目列表+CRUD；项目标签：文件浏览/导出/回收/标签 |
| 历史 | 按批次分组展示操作记录；行悬浮显示操作按钮；整行点击选中；顶栏批量操作+导出/导入 |
| 统计 | 按类型/标签饼图+明细表；导出/回收切换；"包含撤销"复选框 |
| 设置 | 双栏布局：记录管理（已隐藏/已删除条目管理）、关于（图标+作者 wfql1024） |

## 已知问题 & 注意事项

1. **JavaFX WebView JS 兼容性**: 不支持 ES2020+ 的 `?.`（可选链）和 `??`，必须用传统 `var el = document.getElementById('id'); if (el) ...` 模式
2. **Emoji 兼容性**: 部分 emoji 在 WebView 中乱码，已替换为 HTML entity
3. **onclick 中 ID 必须加引号**: V4 的 `recordId` 含连字符，onclick 中未加引号会被 JS 当作减法表达式。正确：`onclick="fn('recordId')"` 而非 `onclick="fn(recordId)"`
4. **Maven 编译**: 必须用 `mvn clean compile` 而非 `mvn compile`
5. **统计按标签**: 使用 `operation_records.tags_json` 在 Java 端解析，不 JOIN tags 表
6. **回收功能**: 依赖 Windows Shell32，非 Windows 平台直接返回 false

## 启动方式

```bash
run-dev.bat    # 开发模式（mvn javafx:run，有控制台日志）
run.bat        # 生产模式（自动编译+静默启动）
```

## 当前状态 (2026-06-07)

- [x] 项目管理 CRUD（管理页"全部"标签）
- [x] 文件浏览（递归扫描、面包屑导航、排序、Ctrl/Shift 多选）
- [x] 导出（重命名+移动到导出目录，失败回滚）
- [x] 回收（重命名+移入 Windows 回收站）
- [x] 标签管理（添加/删除/级联清理）
- [x] 操作历史（按批次分组、撤销、隐藏、删除、不计入、详情弹窗、批量操作）
- [x] 统计页面（饼图、明细、摘要、包含撤销）
- [x] 数据库 V4 迁移（"可推导则不必要"原则精简结构）
- [x] 设置页：双栏布局、记录管理（隐藏/删除条目）、关于页
- [x] 数据导出/导入（JSON 文件格式）
- [x] 全页面"全部"标签支持
- [ ] 跨平台回收站降级方案
- [ ] jpackage 正式打包
