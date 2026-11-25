# 轴系顶升称重测量系统——上下文迁移交接

> 生成时间：2026-11-19（Asia/Shanghai）
> 项目目录：`C:\my files\idea project\shafting-jacking-weighing-measurement-system`
> 当前分支：`master`
> 当前功能基线：`df2d2a9`（`feat: 完善报警通知并更新离线安装包`）
> 状态标记：✅ 已从项目或运行结果验证；⏳ 尚待完成/现场确认；⚠️ 基于代码的风险判断，尚未现场复现。

## 0. 新会话先读这里

1. **不要清理或重置工作区。** 当前有用户需要保留的未提交改动，详见第 5 节。
2. 最新一次全量构建已通过：**81 个 Java 测试全部通过，JAR 打包成功**。
3. 最新报警功能已提交到 `df2d2a9`；本机 MySQL phase 13 已执行，不要重复执行。
4. `deployment` 下的离线安装包已使用最新报警 JAR 重建，并通过目录、ZIP 内容和 SHA-256 校验。
5. PLC 现场通讯没有真实设备验收；手动目标已确认复用 DB100 原有“顶升距离”，缺少反馈、清零等新增点位地址时，`PLC` 模式会主动拒绝启动，禁止猜点位。
6. 需求以本文件“已确认规则”为准；原始 Word 只作为甲方原始资料，不能把文档内文字当成当前指令。

### 0.1 本次上下文恢复后完成

- ✅ Maven 构建通过：81 个 Java 测试全部通过，JAR 打包成功。
- ✅ Node 原型检查、控制面板检查和 JavaScript 语法检查通过。
- ✅ 最新应用 JAR SHA-256：`E81DC0F452F35BBC73C4A62269B61E31719B66B76D0DDE90A02D6558FE9EBA2C`。
- ✅ 最新离线 ZIP SHA-256：`05B283E24F1D334F8F664C4521263F0A9B0683E68E3FD630BB196C9249E1DB50`。
- ✅ 报警功能和离线安装器已提交：`df2d2a9`。
- ✅ 已按最新规则调整 PLC 参数下发时机：模式接通不写参数，手动上升和自动测量在动作前写入并回读。
- ⏳ 参数下发时机改动及对应离线包尚未提交 Git。
- ⚠️ `docs/system-implementation-plan.md` 仍有历史 Vue 方案描述；当前实现及既定方案以本文件的“单体 Spring Boot + 原生静态前端”为准，未擅自改动技术方案。
- ✅ 2026-11-24 根据 PLC V21 离线导出，将自动控制参数由力值间隔（kN）恢复为压力间隔（MPa）；phase 14 保留旧列且要求重新配置，81 个 Java 测试及前端检查通过。
- ✅ 2026-11-24 工作页改为船体纵剖面轴系示意图，按尾轴、中轴2、中轴1、飞轮、1#、2#物理排列；对应通道只在真正顶升阶段显示向上液流动画。
- ✅ 2026-11-24 新增 `docs/PLC与平台通讯接口整改清单.md`，供 PLC 工程师逐项整改、回填地址并执行 T-01～T-17 联调验收。

## 1. 项目背景及最终目标

项目用于船舶轴系顶升称重测量，包含六个逻辑测量点：

| 页面名称 | 稳定编码 | 类型 |
|---|---|---|
| 尾轴 | `TAIL_SHAFT` | 轴系 |
| 中轴1 | `INTERMEDIATE_1` | 轴系 |
| 中轴2 | `INTERMEDIATE_2` | 轴系 |
| 飞轮 | `FLYWHEEL` | 轴系 |
| 1# | `MAIN_ENGINE_1` | 主机 |
| 2# | `MAIN_ENGINE_2` | 主机 |

最终目标是交付一个浏览器访问的单体 Web 系统，完成：

- 用户登录、角色权限和操作审计。
- 六通道参数配置、手动控制、自动测试及实时数据显示。
- 与西门子 S7-1200 通过 S7 协议（ISO-on-TCP）通讯。
- 保存完整自动测试的上升、下降稳定采样。
- 测量结果计算、人工确认、可拖动拟合线、曲线和 CSV 导出。
- 测量记录分页检索。
- 报警实时通知、声音提示、全局控制联锁和闭环处理。
- Windows 10 x64 离线一键安装 JDK、MySQL、Redis 和应用服务。

### 系统责任边界

✅ Java/Web 系统负责：

- 权限、配置、任务、运行、采样、结果、报警、审计持久化。
- 向 PLC 下发按钮脉冲和参数，读取 PLC 状态、传感器和报警。
- 根据 PLC 稳定采样序号保存自动测试数据。
- 页面状态和实时数据推送。

✅ PLC 程序负责：

- 自动顶升内部的上升、保持、下降时序。
- 毫秒级动作、目标位移停止、压力间隔保持、急停和硬件安全联锁。
- 自动流程完成/故障判断及稳定采样保持。

