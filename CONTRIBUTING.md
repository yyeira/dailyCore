# 贡献指南

感谢你对按天拼图（Daily Collage）项目的关注！以下是参与贡献的说明。

## 开发环境

- **Android Studio** Ladybug 或更高版本
- **JDK** 17
- **Android SDK** 35（compileSdk）
- **Kotlin** 版本由 `libs.versions.toml` 管理

## 项目约定

### 代码风格

- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 缩进使用 4 个空格
- 函数参数超过 3 个时每个参数独占一行，末尾加逗号（trailing comma）

### 架构原则

- **UI 层**：纯 Compose，不持有业务状态
- **ViewModel 层**：通过 `StateFlow` 暴露 UI 状态，所有副作用在 `viewModelScope` 内执行
- **Domain 层**：无 Android 框架依赖（除 `ContentResolver`），便于单元测试
- **坐标系统**：使用归一化坐标（0.0 ~ 1.0），不依赖具体分辨率

### 命名规范

| 类型 | 规范 | 示例 |
|---|---|---|
| Composable | PascalCase | `PreviewDayCard` |
| ViewModel 方法 | camelCase，动词开头 | `updateCropOffset` |
| Domain 类 | 名词，描述职责 | `CollageLayoutPlanner` |
| Model | 数据类 | `CropOffset`, `DayPreview` |
| 枚举值 | SCREAMING_SNAKE_CASE | `HERO_LEFT`, `RATIO_4_3` |

### 字符串资源

- 所有用户可见文案放在 `res/values/strings.xml`
- 使用 `stringResource(R.string.xxx)` 引用，不硬编码

## 提交规范

提交信息使用中文，简明描述变更内容：

```
<类型>：<简要描述>

<可选的详细说明>
```

类型参考：
- **新增** — 新功能
- **修复** — Bug 修复
- **优化** — 性能或体验改进
- **重构** — 代码重构，不改变外部行为
- **文档** — 文档更新

示例：
```
新增：全屏裁切编辑器

- 点击格子打开全屏 Dialog
- 完整原图 + 半透明遮罩 + 裁切框
- 支持拖拽平移和双指缩放
```

## 分支策略

- `main` — 主分支，保持可编译状态
- 功能开发建议在独立分支上进行，完成后合并到 `main`

## 构建与测试

```bash
# 编译
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 项目文档

- [README](README.md) — 项目概览
- [PRD](docs/PRD.md) — 产品需求文档
- [架构文档](docs/ARCHITECTURE.md) — 技术架构
- [变更日志](CHANGELOG.md) — 版本历史
