# InkNote Android

一个独立、离线优先的 Android Markdown 笔记应用。

## 下载 APK

**[直接下载 InkNote 0.2.0 APK](https://github.com/Mlevngr/note/releases/download/v0.2.0/InkNote-0.2.0-debug.apk)**

也可以进入 [GitHub Releases](https://github.com/Mlevngr/note/releases) 选择最新版本。APK 使用 Android Debug 签名，适合当前测试阶段直接安装。

## 块级编辑与实时预览

![InkNote 块级编辑和图片/PDF内嵌预览示意](docs/inline-preview.svg)

InkNote 使用混合编辑模式，而不是上下分栏：

- 默认所有段落都是渲染后的实时预览。
- 点击一个段落时，仅该段落显示 Markdown 源码编辑框。
- 点击另一个段落或“完成”，刚才的段落立即恢复渲染。
- 图片直接显示；PDF 展开为真实页面，并只渲染屏幕附近页面。
- GitHub README 不会把 PDF 链接展开成页面，但 InkNote 应用内会。

## 当前 MVP

- Plain Markdown source stored as `note.md`
- Hybrid block editor: only the active block shows Markdown source
- Every inactive block remains rendered as live preview
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