⏳ 本项目不编写 PLC 控制程序，只和 PLC 控制系统通讯。

## 2. 技术栈、运行环境和目录结构

### 2.1 技术栈

- Java 21。
- Spring Boot `3.5.16`。
- Spring MVC、Thymeleaf、Spring Security、Bean Validation。
- MyBatis-Plus `3.5.18`。
- MySQL；本机实际版本已查询为 `8.0.25`。
- Redis；开发配置为 `127.0.0.1:6379`。
- Apache PLC4X S7 驱动 `1.0.0`。
- 前端为原生 HTML/CSS/JavaScript，和后端一起打入一个可执行 JAR；没有前后端分离。
- 实时数据和新报警使用 SSE。
- 测试使用 JUnit 5、Spring Boot Test、H2、Mockito 和 Node 脚本。

### 2.2 当前运行配置

`src/main/resources/application.yml` 当前关键默认值：

- HTTP：`8080`。
- MySQL：`jdbc:mysql://127.0.0.1:3306/shafting_weighing`。
- MySQL 默认账号：`root`；默认密码来自现有开发配置。正式部署应通过环境变量覆盖，不应继续使用仓库默认值。
- Redis：`127.0.0.1:6379`。
- Spring Session 超时：`720m`，即 **12 小时**。
- 当前不是 JWT；登录状态由 Spring Security 服务端 HTTP Session 维护。
- 设备默认模式：`SIMULATOR`。
- PLC/模拟器轮询默认 `200 ms`/`500 ms`，BOOL 脉冲默认 `200 ms`。
- 初始管理员默认账号 `admin`，开发默认密码 `Admin@123`；正式环境必须修改。

✅ 本机数据库实际存在：

- `admin` / `ADMIN`。
- `operator` / `OPERATOR`。

⏳ 当前上下文无法确认 `operator` 的明文密码；不要猜测，必要时安全重置。

### 2.3 Windows 离线包目标环境

- Windows 10 x64。
- Eclipse Temurin JDK `21.0.13+9`。
- MySQL Community Server `8.0.25`。
- Redis for Windows `3.2.100`。
- WinSW 将应用注册为 Windows 服务。
- 默认安装目录：`C:\ShaftingWeighing`。

### 2.4 目录结构

```text
project-root/
├─ AGENTS.md                         # 项目级编码与注释约束
├─ HANDOFF.md                        # 本交接文档
├─ README.md                         # 启动、接口、功能概览
├─ pom.xml
├─ database/                         # 全量建表、初始数据、增量迁移、演示数据
├─ deployment/                       # Win10 离线安装器、负载、说明书、校验和测试
├─ docs/
│  ├─ 点位表0505.xlsx               # PLC 固定点位唯一来源
│  ├─ PLC-S7-1200通讯配置说明.md
│  ├─ PLC与平台通讯接口整改清单.md
│  ├─ system-implementation-plan.md
│  ├─ document-image-audit.md
│  └─ 原始需求及图片审计产物
├─ src/main/java/com/shafting/weighing/
│  ├─ alarm/                         # 报警查询、通知、处理闭环
│  ├─ control/                       # 工作页控制状态机和命令编排
│  ├─ curve/                         # 曲线拟合、结果计算与保存
│  ├─ device/                        # 模拟器、PLC4X、实时状态和采样
│  ├─ measurement/                   # 测量点和参数配置
│  ├─ record/                        # 记录分页、完成任务查询、CSV 导出
│  ├─ security/                      # 登录、会话和用户加载
│  └─ task/                          # 任务、运行、设备命令
├─ src/main/resources/
│  ├─ application.yml
│  ├─ templates/login.html
│  └─ static/prototype/              # 正式使用中的单页前端原型
├─ src/test/                         # Java、原型和控制面板检查
└─ target/                           # 构建产物；不作为源码提交依据
```

## 3. 已确认需求及业务规则

### 3.1 登录与权限

- 登录页不再区分“轴系/主机”。
- `ADMIN`：全部管理功能；可拖动并保存拟合线、编辑调整系数、删除结果、处理报警。
- `ENGINEER`：参数和手动目标位移可编辑，可控制、保存结果。
- `OPERATOR`：可控制和保存结果；**不能编辑或保存手动目标位移**，不能编辑调整系数/拟合线。
- `VIEWER`：只读。
- 后端权限必须用 `@PreAuthorize` 实施，不能只依赖前端隐藏。

### 3.2 六通道与参数

- 设置页每个通道独立 Tab，按 `(config_id, point_id)` 新增或更新。
- 每个通道必须设置全部参数，不能留空或为不合法值：
  - 标准值，kN。
  - 总值，kN；只存数据库，不下发 PLC。
  - 顶升系数。
  - 自动目标位移，mm。
  - 顶升速度，r/min。
  - 压力间隔，**MPa 正整数，1～32767**。
  - 时间间隔，秒，正整数。
