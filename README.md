# InkNote Android

一个独立、离线优先的 Android Markdown 笔记应用。

## 下载 APK

**[直接下载 InkNote 0.1.0 APK](https://github.com/Mlevngr/note/releases/download/v0.1.0/InkNote-0.1.0-debug.apk)**

也可以进入 [GitHub Releases](https://github.com/Mlevngr/note/releases) 选择最新版本。APK 使用 Android Debug 签名，适合当前测试阶段直接安装。

## 编辑与实时预览

![InkNote 图片与 PDF 内嵌预览示意](docs/inline-preview.svg)

界面上半部分是 Markdown 源文，下半部分是约 140ms 防抖更新的实时预览：

- 普通 Markdown 渲染为标题、正文、列表、引用、表格和任务列表。
- 导入图片后，图片直接显示在预览中。
- 导入 PDF 后，PDF 会展开为真实页面；预览列表仅渲染屏幕附近的页面。
- GitHub 自己的 README 渲染器不会把 PDF 链接展开成页面，但 InkNote 的应用内预览会。

## 当前 MVP

- Plain Markdown source stored as `note.md`
- Debounced live preview while typing
- CommonMark rendering with headings, emphasis, lists, quotes, tables, task lists and strike-through
- Image and PDF import through Android's system document picker
- Imported images are rendered inline, not as links
- Every PDF page is expanded into the preview and rendered lazily by `RecyclerView`
- Imported assets are copied into the note's private `assets/` directory
- Atomic local autosave; no storage permission and no network access

## 可移植资产语法

InkNote 保持标准 Markdown 文件格式：

```markdown
![照片](assets/uuid.png)
[课程讲义](assets/uuid.pdf)
```

源文件中的 PDF 使用标准链接语法保证可移植性，但 InkNote 预览不会显示文字链接，而是显示 PDF 页面。

## 本地数据结构

```text
files/notes/welcome/
├── note.md
└── assets/
    ├── uuid.png
    └── uuid.pdf
```

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

当前里程碑使用一个本地笔记。多笔记管理、手写、PDF 标注和套索编辑属于后续里程碑。
