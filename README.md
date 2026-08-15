# InkNote Android

A small, local-first Android notes app. This repository is independent from the Fcitx/input-method work.

## Current MVP

- Plain Markdown source stored as `note.md`
- Debounced live preview while typing
- CommonMark rendering with headings, emphasis, lists, quotes, tables, task lists and strike-through
- Image import through Android's system document picker
- PDF import through Android's system document picker
- Imported images are rendered inline, not as links
- Every PDF page is expanded into the preview and rendered lazily by `RecyclerView`
- Imported assets are copied into the note's private `assets/` directory
- Atomic local autosave; no storage permission and no network access

## Portable asset syntax

InkNote keeps standard Markdown syntax:

```markdown
![photo](assets/uuid.png)
[paper](assets/uuid.pdf)
```

The PDF line is rendered as real pages inside InkNote rather than as a clickable text link.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The first milestone intentionally uses one local note. Notebook management, handwriting and PDF annotation belong to later milestones after the editor/asset model is stable.