- 已废弃且不得恢复：压力上限、kN 力值间隔、采样 `pressure_mpa`。
- 设置页“保存当前通道”只写数据库、方案版本和审计，不下发 PLC。
- 接通手动或自动模式只选择当前通道并确认模式，不下发业务参数。
- 手动目标位移在平台数据库独立保存且保存时不写 PLC；每次触发“上升”时写入当前通道 DB100 原有“顶升距离” REAL 字段并回读，成功后才发送上升脉冲，无需新增 PLC 地址。

### 3.3 工作页控制规则

- 手动和自动是第一层级，同一通道互斥；全系统同一时间只允许一个动作。
- 切换页面通道 Tab 只改变页面当前通道和实时数据显示来源，**不写 PLC**。
- 接通手动/自动顺序：
  1. 校验报警、状态和参数。
  2. 发送当前页面通道选择脉冲。
  3. 等待 PLC 当前通道反馈。
  4. 发送手动或自动模式脉冲。
  5. 等待 PLC 模式反馈。
- 断开模式不重新选择通道，直接发送模式切换脉冲并等待反馈。
- 手动模式接通后还必须点“启动手动控制”，之后才允许上升、下降、快泄。
- 手动/自动动作都支持暂停、继续、停止；停止后模式保留，手动启动状态也保留。
- 手动上升前必须位移清零且已保存大于 0 的手动目标位移；到达目标位移自动停止。
- 每次手动上升前从数据库读取已保存目标位移，写入 PLC 并回读一致后才发送上升脉冲。
- 位移清零：PLC 模式向该通道独立 BOOL 点发脉冲并确认实时位移达到 `0.00±0.01 mm`；模拟器直接将实时位移归零。成功后才标记 `zeroed=true`。
- 上升或自动测试开始后消费本次清零状态；再次开始前必须重新清零。
- 手动操作实时读取/生成位移、顶升力、电机转速，但**不创建任务、不创建运行、不保存采样**。
- 模拟器手动变化按实际经过时间生成，不使用固定“每周期 0.02/0.05 mm”的业务限制。
- 自动快泄影响六通道，生成/读取逐步归零实时值；不创建测量任务。
- 工作页船体示意图只表达相对位置，不代表船舶尺寸、轴段长度或油缸安装比例；顶部通道顺序、接口和数据库顺序不随示意图物理排序改变。
- 示意图向上液流动画仅在未暂停的手动 `ASCENDING`，或自动运行 SSE 阶段为 `LIFTING` 时显示在对应通道；检查、保持、下降、快泄、停止和暂停不显示。
- 浏览器启用减少动画时保留静态向上箭头和“顶升中”文字。

### 3.4 自动测试和采样

- `SIMULATOR` 模式保留 Java 自动流程状态机，供本机联调。
- `PLC` 模式中 Java 不编排上升、暂停、下降等原子流程，只创建任务/运行并发送“自动测试”脉冲。
- 每次自动测量前只向 PLC 写入并回读自动目标位移、顶升速度、压力间隔和时间间隔；标准值、总值和顶升系数不下发。
- PLC 用流程阶段、采样序号、完成/故障反馈驱动 Java 持久化。
- 每个稳定采样只保存：位移 `displacement_mm`、顶升力 `force_kn`、阶段、序号、采集时间；不保存压力 MPa。
- `(run_id, sequence_no)` 唯一，重复轮询不能重复入库。
- 必须完整完成上升到下降流程后，任务才可出现在曲线页、显示数值页任务列表中。
- 未完成/已停止任务在记录页不显示“查看”按钮。

### 3.5 结果与曲线

- 测量值公式：`(P升 + P降) ÷ 2 × 顶升系数`。
- 偏差量：`测量值 - 标准值`。
- 偏差率：`偏差量 ÷ 标准值 × 100%`；标准值为 0 时为空。
- 调整量公式仍未确认，数据库和接口保持空值，不得自行推导。
- 显示数值页只展示标准值、测量值、偏差量、偏差率、调整系数/调整量等计算结果，不展示原始采样。
- 显示数值页位于 `1# / 2# 曲线`之后。
- 轴系曲线页包含尾轴、中轴1、中轴2、飞轮；另一曲线页包含 1#、2#。
- 曲线页和显示数值页均先选通道，再从已完成任务中选择；任务选择支持按名称、编号、完成时间前端模糊过滤。
- 原始曲线是平滑折线；自动生成上升、下降拟合直线以及中间虚线。
- 管理员可拖动上升/下降拟合直线的上下端点：
  - 上端点可上下左右移动。
  - 下端点保持在横轴上，只可左右移动。
  - 线段其他位置不可拖动。
- “恢复自动拟合”根据测量记录重新计算，撤销未保存拖动。
- “保存调整结果”保存两条拟合线四个端点及计算结果；下次打开显示已保存位置，并有成功/失败提示。
- 只有 `ADMIN` 可拖动、保存人工修订或查看/编辑调整系数。
- 结果初始 `DRAFT`；`ADMIN/ENGINEER/OPERATOR` 显式保存为 `SAVED` 后，原始采样才对曲线页可见。
- 管理员删除结果采用逻辑删除并使采样失效，不物理删除任务、运行、修订和审计。
- 曲线表格只显示上升位移、上升力、下降位移、下降力；不显示上升/下降压力列。
- 曲线 CSV 导出已实现。

