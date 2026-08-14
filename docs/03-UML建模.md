# 记一笔（MoneyBook）UML 建模

> 文档版本：v1.1 ｜ 对应软件版本：记一笔 v2.0.0（正式版） ｜ 更新日期：2026-08-15
> 图表使用 Mermaid 绘制（GitHub 原生渲染）。

## 1. 用例图

### 1.1 总体用例

```mermaid
flowchart LR
    U((用户))
    subgraph 记一笔系统
        UC1[记一笔账单]
        UC2[管理分类/账户/账本]
        UC3[查看统计图表]
        UC4[设置预算与存钱目标]
        UC5[数据备份与导出]
        UC6[应用锁与提醒]
        UC7[桌面小组件]
        UC8[首次引导设置]
    end
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7
    U --> UC8
    UC1 -.->|include| UC9{{选择分类与账户}}
    UC1 -.->|extend| UC10{{附加截图/文件}}
    UC1 -.->|extend| UC11{{连续记账}}
    UC1 -.->|extend| UC12{{日历补账}}
    UC3 -.->|extend| UC13{{导出图表 PNG}}
    UC5 -.->|include| UC14{{加密备份}}
    UC5 -.->|extend| UC15{{附件随备份恢复}}
    UC6 -.->|include| UC16{{指纹验证}}
```

### 1.2 核心用例「记一笔」详细用例

| 项 | 内容 |
| --- | --- |
| 用例名称 | 记一笔账单 |
| 参与者 | 用户 |
| 前置条件 | 已进入首页（或从日历视图点日期进入） |
| 基本流 | 1. 点击悬浮「+」按钮；2. 选择支出/收入；3. 输入金额；4. 选择分类（自动选中第一个）；5. 选择账户；6. 填写备注（可选）；7. 选择日期（默认今天）；8. 可选附加截图/文件；9. 点击「记完了」保存并返回 |
| 扩展流 | 3a. 金额为空/非法 → 提示错误不保存；9a. 点击「继续记」→ 保存后留在本页并自动聚焦金额框、清空附件；9b. 附加了图片 → 列表显示附件图标；10. 从日历图进入时日期自动带入所选日期 |
| 后置条件 | 账单写入数据库，首页/统计/小组件自动刷新 |

## 2. 时序图

### 2.1 记一笔（含连续记账）

```mermaid
sequenceDiagram
    participant U as 用户
    participant S as AddEditBillScreen
    participant VM as AddEditBillViewModel
    participant R as BillRepository
    participant D as Room

    U->>S: 输入金额/分类/账户
    U->>S: 点击「继续记」
    S->>VM: save(andContinue=true)
    VM->>VM: 校验金额(正则+分转换)
    VM->>R: insert(Bill)
    R->>D: INSERT bills
    D-->>R: id
    VM-->>S: SavedContinue 事件 + 清空金额/备注/附件
    S->>S: 显示提示「已保存」并聚焦金额框
    U->>S: 继续输入下一笔
    U->>S: 点击「记完了」
    S->>VM: save(andContinue=false)
    VM->>R: insert(Bill)
    R->>D: INSERT bills
    VM-->>S: Saved 事件
    S-->>U: 返回上一页
```

### 2.2 切换账本（多账本联动）

```mermaid
sequenceDiagram
    participant U as 用户
    participant H as HomeScreen
    participant VM as HomeViewModel
    participant LR as LedgerRepository
    participant DS as DataStore
    participant BR as BillRepository

    U->>H: 点击顶栏账本名
    H->>VM: setActiveLedger(id)
    VM->>LR: setActiveLedger(id)
    LR->>DS: 写入 active_ledger_id
    DS-->>VM: activeLedgerId 新值发射
    VM->>BR: getMonthSummary(新账本, 当月)
    VM->>BR: getRecentBills(新账本)
    BR-->>VM: Flow 发射新数据
    VM-->>H: uiState 更新
    H-->>U: 首页显示新账本的结余与账单
```

### 2.3 加密备份导出（v3，跨设备可恢复）

```mermaid
sequenceDiagram
    participant U as 用户
    participant ST as SettingsScreen
    participant VM as SettingsViewModel
    participant BM as BackupManager
    participant SAF as 系统文件选择器

    U->>ST: 点击「导出加密备份」
    ST->>ST: 弹出密码输入框
    U->>ST: 输入并确认密码 PIN
    ST->>SAF: 创建 .mbk 文件(固定 requestCode)
    SAF-->>ST: 目标 Uri
    ST->>VM: exportBackup(uri, pin)
    VM->>BM: exportEncryptedTo(uri, pin)
    BM->>BM: 读取全量数据 → JSON
    BM->>BM: 收集附件文件 → ZIP 容器(backup.json + attachments)
    BM->>BM: 密钥 = SHA-256(PIN + KEY_SALT_V3)
    BM->>BM: GZIP → AES-256-GCM → 写入 magic(MBK3)+iv+密文
    BM-->>VM: Result(账单数)
    VM-->>ST: 提示「已导出加密备份」
```

### 2.4 指纹解锁（自动唤起）

```mermaid
sequenceDiagram
    participant A as MainActivity
    participant L as LockScreen
    participant BP as BiometricPrompt
    participant U as 用户

    A->>A: onStart(回到前台) → 读取锁设置
    A->>L: 显示锁屏
    L->>L: 检测：已设密码且启用指纹
    L->>BP: 自动 authenticate()
    BP-->>U: 系统指纹弹窗
    U->>BP: 按指纹
    BP-->>L: onAuthenticationSucceeded
    L-->>A: onUnlocked
    A->>A: 隐藏锁屏，显示主界面
```

### 2.5 图表导出 PNG（原生 Canvas 渲染）

```mermaid
sequenceDiagram
    participant U as 用户
    participant S as StatsScreen
    participant R as ChartBitmapRenderer
    participant C as android.graphics.Canvas

    U->>S: 点击「导出」按钮
    S->>R: render(当前图表状态, 1080, 1280)
    R->>C: 创建 Bitmap + Canvas
    R->>C: 复刻绘制饼图/柱状/折线/日历
    C-->>R: 绘制完成
    R-->>S: Bitmap
    S->>S: 保存到相册 / 分享
    S-->>U: 提示导出成功
```

### 2.6 首次引导（创建即见）

```mermaid
sequenceDiagram
    participant U as 用户
    participant M as MainActivity
    participant O as OnboardingScreen
    participant OVM as OnboardingViewModel
    participant DS as DataStore
    participant REPO as Ledger/Category/Budget Repository

    M->>DS: 读取 firstLaunchDone
    DS-->>M: false（首次启动）
    M->>O: 显示 6 页向导
    U->>O: 依次填写账本/分类/预算/安全
    O->>OVM: 提交各页数据
    OVM->>REPO: 事务批量创建
    U->>O: 点击「完成」
    O->>OVM: finish()
    OVM->>DS: 写入 firstLaunchDone=true
    M-->>U: 进入首页，创建内容立即可见
```
