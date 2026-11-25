# 轴系顶升称重测量系统

系统支持 `SIMULATOR` 本机联调模式和 `PLC` 现场模式。现场模式已接入 Apache PLC4X S7 驱动，通过 ISO-on-TCP 与 S7-1200 通讯；Java 负责按钮脉冲、动作开始前的参数写入回读、状态和传感器轮询、稳定采样保存及报警闭环，自动顶升动作时序和硬件安全联锁由 PLC 程序负责。

## 后端包结构

后端按业务模块组织，每个模块内部再按职责分包。当前业务模块包括 `alarm`、`control`、`curve`、`device`、`measurement`、`record`、`security` 和 `task`。

```text
com.shafting.weighing
├─ config/                 # 全局 Spring 配置
├─ {业务模块}/
│  ├─ controller/         # HTTP 接口与页面入口
│  ├─ dto/                # 请求、响应及跨层数据对象
│  ├─ entity/             # MyBatis-Plus 数据库实体
│  ├─ mapper/             # MyBatis 数据访问接口
│  └─ service/            # 业务编排、状态流转和计算
└─ ShaftingWeighingApplication.java
```

并非每个模块都强制创建全部目录，只保留实际存在的职责。例如 `record` 当前没有独立业务逻辑，因此不增加空的 Service。`PackageStructureTest` 会阻止 Controller、Mapper、DTO、Entity、Service 再次混放到业务模块根包。

## 启动系统

先创建 MySQL 数据库：