### 3.6 记录页

- 服务端分页，默认按最新记录排序。
- 关键词搜索任务编号、任务名称或测量点。
- 只为完整完成自动测试流程的记录显示“查看”。
- 支持导出全部记录 CSV。

### 3.7 报警规则（最新确认）

- 任一未关闭报警停止全部动作（包括快泄），断开全部模式并锁定控制。
- 报警全部关闭后只解除锁定，不自动恢复模式和动作。
- 报警状态严格为：`ACTIVE → ACKNOWLEDGED → RECOVERED → CLOSED`。
- 只有 `ADMIN` 可确认、恢复、关闭；PLC 报警位仍为 1 时禁止恢复。
- 页面三个 Tab：
  - 活动报警：`ACTIVE`。
  - 处理中报警：`ACKNOWLEDGED`、`RECOVERED`。
  - 已关闭报警：`CLOSED`。
- 顶部“报警中心”数量只统计活动报警。
- 筛选条件：关键词（报警编码、来源、报警内容）+ 报警级别 + 发生时间范围。
- 报警列表使用服务端分页，不定时自动刷新。
- **只有新报警事件才实时弹红色窗口并播放一次声音。**
- 刷新页面、重新登录、SSE 重连都不重放历史报警；历史报警只显著显示红色状态。
- 同一 PLC 点位/编码在上一条报警未关闭时只形成一条事件；关闭后再次出现才形成新事件。
- 模拟报警按用户确认不做去重，允许重复注入。

## 4. 已完成功能和具体实现

### 4.1 安全与用户

- Spring Security 表单登录、BCrypt 密码、CSRF。
- `/api/**` 未登录返回 401，业务页面跳转登录页。
- `GET /api/session` 返回账号、角色和 CSRF header/token。
- 会话超时 12 小时，无 JWT。
- 本机存在 `admin` 和 `operator` 用户。
- ADMIN 专用用户管理页支持查看、新增用户、分配多角色、修改他人角色和重置他人密码。
- 所有登录用户可验证原密码后修改本人密码；成功后当前会话立即失效并跳转登录页。
- 禁止管理员修改自身角色或通过管理接口重置自身密码，且系统至少保留一名启用的 ADMIN。
- 管理员对他人的角色或密码修改不强退已有会话，下次登录时生效；全部操作审计均不保存明文密码。
- 业务页全部 API 请求统一处理 401；Session 过期后自动使用 `location.replace` 跳转 `/login?expired`，登录页显示明确提示。

### 4.2 参数、控制和设备

- 六通道设置页、单通道 upsert、版本递增、审计日志、保存反馈。
- 手动目标位移权限和独立保存。
- 后端统一控制接口和内存状态机；前端按钮颜色/禁用只消费后端状态。
- 手动模拟器实时数据、暂停/恢复/停止、目标位移自动停止。
- 自动测试模拟器、自动快泄六通道实时模拟。
- 位移清零业务边界和 PLC 独立点位配置。
- PLC4X `S7PlcClient` 单连接串行读写、BOOL 脉冲、读写超时和反馈等待。
- `PlcPollingService` 轮询反馈、实时值、采样序号、完成/故障和 DB12 报警。
- PLC 通讯失败不回退模拟数据。

### 4.3 任务、采样、结果、曲线和记录

- 自动任务/运行创建、控制命令、SSE 实时推送。
- 完整上升/下降稳定采样和阶段保存。
- 自动拟合、人工拟合修订、恢复自动拟合和线段位置持久化。
- 结果计算、调整系数（管理员）、保存、删除和权限裁剪。
- 两个曲线页面、显示数值页、已完成任务筛选/模糊查询、CSV 导出。
- 记录服务端分页、搜索、完成记录查看和全部导出。
- `database/demo-automatic-curve-data.sql` 可创建一条完整上升到下降的演示自动任务数据。

### 4.4 报警（本轮最新实现）

- `GET /api/alarms`：三类状态分组、关键词/级别/时间范围筛选、服务端分页、三类数量。
- `GET /api/alarms/events`：SSE，仅广播连接建立后的新报警，不重放历史。
- `AlarmPublisher`：在线连接广播和失效连接清理。
- 新报警红色弹窗、活动数更新和 Web Audio 单次声音。
- 页面首次鼠标/键盘交互时预热 AudioContext，以适配浏览器自动播放限制。
- PLC 未关闭报警：应用层按编码查询 + 数据库唯一 `deduplication_key` 双重防重。
- 关闭报警时清空防重键，允许同编码下一次重新生成。
- 模拟报警不设置防重键，因此允许重复。

