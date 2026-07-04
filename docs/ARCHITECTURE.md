# 技术架构

## 总体架构

采用 **单 Activity + ViewModel (MVVM)** 架构，无 DI 框架，依赖在 ViewModel 构造函数中直接创建。

```
┌─────────────────────────────────────────────┐
│                  UI 层                       │
│  MainActivity.kt (Jetpack Compose)          │
│  ┌─────────────────────────────────────┐    │
│  │ CollageScreen                        │    │
│  │  ├─ 日期选择 / 布局规则 / 输出比例    │    │
│  │  ├─ PreviewDayCard (每日预览卡片)     │    │
│  │  │   ├─ 预览图 (点击→裁切编辑器)      │    │
│  │  │   └─ 缩略图行 (排序/编辑/删除)     │    │
│  │  ├─ CropEditorDialog (全屏裁切)      │    │
│  │  └─ ZoomablePreviewDialog (放大预览)  │    │
│  └─────────────────────────────────────┘    │
└──────────────────┬──────────────────────────┘
                   │ StateFlow<CollageUiState>
┌──────────────────▼──────────────────────────┐
│               ViewModel 层                   │
│  CollageViewModel.kt                         │
│  ┌─────────────────────────────────────┐    │
│  │ 状态管理 (MutableStateFlow)          │    │
│  │ 预览生成 / 拼图保存 / 编辑操作        │    │
│  └──────────────┬──────────────────────┘    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│              Domain 层                       │
│  ┌────────────┐ ┌────────────────────┐      │
│  │ImageGrouper│ │CollageLayoutPlanner│      │
│  └────────────┘ └────────────────────┘      │
│  ┌───────────────────┐ ┌─────────────────┐  │
│  │  GridCollageMaker  │ │OutputAspectRatio│  │
│  │  (渲染 + 水印)     │ │Fitter (比例适配)│  │
│  └───────────────────┘ └─────────────────┘  │
│  ┌──────────────────────┐                    │
│  │ImageDimensionResolver│                    │
│  └──────────────────────┘                    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│              Data 层                         │
│  GalleryRepository (MediaStore 查询)         │
│  ImageSaver / ImageDeleter / ThumbnailDecoder│
└─────────────────────────────────────────────┘
```

## 数据流

```
用户选择日期范围
    │
    ▼
GalleryRepository.queryImages()
    │ 查询 MediaStore，返回 List<GalleryImage>
    ▼
ImageGrouper.groupByDate()
    │ 按 dateKey 分组，返回 Map<String, List<GalleryImage>>
    ▼
ImageDimensionResolver.resolve()
    │ 读取每张图片的 width/height（仅边界，不加载 Bitmap）
    ▼
CollageLayoutPlanner.plan()
    │ 根据图片数量 + 宽高比 + 布局规则，计算 CollageLayout
    ▼
GridCollageMaker.createCollage()
    │ 解码图片 → center-crop 绘制到单元格 → 添加水印
    │ 预览：360px / 最终输出：1080px
    ▼
OutputAspectRatioFitter.fit()
    │ 将自然比例 Bitmap 适配到目标输出比例
    ▼
ImageSaver.save()
    │ JPEG 90% → Pictures/DailyCollage/
    ▼
ImageDeleter.delete() (可选)
    │ Android 11+: createDeleteRequest
    │ Android 10:  ContentResolver.delete
```

## 核心模型

### CropOffset — 裁切坐标模型

采用**归一化坐标**（0.0 ~ 1.0），与分辨率无关。同一个 CropOffset 在 360px 预览和 1080px 输出中产生一致的裁切效果。

```
x = 0.0        x = 0.5        x = 1.0
┌───┐           ┌───┐           ┌───┐
│▓▓▓│░░░░░░░   ░░░│▓▓▓│░░░   ░░░░░░░│▓▓▓│
└───┘           └───┘           └───┘
左对齐           居中            右对齐

scale = 1.0: 图片恰好填满格子（center-crop）
scale = 2.0: 放大 2 倍，看到原图更小的区域
```

### CollageLayout — 布局模型

```
CollageLayout {
    canvasWidth: 1080        # 画布宽度
    canvasHeight: 720        # 画布高度（动态计算）
    cells: [                 # 单元格列表
        CollageCell(imageIndex=0, left=0, top=0, width=540, height=720),
        CollageCell(imageIndex=1, left=540, top=0, width=540, height=360),
        CollageCell(imageIndex=2, left=540, top=360, width=540, height=360),
    ]
    description: "hero_left" # 布局描述键
}
```

## 布局规划算法

### AUTO 模式评分函数

```
score = heightPenalty × 0.3 + variancePenalty × 0.4 + cropPenalty × 0.3

heightPenalty:   总高度偏离目标高度的程度（越小越好）
variancePenalty: 各行高度的方差（越均匀越好）
cropPenalty:     图片被裁切的严重程度（越少越好）
```

AUTO 模式会生成多个候选布局（不同的行分配方案），对每个候选计算 score，选 score 最低者。

### 行内列宽分配

根据图片实际宽高比按比例分配列宽，而非简单等分。假设行高固定为 H：

```
图片 A (宽高比 1.5) → 分配宽度 = 1.5H
图片 B (宽高比 0.75) → 分配宽度 = 0.75H
总宽 = (1.5 + 0.75) × H → 按比例缩放到画布宽度
```

## 图片处理流程

### Center-Crop 缩放

与 Android `ImageView.ScaleType.CENTER_CROP` 一致：取宽/高缩放比的**较大值**，确保图片完全覆盖目标区域，多余部分被裁切。

```
缩放比 = max(cellWidth / imgWidth, cellHeight / imgHeight)

裁切区域由 CropOffset 的 x, y 控制锚点位置
scale 参数控制额外放大倍率
```

### 水印绘制

右下角固定位置，样式：
- 白色文字 + 文字阴影（增强可读性）
- 半透明黑色圆角矩形背景
- 字体大小根据画布宽度自适应

## 线程模型

| 操作 | 线程 | 说明 |
|---|---|---|
| UI 渲染 | Main | Compose 重组 |
| 图片查询 | IO | MediaStore 查询 |
| 图片解码 | IO | BitmapFactory 解码 |
| 拼图渲染 | Default | Canvas 绘制 |
| 文件保存 | IO | MediaStore 写入 |

通过 `viewModelScope.launch` + `withContext(Dispatchers.IO/Default)` 切换。

## 内存策略

| 场景 | 策略 |
|---|---|
| 缩略图列表 | 160px inSampleSize 降采样 |
| 预览拼图 | 360px 画布宽度 |
| 裁切编辑器 | 1200px 单张大图 |
| 最终输出 | 1080px 画布宽度，逐张解码绘制后释放 |
