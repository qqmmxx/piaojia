# 票夹 · FaP2

一个自己用的 Android 报销票据管理小工具：按项目归集差旅票据，随手拍照存档，最后一键导成带图的 Excel 交给财务。

用 Kotlin + Jetpack Compose 写的，纯本地存储、不联网。

## 功能

- **项目管理**：新建 / 编辑 / 删除项目，标记完成或恢复为进行中，按状态筛选
- **报销记录**：四种费用类型（机票/高铁、滴滴/出行、酒店住宿、其他费用），金额、描述、时间，可挂多张票据照片
- **费用统计**：项目内按类型小计 + 总计，列表页显示每个项目的笔数与总额
- **票据照片**：选中的照片会复制进应用私有目录，删除报销时同步清理，不留垃圾文件
- **导出**：走系统文件保存（SAF）
  - 没有照片 → 导出 `.xlsx`，含「统计概览」和每个项目一张明细表
  - 有照片 → 导出 `.zip`，内含一份 `.xlsx` 和 `images/` 目录下的原图
- **导入**：读取 `.xlsx`，自动识别表头所在行、跳过小计/总计行，每个含有效表头的工作表导入为一个项目
- **配色自定义**：设置 → 配色，12 个预设色 + 色相微调，带实时预览，选择会记住。整套 Material 3 配色（浅色 / 深色）由你选的颜色推导出来

## 截图

| 项目列表 | 项目详情 | 新增报销 | 配色自定义 |
| --- | --- | --- | --- |
| ![项目列表](docs/screenshots/02-project-list.png) | ![项目详情](docs/screenshots/03-project-detail.png) | ![新增报销](docs/screenshots/04-add-expense.png) | ![配色](docs/screenshots/05-color-picker.png) |

## 构建

环境要求：JDK 17+、Android SDK（compileSdk 35）。

在仓库根目录建一个 `local.properties` 指向本机 SDK：

```properties
sdk.dir=/path/to/Android/Sdk
```

然后：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

装到设备上：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 构建时把 `--project-cache-dir` 指到本地磁盘可以规避 Gradle 项目缓存目录被占用/拒绝访问导致的 `fileHashes.lock` 报错：
> `./gradlew assembleDebug --project-cache-dir=/path/to/tmp/gcache`

## 技术栈

| 用途 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3（配色由用户选定，不用动态取色） |
| 本地存储 | Room 2.6.1（`fallbackToDestructiveMigration`） |
| 偏好存储 | SharedPreferences（存配色种子色） |
| 图片加载 | Coil 2.5 |
| Excel 读写 | Apache POI 5.2.5 |
| 异步 | Kotlin Coroutines + Flow |

最低支持 Android 8.0（API 26），目标 API 35。

## 目录结构

```
app/src/main/java/com/qqmmxx/piaojia/
├── MainActivity.kt              单一 Activity + 手写两页导航 + 双击退出
├── data/
│   ├── AppDatabase.kt           Room 数据库
│   ├── ImageManager.kt          票据图片的复制 / 解析 / 清理
│   └── dao/                     ProjectDao / ExpenseDao
├── model/                       实体、领域模型、金额格式化
├── repository/                  ExpenseRepository
├── viewmodel/                   ExpenseViewModel（含 Excel 导入导出）
├── ui/screens/                  列表页 / 详情页 / 两个表单弹窗 / 图片选择器 / 配色选择
└── ui/theme/                    配色生成（ThemeColors）、配色落盘（ThemePreferences）
```

## 配色是怎么来的

`ui/theme/ThemeColors.kt` 从用户选定的一个种子色推导出整套 Material 3 配色，**没有引入任何颜色库**。

刻意没用 Material 官方的色调映射（HCT 的 tone 40 / 90）：那套算法为了保证白字对比度会把主色大幅压暗，
橘色种子 `#E8710A` 会被算成 `#994700`（偏棕），于是「选的颜色」和「界面上看到的颜色」对不上。

这里改成「选什么色，主色就是什么色」：主色直接用选中的颜色，只在对比度低于 4.5:1 时才自动微调明度；
容器色是该色相的浅色调；中性面按种子色色相做极轻微染色，避免出现与主色不搭的灰。

## 已知限制

- 导入只还原文本数据，不含照片（导出 zip 里的图片不会回填）
- 数据库升级走 `fallbackToDestructiveMigration`，改 schema 会清空本地数据；建议先导出备份
- 报销的日期默认取当前时间，暂时不支持手动修改（补录旧票据时需要先改代码）
- `exportSchema = true`，schema 快照在 `app/schemas/`

## License

[MIT](LICENSE)