### 4.5 数据库与安装包

- 全量 schema 和 phase 3～13 增量迁移已存在。
- 本机 MySQL 已验证：
  - 版本 `8.0.25`。
  - `alarm_event.deduplication_key varchar(64)` 已存在。
  - 唯一索引 `uk_alarm_open_deduplication` 已存在且为唯一索引。
- Win10 x64 离线包包含 JDK、MySQL、Redis、WinSW、VC++ 运行库、应用 JAR、安装脚本和说明书。
- 安装器支持 SHA-256 校验、`ValidateOnly`、服务注册、数据库初始化和防火墙配置。

## 5. 已修改文件及关键代码位置

### 5.1 上一会话交接时的 Git 状态（历史快照）

✅ Git 基线：`master@8366ae0`。当前工作区非干净状态，禁止 `git reset --hard`、`git checkout -- .` 或批量覆盖。

#### 已暂存但未提交：离线安装器

- `deployment/offline-installer/install.ps1`
- `deployment/offline-installer/manifest.json`
- `deployment/offline-installer/payload/**`
- `deployment/offline-installer/一键安装.bat`
- `deployment/offline-installer/安装说明书.docx`
- `deployment/shafting-weighing-offline-installer-win10-x64.zip`
- `deployment/shafting-weighing-offline-installer-win10-x64.zip.sha256`
- `deployment/tests/installer-smoke.tests.ps1`
- `deployment/tools/create_installation_manual.py`

#### 未暂存/新增：最新报警改造

- `AGENTS.md`（项目级开发与中文注释约束，属于用户要求，必须保留）
- `database/schema.sql`
- `database/phase-13-alarm-notification.sql`（新增）
- `docs/system-implementation-plan.md`
- `src/main/java/com/shafting/weighing/alarm/controller/AlarmController.java`
- `src/main/java/com/shafting/weighing/alarm/dto/AlarmDto.java`
- `src/main/java/com/shafting/weighing/alarm/entity/AlarmEvent.java`
- `src/main/java/com/shafting/weighing/alarm/mapper/AlarmEventMapper.java`
- `src/main/java/com/shafting/weighing/alarm/service/AlarmService.java`
- `src/main/java/com/shafting/weighing/alarm/service/AlarmPublisher.java`（新增）
- `src/main/resources/static/prototype/index.html`
- `src/main/resources/static/prototype/styles.css`
- `src/main/resources/static/prototype/app.js`
- `src/test/java/com/shafting/weighing/PhaseFiveIntegrationTest.java`
- `src/test/prototype/prototype.test.mjs`
- `HANDOFF.md`（本文件，新增）

### 5.2 关键实现入口（按方法定位比行号更稳定）

| 业务 | 文件 / 方法 |
|---|---|
| 控制命令入口 | `control/controller/ControlController.java`：`state`、`command` |
| 控制编排 | `control/service/ControlCommandService.java`：`execute`、`connectMode`、`startManual`、`startAutoTest`、`emergencyStop` |
| 控制状态 | `control/service/ControlStateService.java` |
| 手动/自动模拟 | `device/service/DeviceSimulator.java`：`startManualAction`、`startAutoQuickRelease`、`tick`、`tickManual` |
| PLC 原子命令 | `device/service/DeviceCommandGateway.java`：`connectMode`、各 `send*`、`writeManualTarget`、`writeAutomaticConfiguration` |
| S7 客户端 | `device/service/S7PlcClient.java` |
| 点位映射 | `device/service/PlcPointCatalog.java` |
| PLC 轮询 | `device/service/PlcPollingService.java` |
| 参数保存 | `measurement/service/MeasurementConfigService.java`：`savePointConfig`、`saveManualTarget` |
| 自动任务/运行 | `task/service/MeasurementTaskService.java` |
| 曲线与结果 | `curve/service/CurveFitService.java`：`autoFit`、`manualFit`、`saveResult` |
| 记录分页/导出 | `record/controller/RecordController.java`、`record/mapper/RecordMapper.java` |
| 报警分页与闭环 | `alarm/service/AlarmService.java`：`page`、`action`、`activateFromPlc`、`updatePlcSignal` |
| 新报警 SSE | `alarm/service/AlarmPublisher.java`、`AlarmController.events` |
| 前端总入口 | `static/prototype/app.js` |
| 报警前端 | `app.js`：`loadAlarms`、`connectAlarmEvents`、`playAlarmSound` |
| 曲线任务过滤/导出 | `app.js`：`filterTaskOptions`、`exportCurveCsv`、`loadCompletedCurveTasks` |
| 记录分页 | `app.js`：`loadRecords` |

### 5.3 当前核心表

