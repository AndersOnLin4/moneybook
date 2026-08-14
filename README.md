# 记账本（MoneyBook）

一款完全本地化、无网络权限的 Android 记账 App。
技术栈：**Kotlin + Jetpack Compose + Room + MVVM**，最低支持 Android 9（API 28），适配小米 HyperOS 3.0.5.0。

## 功能

| 模块 | 说明 |
| --- | --- |
| 记一笔 | 支出/收入切换、金额（分存储，无浮点误差）、分类、账户、备注、日期（可补记）；新增 / 编辑 / 删除账单 |
| 多账户 | 内置账户（现金/微信/支付宝/银行卡），支持自定义增删；删除账户自动转移账单 |
| 分类管理 | 内置默认分类（支出：餐饮、交通、购物、住宿、娱乐、医疗；收入：工资、红包、兼职），支持自定义增删（emoji 图标，删除有账单的分类会二次确认并连带删除账单，默认分类不可删除） |
| 首页 | 当月总收入 / 总支出 / 结余 + 月度预算进度条与超支提醒 + 最近 10 条账单预览 + 悬浮记账按钮 |
| 账单列表 | 时间倒序、按月分组（组头显示当月收支合计）、搜索（备注/分类名）+ 类型筛选 |
| 统计页 | 分类占比环形饼图（Canvas 自绘，无第三方图表库），支持 日 / 周 / 月 / 年 四种时间维度 + 支出/收入切换 |
| 周期账单 | 每周 / 每月 / 每年自动记账（App 启动时补记到期账单），支持增删改与启用停用 |
| 预算 | 按「年-月」设置月度支出预算，首页显示进度与剩余/超支金额 |
| 导出 | JSON 全量备份导出/导入恢复（覆盖式，二次确认）+ 账单导出 CSV（UTF-8 BOM，Excel 直接打开） |
| 应用锁 | 4-6 位数字密码（盐 + SHA-256 哈希存储）+ 指纹解锁，回前台自动锁定 |
| 桌面小组件 | Glance 小组件：显示本月结余 / 收支，一键快捷记账，打开 App 自动刷新 |
| 设置页 | 深色/浅色/跟随系统切换（DataStore 持久化）、分类/账户/预算/周期账单/应用锁管理入口、关于页面 |

## 关键约束（已满足）

- **无任何权限**：AndroidManifest 未声明网络、存储等任何权限；备份导入导出走系统文件选择器（SAF）。
- **数据全本地**：Room（`moneybook.db`）+ DataStore，无登录、无广告、无第三方付费 SDK。
- 空数据状态、金额非法校验（大于 0、最多两位小数）、删除二次确认等边界均已处理。

## 导入与构建

1. 用 **Android Studio Koala（2024.1.1）及以上** 打开本文件夹（自带 JDK 17）。
2. 等待 Gradle 同步完成（首次需联网下载依赖，之后可离线构建）。构建仓库已配置阿里云镜像优先、官方源兜底，国内同步更快。
3. 连接手机（开启 USB 调试）或创建模拟器，点击 Run ▶ 即可。

> 注意：工程路径包含中文字符（工作文件夹名），`gradle.properties` 已加入 `android.overridePathCheck=true`，请勿删除，否则 AGP 会拒绝在此路径下构建。

命令行构建（需 JDK 17）：

```bat
gradlew.bat assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`（本工程已在本机验证构建成功，约 54 MB，含完整调试信息，可直接安装）

Release 构建（已开启混淆 + 资源收缩 + 签名）：

```bat
gradlew.bat assembleRelease
```

产物：`app\build\outputs\apk\release\app-release.apk`（已验证构建成功，R8 压缩后约 2.6 MB，可直接安装）

签名说明：发布签名密钥库位于 `keystore\release.keystore`（别名 `moneybook`，密码见 `local.properties`，均已加入 `.gitignore` 不会提交）。若没有 `local.properties` 中的签名配置，release 仍可构建，只是产物为未签名 APK。

### 安装到小米 HyperOS 3.0.5.0

- 直接安装 `app-release.apk`（体积小）或 `app-debug.apk`；如系统提示，允许「未知来源应用」安装即可。
- 本应用无后台服务、无推送、无网络，不存在被 HyperOS 省电策略限制的问题。

