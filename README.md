# 运输车辆调度优化与仿真

基于 Java 17、Spring Boot 3、MySQL 8 和高德地图的课程项目，提供车辆与订单管理、实时调度监控、路线动画、突发事件模拟，以及可保存、重跑和比较的实验沙箱。前端使用原生 HTML/CSS/JavaScript，无需 npm 构建。

## 功能与算法

| 模块 | 说明 |
| --- | --- |
| 实时监控 | 车辆、订单、任务、趋势统计、地图轨迹及仿真时钟 |
| 调度控制 | 开始、暂停、动态新增车辆和订单、重置场景 |
| 突发事件 | 仿真拥堵、天气和车辆故障，记录延误及受影响车辆 |
| 实验沙箱 | 场景快照、固定种子、参数与权重编辑、进度、历史与重跑 |
| 结果比较 | 检查场景、模型、种子及控制条件，导出 Run JSON 和比较 CSV |

支持三种调度策略：

- `BASELINE`：按订单到达时间和 ID 进行基准派单。
- `OPTIMIZED`：以单位时间净收益为目标，进行有界候选与路线插入搜索。
- `WEIGHTED`：将货物等待、空载里程、载重里程利用率、承运吨位和碳排放转为无量纲损失，按用户权重选择候选计划。

这些策略属于仿真和有界启发式方法，不保证全局最优或所有指标同时改善。地图路线、事件、成本与排放使用项目模型；不代表车辆传感器实测数据。当前新实验按“全部订单完成”统计，旧的 24 小时结果保留原口径。指标定义、评分公式和验证范围见 [加权调度设计与验收记录](reports/2026-11-02-数据沙箱收尾与加权调度设计.md)。

## 本地运行

准备 Java 17、Maven 3.9、MySQL 8；运行前先启动数据库。Node.js 仅用于前端测试。

### 1. 初始化数据库

在 MySQL 客户端中，为新环境创建并选择一个空数据库，再导入结构：

```sql
CREATE DATABASE tran_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE tran_db;
SOURCE E:/works/database/schema.sql;
```

`SOURCE` 路径应替换为实际克隆目录。脚本只包含 `vehicle`、`transport_order`、`dispatch_task`、`location` 四张表的结构，不含原数据库业务数据。应用启动时自动建立沙箱、统计和事件表，因此数据库用户需有相应建表和读写权限。

运行调度前，需向 `location` 导入有权使用的 POI 数据。表结构见 [database/schema.sql](database/schema.sql)，其中 `id` 为唯一地点标识，`location` 为 `经度,纬度`，其余必填字段为 `location_name` 和 `role`；车型、订单及地点筛选沿用项目业务规则。仓库中的报告快照用于核查历史实验，不能替代初始化数据。

### 2. 配置数据库与地图

可在 PowerShell 中设置后端环境变量：

```powershell
$env:DB_USER = 'root'
$env:DB_PASSWORD = '你的数据库密码'
$env:AMAP_WEBSERVICE_KEY = '你的高德 Web 服务 Key'
# 可选，默认连接本机 tran_db：
# $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/tran_db?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000'
```

也可复制 `config/application.example.properties` 为 `config/application.properties`，填写后端本地配置。该文件已被 Git 忽略。后端密码和 Web 服务 Key 默认均为空。

地图页面还需独立的浏览器配置：

```powershell
Copy-Item WebSource/js/amap-config.example.js WebSource/js/amap-config.local.js
```

编辑 `amap-config.local.js`，填写高德 JS API 浏览器 Key 和安全密钥；按高德控制台要求配置允许使用的域名。该本地文件也已被 Git 忽略。Web 服务 Key 由后端使用，旧路线演示页面也通过 `/amap/driving` 代理请求。

### 3. 构建与启动

始终在仓库根目录执行，应用从 `WebSource/` 加载前端文件，从 `config/` 加载本地配置：

```powershell
mvn package
java -jar target/springboot-hello-1.0-SNAPSHOT.jar
```

Windows 也可使用启动脚本，它会先检查 9091 端口，再构建并启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-project.ps1
```

| 入口 | 地址 |
| --- | --- |
| 首页 | http://localhost:9091/ |
| 实时监控 | http://localhost:9091/html/cycle.html |
| 实验沙箱 | http://localhost:9091/html/sandbox.html |
| 中期汇报 | http://localhost:9091/html/midterm.html |

首次使用时先准备 POI，再在监控页生成车辆与订单，或在沙箱创建独立实验。场景重置会清空当前车辆、订单和任务，应按页面提示操作。

仓库名称包含 `.github.io`，但完整应用需要 Java 与 MySQL，不能仅靠 GitHub Pages 运行后端。此仓库用于保存项目源码和验收材料。

## 验证

不访问 MySQL 的默认后端测试与打包：

```powershell
mvn package
```

前端测试：

```powershell
Get-ChildItem WebSource/tests/*.test.cjs | ForEach-Object {
    node $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "前端测试失败：$($_.Name)" }
}
```

MySQL 集成测试默认跳过。需要显式设置 `$env:MYSQL_INTEGRATION='1'` 并提供数据库环境变量，再运行 `mvn '-Dtest=BackendMySqlIntegrationTest' test`。测试从已配置数据库复制四张表结构及最多 20 条 POI，在独立随机命名测试库中执行并清理，需要创建和删除测试库的权限。

`AlgorithmExecutionAuditTest` 绑定旧模型，默认跳过；它用于历史执行路径核查，不作为当前加权模型的效果结论。当前模型由 `WeightedDispatchTest` 等测试覆盖。

## 目录与材料

```text
src/main/java/org/example/   控制器、DAO、调度服务、沙箱与指标
src/main/resources/         公共配置和日志配置
src/test/                   算法、并发安全与可选 MySQL 集成测试
WebSource/                  页面、样式、脚本与前端测试
database/                   业务基础表结构
config/                     后端本地配置示例
scripts/                    数据库结构只读检查工具
reports/                    验收记录、实验快照、截图及中期报告
figures/                    报告配图
start-project.ps1           Windows 构建启动入口
```

更多实现记录见 [后端说明](BACKEND-CHANGES.md)、[前端说明](WebSource/FRONTEND-CHANGES.md) 和 [全订单完成改造记录](reports/2026-10-12-全订单完成改造.md)。历史说明中的计数与参数以记录日期为准。

日志、抓包、IDE 配置、密钥文件、构建目录和报告渲染中间文件均不进入版本控制；本地已有文件继续保留。