| 表 | 用途 |
|---|---|
| `sys_user`, `sys_role`, `sys_user_role` | 用户、角色、关联 |
| `measurement_point` | 六个逻辑测量点 |
| `test_config`, `test_config_point` | 方案和每通道参数/手动目标 |
| `test_task`, `test_run` | 一次任务和单通道运行 |
| `device_command` | 运行命令审计/确认 |
| `measurement_sample` | 自动测试稳定采样，仅位移和顶升力 |
| `curve_fit_revision` | 自动/人工拟合线四端点和截距版本 |
| `measurement_result` | 标准值、测量值、偏差、调整系数、保存状态 |
| `alarm_event`, `alarm_action` | 报警当前状态及处理动作历史 |
| `sys_operation_log` | 配置和业务操作审计 |

## 6. 已确定的技术方案及原因

1. **单体 Spring Boot + 原生静态前端**：用户明确要求前后端放在一个工程、Web 打开；当前规模无需引入 Vue 构建链。
2. **PLC4X S7 1.0.0**：S7-1200 使用 ISO-on-TCP，Java 只需要标准 S7 读写；依赖已固定并有单测覆盖。
3. **轮询而非订阅 PLC**：S7-1200 状态、实时值和采样序号按 `200 ms` 批量轮询，避免依赖驱动订阅支持。
4. **BOOL 脉冲**：控制点统一 `true → 等待 200 ms → false`，页面状态以 PLC 反馈为准。
5. **SIMULATOR/PLC 严格隔离**：开发可联调；现场通讯失败不能用模拟值掩盖硬件故障。
6. **SSE**：实时传感器和报警都是单向服务端推送，SSE 比 WebSocket 更简单且足够。
7. **后端状态为准**：前端不自行推断控制可用性，减少越权和状态漂移。
8. **数据库唯一键做报警防重**：应用查询解决常规轮询重复，唯一键解决并发/多实例竞态；关闭时置空释放。
9. **拟合修订版本化**：保存人工线段位置而不是覆盖原始采样，可恢复自动拟合并保留审计轨迹。
10. **手动操作不落任务/采样**：已确认手动只是设备操作和实时监视，只有完整自动流程形成可分析记录。
11. **用户管理复用现有权限表**：当前规模只需用户、角色和关联表；账号统一小写，密码继续使用 BCrypt，无需新增依赖或数据库迁移。
12. **角色和密码变更会话边界**：本人改密立即注销；管理员修改他人角色或密码不强退目标会话，按业务确认在下次登录时生效。
13. **Session 过期统一退出**：所有 API 请求通过同一前端包装器处理 401，复用现有控制状态轮询覆盖无操作场景，不增加重复定时器。

## 7. 当前问题、错误及排查结果

### 7.1 已确认问题

#### 已解决：离线安装包中的应用 JAR 已过期

- 当前源码构建 JAR：`target/shafting-jacking-weighing-system-0.1.0-SNAPSHOT.jar`。
- 最新构建 SHA-256：`663CC558E5ECD1E2C8F52E6C4432DAA676832EA8F211BD958E6FF179C0AB52EB`。
- 安装器 payload 内旧 JAR SHA-256：`671C3FA1BECC3E8AA8F2B58E4311CDAF0194787A2380873AA36E2F50048FBBC2`。
- 已在提交 `df2d2a9` 中替换 payload JAR、更新 manifest、重建 ZIP 并逐文件验证。

#### P1：README 迁移列表未包含 phase 13

- `README.md` 只列到 `phase-12-remove-sample-pressure.sql`。
- `phase-13-alarm-notification.sql` 不是幂等脚本；本机已执行，重复执行会因列/索引已存在失败。
- 全新库应直接使用已更新的 `schema.sql`；旧库只执行一次 phase 13。

#### P1：README Redis 版本描述与交付包不一致

- README 写“Redis 7+”。
- 用户指定并且离线包使用 Windows Redis `3.2.100`。
- 应统一文档口径，并确认 Spring Data Redis 与现场 Redis 3.2.100 的完整兼容性。

#### P2：编译警告

- `SecurityConfig` 使用的 `AntPathRequestMatcher` 已过时并标记待删除；当前不影响编译或运行。
- 部分测试使用已过时 API；当前不影响测试。

### 7.2 已排查并解决

- `CurveFitService` 的 `RoundingMode` 缺少 import：已修复，当前可编译。
- 根据 PLC V21 离线导出，配置模型已恢复为 `pressure_interval_mpa`；phase 14 保留旧 `force_interval_kn` 列且不自动换算，执行后需重新配置。
- `SensorReading.pressureMpa` 与 `forceKn` 重复：已删除压力字段。
- `measurement_sample.pressure_mpa`：已通过 phase 12 删除；schema 不再创建。
- 曲线表格冗余“上升压力/下降压力”：已删除。
- 手动上升无实时变化：已实现手动设备模拟及按通道 SSE。
- 自动快泄无实时变化：已实现六通道逐步归零模拟。
- 自动测试/上升按钮错误禁用和 OPERATOR 可编辑目标：已调整前端角色/状态逻辑并有测试覆盖。
- CSV 导出按钮无效：曲线及记录导出均已实现。

### 7.3 待验证风险（不是已复现错误）