## 工程结构（全部文件路径）

```
G:\Andersonlin4-design 记账工具\
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradlew / gradlew.bat
├─ gradle\
│  ├─ libs.versions.toml
│  └─ wrapper\
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties
└─ app\
   ├─ build.gradle.kts
   ├─ proguard-rules.pro
   └─ src\main\
      ├─ AndroidManifest.xml
      ├─ res\
      │  ├─ values\strings.xml / themes.xml / colors.xml
      │  ├─ drawable\ic_launcher_foreground.xml
      │  └─ mipmap-anydpi-v26\ic_launcher.xml / ic_launcher_round.xml
      └─ java\com\andersonlin\moneybook\
         ├─ MoneyBookApp.kt                     （Application 容器）
         ├─ MainActivity.kt                     （入口 Activity + 应用锁门禁 + 小组件跳转）
         ├─ data\
         │  ├─ model\Bill.kt / Category.kt / Account.kt / Budget.kt / RecurringBill.kt
          │  ├─ model\DefaultCategories.kt / DefaultAccounts.kt
         │  ├─ db\BillDao.kt / CategoryDao.kt / AccountDao.kt / BudgetDao.kt / RecurringBillDao.kt / AppDatabase.kt
         │  ├─ repository\BillRepository.kt / CategoryRepository.kt / AccountRepository.kt
          │  ├─ repository\BudgetRepository.kt / RecurringRepository.kt
         │  ├─ settings\SettingsRepository.kt / LockSettingsRepository.kt
         │  └─ backup\BackupManager.kt          （JSON 备份 + CSV 导出）
         ├─ util\Format.kt                      （金额、日期工具）
          ├─ widget\MoneyBookWidget.kt / MoneyBookWidgetReceiver.kt
         └─ ui\
            ├─ AppViewModelProvider.kt          （ViewModel 工厂）
            ├─ theme\Theme.kt
            ├─ navigation\AppNavHost.kt         （路由 + 底部导航）
            ├─ components\Common.kt / EmojiChoices.kt
            ├─ home\HomeViewModel.kt / HomeScreen.kt
            ├─ bill\BillListViewModel.kt / BillListScreen.kt
            ├─ bill\AddEditBillViewModel.kt / AddEditBillScreen.kt
            ├─ stats\StatsViewModel.kt / StatsScreen.kt / PieChart.kt
            ├─ category\CategoryViewModel.kt / CategoryScreen.kt
            ├─ account\AccountViewModel.kt / AccountScreen.kt
            ├─ budget\BudgetViewModel.kt / BudgetScreen.kt
            ├─ recurring\RecurringViewModel.kt / RecurringScreen.kt
            ├─ lock\LockViewModel.kt / LockScreen.kt / LockSettingsScreen.kt
            └─ settings\SettingsViewModel.kt / SettingsScreen.kt / AboutScreen.kt
```

## 架构说明

- **MVVM**：`Room DAO → Repository → ViewModel（StateFlow）→ Compose UI`，UI 只依赖状态与事件。
- 金额以「分」（Long）存储，展示时格式化，杜绝浮点误差。
- 日期以 `epochDay` 存储（只到天），按月区间查询汇总。
- 备份 JSON 结构：

```json
{
  "app": "moneybook",
  "version": 1,
  "exportedAt": "2024-05-12T10:00:00",
  "categories": [ { "id":1, "name":"餐饮", "type":0, "icon":"🍜", "isDefault":true, "sortOrder":1 } ],
  "bills": [ { "id":1, "type":0, "amountCents":3500, "categoryId":1, "note":"午饭", "dateEpochDay":19864, "createdAt":1715480000000 } ]
}
```

导入会校验文件格式与引用完整性，再在事务中整体覆盖。

## 版本

- AGP 8.5.2 / Gradle 8.9 / Kotlin 1.9.24 / Compose BOM 2024.06.00 / Room 2.6.1 (KSP)
- compileSdk 34 / targetSdk 34 / minSdk 28

## GitHub 发布（待办）

发布到 GitHub 前需先与你确认 GitHub 账户登录方式，并按你的要求先清理 GitHub 上已烂尾的旧项目，再推送本工程。
