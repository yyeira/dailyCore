# 按天拼图（Daily Collage）

一个 Android 原生应用，自动将相册照片按日期分组，一键生成精美拼图。

## 功能亮点

- **按日期自动分组** — 选择日期范围，照片按天自动归类
- **智能布局** — 13 种布局规则，AUTO 模式根据图片比例智能评分选最优
- **全屏裁切编辑器** — 点击格子打开全屏编辑，完整原图 + 裁切框，拖拽缩放精确调整
- **灵活编辑** — 交换顺序、移除/添加图片、一键重置
- **多种输出比例** — 原始、1:1、4:3、3:4、16:9、9:16
- **日期水印** — 自动添加拍摄日期标记
- **无黑边白边** — center-crop 缩放，拼图干净整洁
- **可选删除原图** — 拼图后释放存储空间，Android 11+ 系统级确认保护

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- 单 Activity + ViewModel (MVVM)
- 原生 BitmapFactory + Canvas 图片处理（无第三方图片库）
- 最低 Android 8.0（API 26），目标 Android 15（API 35）

## 构建

### 前置条件

- Android Studio Ladybug 或更高版本
- JDK 17
- Android SDK 35

### 编译运行

```bash
# 克隆仓库
git clone https://github.com/yyeira/dailyCore.git
cd dailyCore

# 编译 Debug APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

或在 Android Studio 中直接打开项目，点击 Run。

## 项目结构

```
app/src/main/java/com/yyeira/dailycollage/
├── MainActivity.kt              # Compose UI 层
├── CollageViewModel.kt          # 业务逻辑与状态管理
├── model/
│   ├── GalleryImage.kt          # 图片模型
│   ├── ImageDimensions.kt       # 图片尺寸
│   ├── CollageLayout.kt         # 布局模型（画布 + 单元格）
│   ├── LayoutRule.kt            # 13 种布局规则枚举
│   ├── OutputAspectRatio.kt     # 6 种输出比例枚举
│   ├── CropOffset.kt           # 裁切偏移（x/y 锚点 + 缩放）
│   └── DayPreview.kt           # 每日预览聚合模型
├── domain/
│   ├── ImageGrouper.kt          # 按日期分组
│   ├── ImageDimensionResolver.kt# 读取图片尺寸
│   ├── CollageLayoutPlanner.kt  # 布局规划算法
│   ├── GridCollageMaker.kt      # 拼图渲染 + 水印
│   └── OutputAspectRatioFitter.kt # 输出比例适配
├── data/
│   └── GalleryRepository.kt    # MediaStore 数据源
└── util/
    ├── PermissionHelper.kt      # 权限适配
    ├── ThumbnailDecoder.kt      # 缩略图/大图解码
    ├── ImageSaver.kt            # 保存到相册
    ├── ImageDeleter.kt          # 删除原图
    └── LayoutDescriptionFormatter.kt # 布局描述本地化
```

## 权限

| 权限 | 版本范围 | 用途 |
|---|---|---|
| `READ_MEDIA_IMAGES` | Android 13+ | 读取相册图片 |
| `READ_EXTERNAL_STORAGE` | Android 12 及以下 | 读取相册图片 |
| `WRITE_EXTERNAL_STORAGE` | Android 9 及以下 | 写入存储 |

## 文档

- [产品需求文档 (PRD)](docs/PRD.md)
- [技术架构](docs/ARCHITECTURE.md)
- [变更日志](CHANGELOG.md)
- [贡献指南](CONTRIBUTING.md)

## 许可证

私有项目，保留所有权利。