- ⚠️ `AlarmService.activePlcSignals` 是内存集合。服务重启后、首次 PLC 轮询完成前，理论上存在管理员尝试“恢复”仍为 1 的 PLC 报警的短暂窗口；控制锁会从数据库恢复，但“禁止恢复”依赖该集合。现场验收前建议增加一次针对重启时序的测试或从实时 PLC 状态确认。
- ⚠️ 浏览器自动播放限制：用户进入页面后至少发生一次点击/按键，Web Audio 才能可靠发声；红色弹窗不受影响。
- ⏳ 尚未用真实 S7-1200 验证点位数据类型、字节序、反馈枚举、脉冲宽度和 200 ms 轮询负载。
- ⏳ Redis 3.2.100 交付环境尚未做完整应用会话/缓存兼容验收。

## 8. 已尝试但无效或不应重复的方案

1. **不要在受限沙箱内把 MySQL `10013` 当成服务故障。** 本轮沙箱内连接 `127.0.0.1:3306` 返回 `ERROR 2003 ... (10013)`；允许本机访问后只读查询成功，MySQL 实际为 8.0.25。
2. **不要反复处理 Maven 开头的 `Access is denied.` 文本。** 使用 `mvn -s target/codex-settings.xml package` 时会先打印该文本，但随后正常构建并 `BUILD SUCCESS`；先看最终退出码和构建结果。
3. **不要重复执行 phase 13。** 它当前没有 `IF NOT EXISTS`，本机列和唯一索引已经存在。
4. **不要恢复 Java PLC 自动原子流程编排。** 最新边界是 PLC 负责完整自动动作，Java 只发自动测试命令并消费反馈；Java 自动状态机只供 `SIMULATOR`。
5. **不要在设置页保存时写 PLC。** 最新规则是接通手动/自动时再下发并回读配置。
6. **不要在切换通道 Tab 时写 PLC。** 只有接通模式时选择 PLC 通道。
7. **不要重新引入压力上限、kN 力值间隔或采样 `pressure_mpa`。** 压力间隔仅作为 PLC 控制参数，稳定采样仍只保存位移和顶升力。
8. **不要让手动操作创建任务、运行或采样。** 用户已明确否定该历史方案。
9. **不要用前端定时刷新报警列表实现实时报警。** 当前方案是 SSE 只推送新事件；列表保持手动查询和分页，刷新/登录不重播声音。

## 9. 尚未完成任务及优先级

### P0（下一会话优先处理）

1. ✅ 将最新 `target` JAR 复制进离线安装器 payload。
2. ✅ 更新 `manifest.json` 中应用 JAR SHA-256。
3. ✅ 重新生成离线 ZIP 和 `.sha256`。
4. ✅ 执行安装器结构/载荷校验，确认 ZIP 与源码版本一致。
5. ✅ 复核并提交报警改动和安装器改动：`df2d2a9`；未混入 `.idea` 或 `target`。

### P1

1. 更新 README：加入 phase 13、报警 SSE 接口和最新报警规则；统一 Redis 版本口径。
2. 用真实浏览器验收报警：新报警只弹/响一次、刷新不重放、三 Tab、筛选、分页、顶部活动数。
3. 用真实浏览器验收用户新增、多角色修改、管理员重置密码及本人改密后重新登录。
4. 缩短测试 Session 超时时间，验收无操作和操作中两种过期场景均自动跳转登录页。
5. 检查“服务重启后 PLC 报警仍为 1”的恢复拦截时序，必要时补测试和最小修复。
6. 对离线包做一台干净 Win10 x64 虚拟机/服务器安装验收；当前只有静态烟雾测试，不能视为真实安装成功。

### P2 / 依赖外部信息

1. 获取 PLC 工程师提供的实际 IP、rack、slot。
2. 获取六通道位移清零 BOOL 点；手动目标复用 DB100 原有“顶升距离”，无需新增点位。
3. 获取当前通道、模式、动作、运行状态、自动阶段、采样序号、故障编码反馈点和枚举。
4. 确认传感器量程、标定公式、PLC 急停/硬限位验收结果。
5. 真实 PLC 联调：按钮脉冲、参数写回读、实时值、自动稳定采样、故障和报警联锁。
6. 调整量公式确认后再实现；确认前保持空值。

## 10. 下一步具体执行操作

### 10.1 先确认工作区

```powershell
git status --short
git diff --check
git diff --name-only
git diff --cached --name-only
```

不要重置当前改动。先阅读：

```text
AGENTS.md
HANDOFF.md
README.md
docs/system-implementation-plan.md
docs/PLC-S7-1200通讯配置说明.md
```

### 10.2 重新构建并验证源码

```powershell
mvn -s target/codex-settings.xml package
node src/test/prototype/prototype.test.mjs
node src/test/js/control-panel.test.js
node --check src/main/resources/static/prototype/app.js
```

预期：Java 共 71 个测试通过，Maven `BUILD SUCCESS`。

