# 轴系顶升称重测量系统

当前已完成可在无现场硬件条件下实现的软件阶段：数据库登录与权限、六测量点参数、手动/自动互斥控制面板、后端控制状态机、手动/自动设备模拟、原始采样、SSE 实时推送、Redis 最新状态、相邻点自动拟合、管理员人工修订、结果计算、记录查询/CSV 导出和模拟报警闭环。真实 PLC、传感器和硬件安全联锁尚未接入。

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
```

迁移脚本均可重复执行。第六阶段曾增加压力上限，当前应用已按最新需求停用该字段；为避免破坏历史数据，已有数据库中的旧列保留但不再读取和更新，新建数据库不再创建。第七阶段增加结果保存状态；第八阶段增加通道总值；第九阶段增加手动目标位移；第十阶段将原 MPa 压力间隔替换为 N 力值间隔；第十一阶段再统一为 kN，迁移时将历史 N 数值除以 1000，保持实际阈值不变。

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
- `GET /api/points`：六个逻辑测量点。
- `GET /api/configs/{id}`：读取参数方案。
- `PUT /api/configs/{id}`：管理员或工程师保存六点参数，并写入操作审计。
- `PUT /api/configs/{id}/points/{pointCode}`：设置页按当前通道新增或更新参数，并递增方案版本、写入审计。
- `PUT /api/configs/{id}/points/{pointCode}/manual-target`：工作页按当前通道新增或更新手动目标位移。
- `GET /api/control/state`：读取报警锁定、全局动作和六通道模式、手动启动、暂停、清零及运行状态。
- `POST /api/control/commands`：执行模式切换、清零、手动启动与动作、暂停继续、停止、自动测试和自动快泄命令。
- `POST /api/tasks`：创建测量任务和模拟运行。
- `GET /api/tasks/{id}`：读取任务及运行状态。
- `POST /api/runs/{id}/commands`：启动、暂停、继续或停止模拟运行。
- `GET /api/runs/{id}/events`：订阅实时测量 SSE 事件。
- `GET /api/runs/{id}/samples`：结果保存后，按序读取原始压力、位移和顶升力采样。
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

控制面板由后端判定红、绿、灰状态：手动/自动是同级模式并按通道互斥，全系统同时只允许一个动作。手动模式还需按下“启动”后才能上升、下降或快泄；手动和自动动作均可暂停、继续和停止，停止后保留当前模式，手动启动状态也保留。手动目标位移必须按当前通道先保存，上升命令只读取数据库已保存值；到达目标位移后即使未人工停止也会自动结束上升。手动上升和自动测试每次启动前都要重新清零位移；自动快泄必须在其他动作停止后启动。任一未关闭报警会停止包括快泄在内的全部动作并断开所有模式，全部报警关闭后仍需人工重新选择模式。

模拟器默认每 500 ms 推送位移、压力、转速和顶升力，但自动测试只在力值阈值暂停结束、最高位移暂停结束和下降归零时保存稳定采样。工作页实时卡片只显示位移、顶升力和转速，压力随稳定采样保存，不设置压力上限或以压力作为间隔判断。自动上升按 0.5kN、1.0kN、1.5kN 等累计力值暂停，等待设置页“时间间隔”后同时读取并保存传感器即时值；达到最大位移后以相同方式保存最高点并转入下降。下降按相反的累计力值逐级暂停采样，顶升力归零时保存最后一组数据并完成流程。最新快照以 `realtime:run:{runId}` 为键写入 Redis，TTL 为 10 秒。

测量值按文档公式 `(P升 + P降) ÷ 2 × 顶升系数` 计算。调整量公式仍有歧义，因此数据库和接口保持空值。

显示数值页展示所选通道、已完成任务的标准值、测量值、偏差量、偏差率和调整量；调整系数仅管理员可查看和编辑。修改系数会重新计算测量值与偏差，并将结果恢复为“待保存”。两个曲线页按通道和已完成任务读取压力、位移、顶升力稳定采样并生成坐标图。删除仅作用于该次结果和采样可见性，不物理删除任务、运行、曲线修订或审计记录。

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

## 尚不能实现的现场阶段

- PLC 品牌、协议、寄存器/变量表和六点物理通道映射未提供。
- 压力、称重、位移传感器的量程及标定公式未提供。
- 自动快泄的软件范围已确定为六通道；真实阀组输出、急停回路和硬件联锁仍需 PLC 方提供。
- 项目/任务记录的删除恢复、保存年限和审批规则仍未确认；显示数值页的单次结果删除已按管理员逻辑删除实现。

这些能力不能由 Web 软件模拟结果替代，资料确认后应在现有设备边界内接入并增加现场联调测试。

## 设计资料

- `docs/document-image-audit.md`：原始 Word 文档全部图片识别结果。
- `docs/system-implementation-plan.md`：系统架构、页面方案、表设计、状态机和待确认项。
- `docs/prototype-work.png`：轴系工作页渲染截图。
- `docs/prototype-curves.png`：轴系曲线页渲染截图。
