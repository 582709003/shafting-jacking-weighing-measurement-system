# 轴系顶升称重测量系统实现方案（页面评审版）

## 1. 当前目标与边界

当前阶段在既有页面和数据模型上接入 S7-1200 通讯边界；软件不实现 PLC 内部自动动作时序，也不补全未确认的测量算法。

- 最终形态：浏览器访问的 Web 系统。
- 工程形态：一个 Maven 工程、一个 Git 仓库、一个 Spring Boot 进程、一个可部署 JAR。
- 后端基线：Java 21、Spring Boot 3.5.16、Spring Security、MyBatis-Plus 3.5.18、MySQL、Redis。
- 前端生产方案：Vue 3 + TypeScript + Vite + Element Plus + Apache ECharts。
- 前端源码放在同一 Maven 工程中，构建产物进入 `src/main/resources/static/app/`，由 Spring Boot 同源提供，不单独部署前端服务。
- 当前原型：零依赖 HTML/CSS/SVG，位于 `src/main/resources/static/prototype/`。

版本选择依据：[Spring Boot 官方稳定版本](https://docs.spring.io/spring-boot/spring-projects.html)、[MyBatis-Plus 官方版本](https://baomidou.com/)、[ECharts 官方拖拽示例](https://echarts.apache.org/handbook/en/how-to/interaction/drag/)。

## 2. 总体架构

```text
浏览器
  ├─ Vue 页面、ECharts、WebSocket/SSE
  └─ HTTPS / 同源请求
          │
Spring Boot 单体应用
  ├─ Spring Security：登录、角色、接口权限
  ├─ MVC/API：页面入口与业务接口
  ├─ 试验状态：根据 PLC 反馈同步顶升、保持、下降、暂停、完成和故障
  ├─ 曲线计算：拟合、截距、中间线、人工修订
  ├─ 设备通信：PLC4X S7 / ISO-on-TCP，SIMULATOR 与 PLC 模式隔离
  ├─ MyBatis-Plus → MySQL：配置、采样、结果、报警、审计
  └─ Redis：会话、实时值、设备状态、命令锁、运行态缓存
          │
PLC / 控制器
  ├─ 伺服电机与泵
  ├─ 电磁换向阀
  ├─ 压力 / 称重 / 位移传感器
  └─ 硬件急停、限位、超压与通信失效保护
```

### 关键边界

1. PLC/控制器负责毫秒级动作、安全联锁、急停、位移和压力硬限位。
2. Web 后端负责任务记录、命令和参数下发、状态展示、PLC稳定采样持久化和业务审计；自动动作流程由 PLC 负责。
3. Redis 只保存可重建的实时状态，不作为测量结果的唯一存储。
4. MySQL 保存任务参数快照、原始采样、曲线修订记录和最终结果。

## 3. 前端方案

### 3.1 单工程组织方式

```text
shafting-jacking-weighing-system/
├─ pom.xml
├─ AGENTS.md
├─ frontend/                         # Vue 3 源码，后续页面确认后建立
├─ src/main/java/com/shafting/weighing/
│  ├─ config/                        # 全局 Spring 配置
│  ├─ alarm/                         # 报警及处理记录
│  ├─ control/                       # 工作页控制状态机
│  ├─ curve/                         # 曲线拟合与测量结果
│  ├─ device/                        # 模拟器、采样和实时状态
│  ├─ measurement/                   # 测量点及参数配置
│  ├─ record/                        # 测量记录查询与导出
│  ├─ security/                      # 登录、会话、用户与权限
│  └─ task/                          # 测量任务和运行
├─ src/main/resources/
│  ├─ mapper/
│  ├─ static/app/                    # Vue 构建产物
│  ├─ static/prototype/              # 本轮可审阅原型
│  └─ application.yml
└─ docs/
```

每个业务模块内部按实际职责使用 `controller`、`dto`、`entity`、`mapper`、`service`、`config` 子包；不创建没有实现内容的空包。包结构由自动化测试约束，Controller、Mapper、DTO、数据库实体和 Service 不得直接混放在业务模块根包。

开发时允许 Vite 提供热更新，但通过代理访问同一个 Spring Boot API；正式构建由 Maven 调用前端构建，最终只发布一个 JAR。

### 3.2 曲线交互

- ECharts `line`/`scatter` 显示上升、下降原始点，点间曲线启用 `smooth` 平滑连接；平滑仅用于显示，计算仍使用原始采样点。
- 拟合线上端点可上下左右拖动，下端点固定在横轴且仅可左右拖动；中间虚线由升、降拟合线自动计算。
- 拖动时使用 `convertFromPixel` 将屏幕坐标还原为 kN/mm 数据坐标。
- 自动线段分别从上升、下降数据中比较相邻两点斜率，取最大斜率的一对相邻点；位移统一按 0.01 mm 分度。
- 上升线或下降线改变后，实时重新计算横轴截距和平均线。
- 保存时同时提交：自动拟合端点、人工端点、自动结果、人工结果、修改人和修改时间。
- 仅 `ADMIN` 可拖动和保存人工拟合；其他角色只读。修改无需第二人复核，但必须写入审计记录。
- 支持“恢复自动拟合”，人工修改不覆盖原始采样和自动结果。

## 4. 公共数据规则

所有业务表默认包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 雪花 ID 或数据库主键 |
| `created_by` | BIGINT | 创建用户 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_by` | BIGINT | 最后修改用户 |
| `updated_at` | DATETIME(3) | 最后修改时间 |
| `version` | INT | 乐观锁版本 |
| `deleted` | TINYINT | 逻辑删除标记 |

测量值使用 `DECIMAL`，不得使用 `FLOAT/DOUBLE` 保存最终业务结果；原始设备浮点值在进入计算前统一量纲并记录原始值。

## 5. 模块与页面方案

### 5.1 登录页

页面：用户名、密码；当前登录不区分轴系和主机，成功后统一进入控制台。登录失败显示明确原因，正式版本不提供前端记住明文密码。

权限建议：

- `ADMIN`：用户、角色、设备、所有数据和曲线人工修订。
- `ENGINEER`：参数配置、结果确认；曲线只读。
- `OPERATOR`：启动/暂停/停止、采样、保存记录。
- `VIEWER`：只读曲线、记录和报警。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `sys_user` | `username`, `password_hash`, `display_name`, `status`, `last_login_at` | 用户与密码摘要 |
| `sys_role` | `role_code`, `role_name` | 角色 |
| `sys_user_role` | `user_id`, `role_id` | 用户角色关系 |
| `sys_permission` | `permission_code`, `module`, `action` | 页面和动作权限 |
| `sys_role_permission` | `role_id`, `permission_id` | 角色权限关系 |
| `sys_login_log` | `user_id`, `login_at`, `ip`, `success`, `failure_reason` | 登录审计 |

### 5.2 轴系工作页

页面布局：

- 顶部六通道条：尾轴、中轴1、中轴2、飞轮、1#、2#。
- 左上实时数据：距离、顶升力、转速；压力保留采样、保护和曲线用途，不在实时卡片显示。
- 右上轴线图：通道在线、传感器状态和当前动作。
- 左下动作控制采用同级双模式结构：左侧手动模式、启动、目标位移、上升、下降、快泄、暂停/继续、停止；右侧自动模式、自动测试、自动快泄、暂停/继续、停止。
- 右下状态机步骤：预检查、上升、保压、采样、下降、完成。

后台逻辑：

- 动作按钮先调用预检查：权限、报警、通道、设备在线、参数范围、运行锁。
- 每个动作生成唯一 `command_no`，避免浏览器重复点击造成重复命令。
- 同一时刻只允许一个测量通道进入动作状态。
- 手动/自动模式按通道保存且互斥，动作执行或暂停期间禁止切换模式。
- 手动模式按下启动后才允许动作；停止动作不清除当前模式和手动启动状态。
- 本通道清零只清零位移，手动上升和自动测试每次启动都会消费清零标记。
- 手动目标位移仅允许管理员或工程师在工作页按通道单独保存，操作员只读；没有参数行时新增，已有时更新。手动上升只读取已保存且大于 0 的值，到达目标后自动停止上升。无 PLC 联调时，手动设备模拟按动作经过时间生成位移、力值和转速并通过现有 SSE 展示，不创建任务、运行或采样记录；真实传感器有值时优先使用真实数据。
- PLC 模式下自动测试只下发自动测试脉冲，目标位移、压力间隔、等待和升降顺序均由 PLC 程序执行；Java 根据阶段、采样序号及完成/故障反馈保存数据。
- 切换工作页通道只切换页面实时数据来源；设置页和手动目标保存只更新数据库。接通手动或自动时只选择通道并确认模式；每次手动上升前写入并回读目标位移，每次自动测量前只写入并回读自动目标位移、顶升速度、压力间隔和时间间隔。
- 自动快泄必须在活动动作停止后启动，并与自动测试共用暂停/继续和停止按钮；无硬件读数时，六通道按快泄经过时间生成逐步归零的位移、力值和转速并通过通道 SSE 展示，不保存任务或采样。
- 任一未关闭报警停止包括快泄在内的全部动作，关闭全部报警后仍需人工重新启用。
- 实时值通过 WebSocket/SSE 推送；捕获点和关键状态写入 MySQL。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `measurement_point` | `point_code`, `point_name`, `point_type`, `sort_no`, `enabled` | 六个逻辑测量点 |
| `device` | `device_code`, `device_type`, `protocol`, `address`, `status` | PLC、传感器、伺服等设备 |
| `device_tag` | `device_id`, `tag_code`, `data_type`, `unit`, `read_write`, `scale`, `offset` | 设备点位/寄存器定义 |
| `point_tag_mapping` | `point_id`, `tag_id`, `purpose` | 测量点与压力、位移、阀输出映射 |
| `test_run` | `task_id`, `point_id`, `run_no`, `mode`, `state`, `started_at`, `ended_at`, `fault_code` | 一次通道运行 |
| `device_command` | `command_no`, `run_id`, `command_type`, `payload_json`, `status`, `sent_at`, `acked_at` | 控制命令与应答审计 |

Redis：

- `device:status:{deviceCode}`：设备在线状态，短 TTL。
- `realtime:{pointCode}`：最新压力、位移、力、转速。
- `run:state:{runId}`：当前状态机快照。
- 当前单机版全局控制锁保存在进程内存中；服务重启后安全复位为全部模式断开。部署多实例前再改为 Redis 分布式锁。

### 5.3 轴系设置页

页面按六个测量点提供 Tab，每次只编辑和保存当前通道。标准值与该通道总值并排显示；总值单位 kN、精度 0.01，仅记录，不参与测量计算。后台按参数方案和测量点查询，记录不存在时新增，存在时更新，并递增方案版本、写入审计。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `test_config` | `config_name`, `config_version`, `status`, `effective_at`, `remark` | 参数方案主表 |
| `test_config_point` | `config_id`, `point_id`, `standard_force`, `total_value`, `manual_target_displacement`, `jacking_coefficient`, `max_displacement`, `motor_rpm`, `pressure_interval_mpa`, `hold_seconds` | 每点参数；压力间隔单位 MPa，总值只记录；不设置压力上限 |
| `parameter_change_log` | `config_id`, `point_id`, `field_name`, `old_value`, `new_value`, `changed_by`, `changed_at` | 参数变更审计 |

校验在三处同时存在：前端即时提示、后端 Bean Validation、PLC 最终安全范围。

### 5.4 显示数值页

页面显示标准值、测量值、偏差量、偏差率和调整量，并可选择六个通道查看各自最新计算结果；调整系数仅 `ADMIN` 可查看和编辑，其他角色的接口响应不返回该字段。管理员修改后按最新拟合平均截距重新计算测量值和偏差，结果恢复为待保存。本页不展示压力、位移等原始采样。

计算完成的结果先处于 `DRAFT`（待保存）状态。`ADMIN`、`ENGINEER`、`OPERATOR` 可显式保存，保存后状态变为 `SAVED`，对应原始采样才可在轴系曲线页或 1#/2# 曲线页的表格和坐标图中查看。重复保存保持幂等；结果重新拟合后恢复为待保存，必须再次确认。`ADMIN` 可删除本次结果，删除采用逻辑删除，同时将本次采样标为无效；任务、运行、曲线修订和操作审计保留。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `measurement_result` | `task_id`, `run_id`, `point_id`, `standard_force`, `p_up`, `p_down`, `p_mean`, `jacking_coefficient`, `measured_force`, `deviation`, `deviation_rate`, `adjustment_factor`, `adjustment_amount`, `result_status`, `saved_by`, `saved_at`, `deleted` | 计算结果、显式保存状态和逻辑删除标记 |

`adjustment_amount` 在公式确认前允许为空，不以猜测公式生成正式值。

### 5.5 轴系曲线页

页面：尾轴、中轴1、中轴2、飞轮四个概览；只有结果已保存时，选择后才显示该次压力、位移、顶升力原始采样表和大尺寸交互曲线。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `measurement_sample` | `run_id`, `point_id`, `stage`, `sequence_no`, `captured_at`, `force_kn`, `displacement_mm`, `raw_payload_json`, `valid` | 上升/下降原始采样 |
| `curve_fit_revision` | `run_id`, `point_id`, `direction`, `revision_no`, `source_type`, `p1_force`, `p1_displacement`, `p2_force`, `p2_displacement`, `x_intercept`, `algorithm_version`, `reason` | 自动或人工拟合线版本 |

规则：原始采样不物理删除；删除测量结果时将对应采样标为无效，并逻辑删除结果，保留任务、运行、拟合修订和审计记录。坐标图当前沿用已确认的力—位移曲线，压力和位移原始值同时保存在表格中。

### 5.6 1# / 2# 曲线页

与轴系曲线共用组件和两张表，只通过 `measurement_point.point_type = MAIN_ENGINE` 区分，不另建重复业务表。主机与轴系曲线的位移分度统一为 `0.01 mm`。

### 5.7 记录页

页面：左侧项目/文件夹，右侧任务列表；支持搜索、查看、重命名、导出和受控删除。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `measurement_project` | `project_no`, `project_name`, `vessel_name`, `description`, `status` | 对应原图“文件夹” |
| `test_task` | `project_id`, `task_no`, `task_name`, `config_id`, `operator_id`, `task_status`, `started_at`, `completed_at` | 一次完整测量任务 |
| `export_record` | `task_id`, `export_type`, `file_name`, `file_path`, `exported_by`, `exported_at` | CSV/PDF 导出记录；确认需要文件导出后再实现 |

首版不引入对象存储；导出文件可即时生成。只有明确需要长期保存附件时再增加文件存储。

### 5.8 报警页

页面分为“活动报警、处理中报警、已关闭报警”三个页签：`ACTIVE` 进入活动报警，
`ACKNOWLEDGED/RECOVERED` 进入处理中报警，`CLOSED` 进入已关闭报警。各页签支持按报警编码、
来源或内容关键词、报警级别和发生时间范围筛选，并采用服务端分页；列表不定时自动刷新。
新报警通过 SSE 弹出一次红色提示并播放一次声音，重新登录或刷新页面不重放历史报警，
但顶部报警中心持续以红色角标显示 `ACTIVE` 数量。

表设计：

| 表 | 关键字段 | 用途 |
|---|---|---|
| `alarm_event` | `alarm_code`, `alarm_level`, `source_name`, `message`, `status`, `deduplication_key`, `occurred_at`, `updated_at` | 一次报警事件；PLC未关闭报警使用唯一防重键，模拟报警不使用防重键 |
| `alarm_action` | `alarm_id`, `action_type`, `from_status`, `to_status`, `remark`, `acted_by`, `acted_at` | 确认、恢复、关闭的不可覆盖处理轨迹 |

安全规则：任一新报警立即停止一切软件动作，包括手动快泄和自动快泄，并锁定全部控制。
只有管理员可严格按 `ACTIVE → ACKNOWLEDGED → RECOVERED → CLOSED` 处理；PLC信号未恢复为0时
不能标记恢复，全部报警关闭后只解除锁定，不自动恢复原模式或动作。真实硬件急停和安全联锁仍由 PLC/控制器兜底。

### 5.9 通用操作审计

| 表 | 关键字段 | 用途 |
|---|---|---|
| `sys_operation_log` | `user_id`, `module`, `operation`, `business_id`, `request_id`, `result`, `detail_json`, `operated_at` | 参数保存、控制命令、人工拟合、删除和报警复位审计 |

## 6. 自动测试反馈状态

```text
IDLE
  → PRECHECK
  → LIFT_START
  → LIFTING
  → PRESSURE_INTERVAL_REACHED
  → HOLDING
  → SAMPLE_UP
  → 下一压力点 / MAX_DISPLACEMENT
  → DESCEND_START
  → LOWERING
  → PRESSURE_INTERVAL_REACHED
  → HOLDING
  → SAMPLE_DOWN
  → 下一压力点 / ZERO_PRESSURE
  → COMPLETED
```

自动测试启动前必须已有一次有效位移清零，并配置大于 0 的目标位移和 1～32767 MPa 的整数压力间隔。PLC 模式下 Java 不按上述节点逐条编排动作，只发送自动测试脉冲。PLC 每完成一次稳定采样便递增采样序号，并保持该次位移和顶升力；Java 轮询检测序号变化后按 `(runId, sequenceNo)` 幂等保存，且先保存最终采样再处理完成反馈。

任意运行状态可进入 `PAUSED`、`STOPPING` 或 `FAULT`。恢复策略、故障后的安全泄压和断电重启恢复方式需与 PLC 方联合定义。

## 7. 接口初稿

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/points` | 测量点及实时状态 |
| `GET/PUT` | `/api/configs/{id}` | 参数方案读取与保存 |
| `PUT` | `/api/configs/{id}/points/{pointCode}` | 新增或更新当前通道参数 |
| `PUT` | `/api/configs/{id}/points/{pointCode}/manual-target` | 工作页新增或更新当前通道手动目标位移 |
| `GET` | `/api/control/state` | 控制面板权威状态 |
| `POST` | `/api/control/commands` | 模式、清零、手动启动与动作、暂停继续、停止、自动测试和自动快泄 |
| `POST` | `/api/tasks` | 新建测量任务 |
| `POST` | `/api/runs/{id}/commands` | 启动、暂停、停止、上升、下降、快泄 |
| `GET` | `/api/runs/{id}/samples` | 已保存结果的原始采样数据；未保存返回业务冲突 |
| `GET/POST` | `/api/runs/{id}/curve-fit` | 自动拟合和人工修订；`POST` 仅允许 `ADMIN` |
| `GET` | `/api/results/latest?pointCode={code}` | 显示数值页按通道读取最新计算结果 |
| `PATCH` | `/api/runs/{runId}/result/adjustment-coefficient` | 仅管理员修改调整系数并重新计算结果 |
| `POST` | `/api/runs/{id}/result/save` | 显式保存结果；`ADMIN/ENGINEER/OPERATOR` 可操作 |
| `DELETE` | `/api/runs/{id}/result` | 逻辑删除结果并使采样失效；仅 `ADMIN` |
| `GET` | `/api/tasks/{id}/results` | 读取任务下未删除的测量结果 |
| `GET` | `/api/alarms` | 报警列表 |
| `POST` | `/api/alarms/{id}/actions` | 确认、处理、复位 |
| `GET` | `/api/records` | 项目和任务归档 |
| `GET` | `/stream/realtime` | 实时值和状态推送 |

## 8. 计算与数据质量

- 每个采样点必须保存统一时间戳、阶段、序号、顶升力和位移；不保存与顶升力重复的压力字段。
- 同组力与位移必须来自同一设备采样周期或带有可验证的时间同步误差。
- 曲线平滑只用于显示；正式计算默认使用原始有效点或经确认的滤波数据。
- 自动拟合算法必须带版本号，历史结果能按原算法复算。
- 人工拖线仅允许管理员操作，保存修改原因和前后值，不需要第二人复核。
- 压力转顶升力的公式、标定系数和单位转换单独版本化。

## 9. 测试方案

1. 曲线数学测试：截距、中间线、异常斜率、重复点、零标准值。
2. 状态机测试：正常升降、暂停恢复、超压、超位移、断连、重复命令。
3. 权限测试：操作员不能修改拟合线，查看者不能发控制命令。
4. 持久化测试：任务参数快照、采样顺序、人工修订版本不可覆盖。
5. 设备通讯测试：模拟模式验证页面和算法，PLC 模式验证点位、脉冲顺序、反馈超时和禁止降级。
6. 浏览器测试：页面切换、曲线端点拖拽、不同分辨率和误操作确认。

## 10. 实施顺序

1. 当前阶段：页面与业务字段评审。
2. 确认 PLC、传感器、通道映射、单位和算法。
3. 建库并完成用户、参数、记录、报警基础模块。
4. 实现设备模拟器和自动状态机测试。
5. 接入真实 PLC，只替换设备通信边界。
6. 实现曲线拟合、人工修订和结果确认。
7. 联调安全联锁、断连恢复、报表与现场验收。

### 当前实现状态（2026-11-18）

- 已完成第 3、4、6 项的软件实现：数据库基础模块、同级互斥的手动/自动控制面板、内存控制状态机、手动/自动暂停继续与停止、自动稳定点采样、曲线拟合/管理员修订、结果显式保存与逻辑删除、保存后曲线可见、记录导出和报警强制停机。手动操作不创建任务、运行或采样记录，只下发设备动作并用实时位移执行目标值兜底停止。
- 已实现 PLC4X S7 通讯、DB100 固定点位、DB12 报警轮询、反馈驱动状态、动作开始前参数写入回读和自动采样保存；手动目标复用各通道原有“顶升距离”字段，真实 PLC 验收仍依赖清零与反馈点位地址和现场硬件。
- 原计划中的项目文件夹、项目/任务记录删除和调整量计算因业务规则不完整保持禁用或空值；单次测量结果删除已按逻辑删除实现。

## 11. 必须确认的问题

1. 已确认 S7-1200、S7 协议（ISO-on-TCP）及《点位表0505.xlsx》；手动目标复用 DB100 原“顶升距离”，仍需提供六通道清零和各反馈点的实际地址与枚举确认。
2. 请提供六个测量点对应的阀组、称重传感器和位移传感器最终结构资料。
3. 压力传感器、称重传感器和位移传感器的型号、量程、精度、单位及标定公式是什么？
4. 六通道自动快泄对应的 PLC 输出点、动作确认信号和硬件超时是多少？软件范围已确定为全部通道。
5. 记录删除是否可恢复，数据和操作日志需要保存多久？