```sql
CREATE DATABASE shafting_weighing CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

全新环境可依次执行 `database/create-database.sql`、`database/schema.sql` 和 `database/data.sql`。已有数据库需按阶段依次执行：

```text
database/phase-3-migration.sql
database/phase-4-migration.sql
database/phase-5-migration.sql
database/phase-6-control-panel.sql
database/phase-7-result-confirmation.sql
database/phase-8-channel-config.sql
database/phase-9-manual-target.sql
database/phase-10-force-interval.sql
database/phase-11-force-interval-kn.sql
database/phase-12-remove-sample-pressure.sql
database/phase-13-alarm-notification.sql
database/phase-14-pressure-interval.sql
```

除第十三阶段外，迁移脚本可重复执行；第十三阶段只能执行一次。第六阶段曾增加压力上限，当前应用已停用该字段；已有数据库中的旧列保留但不再读取和更新。第十、十一阶段曾把压力间隔改为力值间隔；第十四阶段根据 PLC 实际程序恢复 `pressure_interval_mpa`。旧 kN 数值无法在缺少油缸有效面积时可靠换算为 MPa，因此旧列保留，新压力间隔需重新配置。采样表仍不保存压力值。

按本机数据库设置环境变量后启动：

```powershell
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的数据库密码"
$env:ADMIN_INITIAL_PASSWORD = "首次管理员密码"
mvn spring-boot:run
```

浏览器访问：

```text
http://localhost:8080/login
```

首次启动自动创建 `admin` 管理员；如果未设置 `ADMIN_INITIAL_PASSWORD`，本地开发默认密码为 `Admin@123`，正式使用前必须修改。

## 已实现接口

- `GET /api/session`：当前用户、角色和 CSRF 信息。
- `GET /api/users`、`GET /api/users/roles`：仅管理员读取用户和可分配角色。
- `POST /api/users`：仅管理员新增用户并分配一个或多个角色。
- `PUT /api/users/{id}/roles`、`PUT /api/users/{id}/password`：仅管理员修改他人角色或重置密码。
- `PUT /api/users/me/password`：已登录用户验证原密码后修改本人密码并注销当前会话。
- `GET /api/points`：六个逻辑测量点。
- `GET /api/configs/{id}`：读取参数方案。
- `PUT /api/configs/{id}`：管理员或工程师保存六点参数，并写入操作审计。
- `PUT /api/configs/{id}/points/{pointCode}`：设置页按当前通道新增或更新参数，并递增方案版本、写入审计。
- `PUT /api/configs/{id}/points/{pointCode}/manual-target`：管理员或工程师在工作页按当前通道新增或更新手动目标位移。
- `GET /api/control/state`：读取报警锁定、全局动作和六通道模式、手动启动、暂停、清零及运行状态。
- `POST /api/control/commands`：执行模式切换、清零、手动启动与动作、暂停继续、停止、自动测试和自动快泄命令。
- `POST /api/tasks`：创建测量任务和模拟运行。
- `GET /api/tasks/{id}`：读取任务及运行状态。
- `POST /api/runs/{id}/commands`：启动、暂停、继续或停止模拟运行。
- `GET /api/runs/{id}/events`：订阅实时测量 SSE 事件。
- `GET /api/points/{pointCode}/events`：手动操作按通道订阅传感器即时值；无硬件读数时可返回手动模拟值，不依赖任务或运行记录。
- `GET /api/runs/{id}/samples`：结果保存后，按序读取位移和顶升力采样。
- `POST /api/runs/{id}/curve-fit/auto`：按升降阶段选择斜率最大的相邻两点自动拟合。
- `GET /api/runs/{id}/curve-fit`：读取最新拟合修订和测量结果。
- `POST /api/runs/{id}/curve-fit/manual`：管理员保存拖动后的人工修订。
- `GET /api/results/latest?pointCode={code}`：显示数值页按通道读取最新计算结果，不返回原始采样。
- `PATCH /api/runs/{runId}/result/adjustment-coefficient`：仅管理员修改本次结果的调整系数并重新计算。
- `POST /api/runs/{id}/result/save`：管理员、工程师或操作员确认保存结果，保存后曲线页才可读取原始采样。
- `DELETE /api/runs/{id}/result`：管理员逻辑删除结果并使该次原始采样失效；保留任务、运行、曲线修订和审计记录。
- `GET /api/tasks/{id}/results`：读取任务结果。
- `GET /api/records`、`GET /api/records/export`：记录查询和 UTF-8 CSV 导出。
- `GET /api/alarms`、`GET /api/alarms/{id}`：报警及动作历史。
- `POST /api/alarms/{id}/acknowledge|recover|close`：管理员执行模拟报警状态流转。
- `POST /api/simulator/alarms`：管理员注入模拟报警，用于无硬件测试。

控制面板由后端判定红、绿、灰状态：手动/自动是同级模式并按通道互斥，全系统同时只允许一个动作。手动模式还需按下“启动”后才能上升、下降或快泄；手动和自动动作均可暂停、继续和停止，停止后保留当前模式，手动启动状态也保留。手动目标位移仅允许管理员或工程师按当前通道保存，操作员只读；上升命令只读取数据库已保存值。后台优先读取真实位移传感器；无 PLC 且启用手动设备模拟时，按实际经过时间生成位移、力值和转速并实时推送，达到目标位移后自动停止。手动操作不创建测量任务、运行记录或原始采样，也不使用固定的每周期位移增量。切换手动/自动模式会清除原模式的清零标记，手动上升和自动测试每次启动前都要在当前模式下重新清零位移；自动快泄必须在其他动作停止后启动，并为六个通道实时生成逐步归零的模拟值。任一未关闭报警会停止包括快泄在内的全部动作并断开所有模式，全部报警关闭后仍需人工重新选择模式。

用户管理页面仅对管理员显示，可新增用户、分配多角色、修改他人角色和重置他人密码。登录账号统一转为小写；密码使用 BCrypt 保存。所有用户均可在顶部验证原密码后修改本人密码，成功后必须重新登录。用户及密码操作写入审计，但不记录明文密码。

业务页面的所有 API 请求统一处理 `401 Unauthorized`：后端判断 Session 已过期后，前端自动退出当前页面并跳转登录页，明确提示“登录已过期，请重新登录”。

模拟器模式默认每 500 ms 推送位移、转速和顶升力，并按 0～100 MPa 线性压力模型触发稳定采样。PLC 模式不运行 Java 自动动作状态机：软件只发送“自动测试”脉冲，依据 PLC 的流程阶段、采样序号和完成/故障反馈保存数据。工作页始终按当前页面通道订阅实时值，切换 Tab 不写 PLC；设置页和手动目标保存只更新数据库。接通手动或自动时只选择通道并接通模式；每次手动上升前将手动目标写入并回读当前通道原有“顶升距离”字段，每次自动测量前写入并回读自动目标位移、顶升速度、压力间隔（MPa）和时间间隔。PLC 模式通讯失败不会回退到模拟数据。

测量值按文档公式 `(P升 + P降) ÷ 2 × 顶升系数` 计算。调整量公式仍有歧义，因此数据库和接口保持空值。

显示数值页展示所选通道、已完成任务的标准值、测量值、偏差量、偏差率和调整量；调整系数仅管理员可查看和编辑。修改系数会重新计算测量值与偏差，并将结果恢复为“待保存”。两个曲线页按通道和已完成任务读取位移、顶升力稳定采样并生成坐标图。删除仅作用于该次结果和采样可见性，不物理删除任务、运行、曲线修订或审计记录。

## 验证

```shell
mvn test
node --check src/main/resources/static/prototype/app.js
node --test src/test/prototype/prototype.test.mjs
node src/test/js/control-panel.test.js
mvn package
```

## 后端环境

- JDK 21
- Maven 3.8.2+
- MySQL 8+
- Redis 7+

本机已安装 Microsoft OpenJDK 21。项目使用单体结构，前端静态资源与后端一起打入同一个可执行 JAR。

## 现场联调前仍需提供

- 六通道位移清零 BOOL 点；手动目标位移复用 DB100 各通道原有“顶升距离” REAL 字段，无需新增地址。
- 当前通道、模式、动作、运行状态、自动阶段、采样序号和故障编码反馈点及枚举确认。
- 压力、称重、位移传感器量程及标定公式，以及 PLC 急停回路和硬件联锁验收结果。

缺少这些地址时 `PLC` 模式会拒绝启动，不会使用猜测地址或模拟结果替代。完整配置和 TIA Portal 要求见 `docs/PLC-S7-1200通讯配置说明.md`。

## 设计资料

- `docs/document-image-audit.md`：原始 Word 文档全部图片识别结果。
- `docs/system-implementation-plan.md`：系统架构、页面方案、表设计、状态机和待确认项。
- `docs/PLC-S7-1200通讯配置说明.md`：S7-1200 现场连接、点位、反馈枚举和参数同步规则。
- `docs/PLC与平台通讯接口整改清单.md`：交给 PLC 工程师执行的点位整改、地址回填及联调验收表。
- `docs/prototype-work.png`：轴系工作页渲染截图。
- `docs/prototype-curves.png`：轴系曲线页渲染截图。
