# Mote Plugin API v1

Mote 插件是可单独安装、更新和卸载的 Android APK。主应用仅接受结构化数据，不加载插件 View、Activity 实例、ClassLoader 或笔记内部文件路径。

## 模块

- `:plugin-api`：AIDL、数据模型与 `MotePluginService` 基类。
- `:app`：发现、绑定、授权、超时/取消、会话验证、结果预览与原子应用。
- `:plugins:ai-organizer`：不是主应用依赖项的独立示例 APK。

## 声明 Service

```xml
<service
    android:name=".ExamplePluginService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.mlevngr.mote.action.PLUGIN_SERVICE" />
    </intent-filter>
    <meta-data
        android:name="com.mlevngr.mote.plugin.API_VERSION"
        android:value="1" />
    <meta-data
        android:name="com.mlevngr.mote.plugin.ID"
        android:value="example.plugin" />
</service>
```

Service 继承 `MotePluginService`，必须实现：

- 稳定的 `PluginDescriptor`、action ID 和 capability 集合。
- `isCallerAllowed(uid)`：验证调用者是目标 Mote host；默认基类不会放行未验证调用者。
- 异步 `execute(request, callback)` 和可中断的 `cancel(requestId)`。

## Capability

v1 定义 `READ_SELECTION`、`READ_FULL_NOTE`、`MODIFY_TEXT`、`MODIFY_STRUCTURE`、`READ_ATTACHMENTS`、`CREATE_ATTACHMENTS`、`NETWORK_ACCESS`、`VOICE_INPUT`、`EXPORT_NOTES` 与 `SYNC_PROVIDER`。

描述中的 API 版本、插件 ID 或 capability 变化会改变授权指纹，主应用会重新请求用户同意。高版本插件不会被低版本 host 执行。

## 请求与结果

`PluginRequest` 携带 `sessionId`、`requestId`、`baseRevision`、action、标题、Markdown 快照与可选选区。`PluginResult` 必须原样返回三个 ID/版本字段，并提供完整候选 Markdown。

Host 只在以下条件全部满足时接受结果：

1. 输入会话仍然有效。
2. 请求未取消，插件仍然连接。
3. 笔记 SHA-256 版本与发起请求时相同。
4. 结果非空且不超过大小上限。
5. 附件引用与 PDF 页间笔记结构标记未被篡改。
6. 用户在 host-owned 预览中确认应用。

## 热插拔与线程

Host 监听包安装、卸载和更新，刷新可用 action。每次执行都是短生命周期 Service 绑定，完成、错误、取消、超时或 Binder death 后立即解绑。

- UI 渲染、用户授权和结果应用：主线程。
- Binder 调用：host 专用 executor，不阻塞编辑器。
- Binder callback：先验证/解析，再 dispatch 到主线程。
- 插件网络、AI、语音识别或大型解析：插件自有后台 executor。

卸载插件只会终止该插件请求；Mote 的笔记存储、Markdown 编辑、附件、PDF、回收站与 WebDAV 不依赖任何插件 APK。