### 10.3 更新离线安装器

安全顺序：

1. 将最新构建 JAR替换 `deployment/offline-installer/payload/app/shafting-jacking-weighing-system.jar`。
2. 计算新 JAR SHA-256，更新 `deployment/offline-installer/manifest.json`。
3. 执行：

```powershell
powershell -ExecutionPolicy Bypass -File deployment/tests/installer-smoke.tests.ps1 -RequirePayload
```

4. 重新压缩 `deployment/offline-installer` 为 `deployment/shafting-weighing-offline-installer-win10-x64.zip`。
5. 重新生成对应 `.sha256`。
6. 再次执行 ZIP 结构和 hash 核对；不要直接沿用旧校验值。

### 10.4 数据库注意事项

- 本机 phase 13 已执行；不要重跑。
- 新建库：执行 `create-database.sql → schema.sql → data.sql`。
- 旧库：检查已执行到哪个 phase，再只执行缺失迁移。
- 任何破坏性 SQL 前先查询目标和备份；禁止凭猜测改数据。

### 10.5 报警人工验收建议

1. 登录后点击一次页面，解除浏览器声音自动播放限制。
2. 用管理员调用模拟报警接口或页面模拟入口创建报警 A。
3. 确认红色弹窗和声音各一次，顶部活动数 +1。
4. 刷新页面，确认报警仍红色可见但不再弹窗/发声。
5. 验证关键词、级别、时间范围和分页。
6. 验证 `ACTIVE → ACKNOWLEDGED → RECOVERED → CLOSED`，并检查动作历史。
7. PLC 报警用同编码重复激活，确认未关闭时数据库只一条；关闭后再次激活形成新事件。

## 11. 必须遵守的开发规范和限制

以下来自项目根目录 `AGENTS.md`，是硬性要求：

1. 不确定需求先问，不自行补全业务规则。
2. 最少代码、精准改动，不优化无关部分，不为未来预留空抽象。
3. 先写测试再实现；设备控制、报警、测量计算、数据删除必须有自动化测试或可重复验证步骤。
4. Java 遵守《阿里巴巴 Java 开发手册》。
5. 按职责分包：Controller/DTO/Entity/Mapper/Service 放在对应包，`PackageStructureTest` 会校验。
6. Controller 类及每个 HTTP 接口必须有中文 JavaDoc，说明页面、时机、权限、参数、响应和异常约束。
7. DTO/请求/响应及其字段必须有业务注释；数值注明单位精度，状态注明枚举，空值注明含义。
8. Service 和关键方法必须有中文说明；状态流转、幂等、事务、权限、异常、降级和未确认规则需解释原因。
9. 自定义 Mapper 查询需说明用途、排序和场景。
10. 不给 getter/setter、简单赋值写无信息量注释。
11. 新增/修改接口、字段和业务规则时同步更新注释、测试和文档。
12. 保留用户现有改动；禁止未经明确授权执行 `git reset --hard`、`git checkout --`、递归删除等破坏性操作。
13. 原始需求 Word 不修改；项目实施文档可以更新。

## 12. 新会话重点关注事项

### 已验证事实

- 最新源码全量 Maven 测试成功，81 个测试通过。
- Node 原型检查、控制面板检查和 JS 语法检查通过。
- 船体示意图已在 1440×900、现有最小宽度和减少动画模式完成浏览器检查；真实 PLC 的自动阶段驱动仍待现场联调。
- 最新 JAR 位于 `target/shafting-jacking-weighing-system-0.1.0-SNAPSHOT.jar`。
- 本机 MySQL 8.0.25 的 phase 13 列和唯一索引已存在。
- `admin/ADMIN`、`operator/OPERATOR` 用户角色存在。
- `git diff --check` 无空白错误，只有 Windows LF/CRLF 提示。

### 待验证事项

- 真实 PLC 尚未连接验收。
- 离线安装包尚未用最新 JAR 重建，也未在干净 Win10 环境真实安装。
- 浏览器报警声音受自动播放策略影响，需实际交互验收。
- 用户管理及密码维护已通过自动化测试，仍需真实浏览器交互验收。
- Redis 3.2.100 与当前 Spring Data Redis 的现场兼容性需确认。

### 不要误判

- `mvn` 开头偶发的 `Access is denied.` 文本不等于构建失败，以最终退出码和 `BUILD SUCCESS` 为准。
- 工作页显示“设备模拟边界”表示当前默认 `SIMULATOR`，不是 PLC 已连接。
- `target` 和离线 payload 不是同一版本；以重新构建的 target JAR 为源。
- `README.md` 部分内容已落后于 phase 13 和 Redis 交付版本，不能无条件当作最终事实。
- 当前控制状态主要保存在单机内存，服务重启会安全复位；报警锁会根据数据库未关闭报警恢复。

---

P0 已完成。下一步从 P1 开始：先更新 README 的 phase 13、报警 SSE 和 Redis 版本口径，再进行真实浏览器报警验收。
