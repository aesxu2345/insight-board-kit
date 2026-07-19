# UIKitInsight AAR 接入文档

构建日期：2026-07-14

## 1. 环境要求

- Android `compileSdk`: **36.1**
- Android `minSdk`: 24
- Java: 11 或更高
- WebView 必须允许 JavaScript（SDK 默认创建的 WebView 已启用）
- 宿主需要网络权限；AAR 会声明 `android.permission.INTERNET` 供 manifest 合并

## 2. 引入 AAR

将 `UIKitInsight-release.aar` 放入应用模块的 `libs/` 目录，然后配置：

```kotlin
dependencies {
    implementation(files("libs/UIKitInsight-release.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
}
```

UIKitInsight 自身不要求 Compose，也不会把宿主应用升级到 API 37。

## 3. 创建配置

```java
import com.uikit.insight.UIInsightPlayConfig;

final class InsightConfig implements UIInsightPlayConfig {
    @Override public String getIp() {
        return "127.0.0.1";
    }

    @Override public String getFirstRoute() {
        return "https://example.com/<randomString>/<signature>";
    }

    @Override public String getSecondRoute() {
        return "https://example.com/<randomString>/<signature>";
    }

    @Override public boolean getBypass() {
        return false;
    }
}
```

字段说明：

| 字段 | 用途 |
| --- | --- |
| `ip` | 在线 HTML5 后端或业务地址标识 |
| `firstRoute` | 第一幕 WebProvider 百分比 JSON 路由，必须是 `http` 或 `https` 签名 URL；传入主路由时 SDK 会自动补成 `/<randomString>/<signature>/webprovider` |
| `secondRoute` | 第二幕 HTML5 内嵌浏览器路由，必须是 `http` 或 `https` 签名 URL，SDK 原样交给 GeckoView 加载 |
| `bypass` | 是否绕过签名路由处理；为 `true` 时任何非空路由字符串都会原样交给 WebView，不解析 URL 结构、不要求 `/<nonce>/<signature>`，也不会为第一幕补 `/webprovider`。生产环境必须为 `false` |

仓库中的 `test` Demo 有意使用 `bypass=true` 和普通 localhost URL，用于稳定验证第一幕 N/A、Android WebView ERR 以及再次上拉切换 N/A；它不是生产配置示例。

## 4. 初始化

使用默认 UI 参数：

```java
NewUIInsightPlay insight = NewInsightKt.NewInsight(
        new InsightConfig(),
        new UIInsightCss()
);
```

完整样式构造参数：

```java
UIInsightCss css = new UIInsightCss(
        "uikit_insight/index.html",
        18f,
        156f,
        88f,
        "#f7faf7",
        "#18813b"
);

NewUIInsightPlay insight = NewInsightKt.NewInsight(
        new InsightConfig(),
        css
);
```

`NewInsight(...)` 返回的缓冲区包含 `ip`、处理后的 `firstRoute`、`secondRoute`、`bypass`、CSS 配置、WebView creator 和已初始化的 `UIEvent` 空事件结构。`bypass=false` 时，初始化阶段会用 AAR 内置的 `route-public.pem` 公钥等价内容分别验证两个签名路由，并为第一幕路由补全 `/webprovider`。除提取 nonce/signature 所需的前两段路径外，SDK 不再叠加 scheme、host、字符正则或路径总段数检查，路由保护只有 RSA 验签一层。`bypass=true` 时只检查路由字符串非空，随后原样交给网络栈；不解析 URL 结构、不解析 `/<nonce>/<signature>`、不执行 RSA 验签，也不改写第一幕路径、查询参数或 fragment。空路由或真实验签失败会被拒绝加载，但不会抛出异常导致宿主 Activity 或 Compose 首帧崩溃；WebView 创建时会用 Toast 提示初始化错误，原因同时记录在 Logcat 的 `UIKitInsight` 标签下。第一幕会在原生后台线程读取 `firstRoute` 返回的 `{"completed":{"count":151,"percentage":74},"pending":{"count":53,"percentage":26},"total":204}` 结构，再异步回灌 WebView，不会阻塞首屏触摸；等待期间、路由无效、读取失败或数据无效时，中心显示 `N/A`，图例保持 `0 / 0`，环形本体以 1% 状态保留可见。第二幕首次从第一幕上滑完全展开后创建 GeckoSession 并加载 `secondRoute`；GeckoView 一显示就立即接收触摸，不依赖页面完成或首帧合成回调。页面加载错误由 GeckoView 自身显示并保持可交互，SDK 不再隐藏浏览器或切换第二幕 N/A。等待时间、页面颜色、回调顺序和设备性能都不会否决浏览器。低性能模式只减少本地壳层的动画和阴影，不禁用 GeckoView，也不改变第二幕的浏览器优先级。

AAR 会在接管宿主创建的 WebView 后统一开启 JavaScript、DOM Storage、数据库存储、图片加载、第三方 Cookie 和兼容混合内容模式，并恢复 `LOAD_DEFAULT` 缓存策略；所有设置和导航客户端安装完成后，AAR 才会统一加载内置入口。因此自定义 `UIInsightCreator` 只需负责创建和布局 WebView，不要提前调用 `loadUrl(...)`、`clearCache(true)`，也不要强制设置 `LOAD_NO_CACHE`。第二幕由原生 GeckoView 加载，宿主必须依赖文档开头指定的 GeckoView 版本；AAR 使用 `compileOnly`，不会把 200MB 级 Gecko 运行库重复打进 AAR。SDK 为单个内嵌页关闭 Fission/站点隔离并限制为单内容进程，同时启用 Gecko 低内存检测，以降低与壳 WebView 同时存在时的进程和内存压力；这些设置不会禁用页面加载。

Compose `AndroidView` 必须返回一个允许同时容纳 WebView 与 GeckoView 的 `FrameLayout`，并调用 `insight.Display(context, frameLayout)`。不要让 `AndroidView.factory` 直接返回 `insight.Display(UIInsightCreator)` 得到的 WebView；Compose 的 `AndroidViewHolder` 只管理该 WebView，后加的 GeckoView 会落到错误层级并被壳页面遮挡。

第一幕会根据 WebView 的实际可用高度缩放环形图；宿主使用 `enableEdgeToEdge()` 与 `navigationBarsPadding()` 避让底部三键导航栏时，不需要注入 JavaScript 修改图表尺寸。卡片触摸隔离、内部拖动和低性能模式仍由 AAR 自身管理。

`NewInsight` 还预留了空实现方法 `OnCardNo(String cardno)`。当前版本调用该方法不会触发业务行为，接入方可以安全保留调用位置，供后续卡号流程扩展。

第二幕会监控与 `secondRoute` 同源的虚拟导航 `/invalid/exam/<体检编号>`。只有路径严格由 `invalid`、`exam` 和一个非空值三段组成时，AAR 才会 URL 解码最后一段、调用 `OnCardNo(cardno)`，并返回 `true` 阻止 WebView 加载该无效页面。例如第二幕触发 `/invalid/exam/TJ000001` 时，会调用 `OnCardNo("TJ000001")`。普通 404、签名失败以及仅包含 `invalid` 或 `exam` 字样的其他 URL 均不会触发。

## 5. 注册 Sidebar 业务事件

在 `Display()` 之前调用 `OnClickUIEvent()`，使用真实业务结构整体替换默认空方法：

```java
insight.OnClickUIEvent(new UIEventStruct() {
    @Override public void onOpenScanner() {
        // 打开扫码；第一幕扫码按钮与 Sidebar 入口共用此事件
    }

    @Override public void onManualBarcodeInput() {
        // 手动输入条码
    }

    @Override public void onConfigureBackendAddress() {
        // 配置后端地址
    }

    @Override public void onCameraInfraredSwitch() {
        // 相机/红外切换
    }

    @Override public void onOpenSourceLicenses() {
        // 展示开放源代码许可
    }

});
```

事件由 WebView JavaScript Bridge 转发，并最终在 Android 主线程执行。

第一幕的“下拉打开扫码”支持轻点和下拉手势。向下拖动时绿色幕布与弧形波浪随手指伸展；达到阈值后松手会调用 `onOpenScanner()`，随后幕布自动回弹。Sidebar 的“打开扫码”入口调用同一个方法。Sidebar 在小尺寸屏幕上使用全高面板，菜单入口区域可独立纵向滚动，底部关闭入口保持可达。饼图卡片拥有独立的纵向触摸区：小屏上拖可查看被卡片边界遮挡的图例，下拖恢复环形图，不会误触幕间切换。低内存、受限堆或低核心数设备会自动启用低性能模式，保留全部手势但关闭阴影和逐帧回弹。

## 6. 显示 UI

```java
insight.Display(this);
```

`Display(Activity)` 会调用 `Activity.setContentView(WebView)`，强制使用 UIKitInsight 覆盖 Activity 当前显示的 XML、Compose 或其他 View。

非 Activity Context 必须提供容器：

```java
insight.Display(context, container);
```

如果 `context` 能解析到 Activity，即使同时传入 `container`，SDK 仍优先覆盖 Activity 根视图。

## 7. 自定义 WebView Creator

```kotlin
val webView = insight.Display { _ ->
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
```

自定义 Creator 返回的 WebView 不会自动挂载到 Activity 或 ViewGroup；宿主负责挂载。SDK 会统一配置 WebView、注入事件 Bridge，并在配置完成后加载内置 asset 页面。

## 8. 销毁

建议在 Activity 的 `onDestroy()` 中调用：

```java
@Override
protected void onDestroy() {
    if (insight != null) {
        insight.Destory();
    }
    super.onDestroy();
}
```

兼容方法 `Destroy()` 与 `Destory()` 行为相同。销毁时 SDK 会：

- 从父容器移除 WebView；
- 移除 JavaScript Bridge；
- 停止加载并清空历史；
- 销毁 WebView；
- 将 `UIEventStruct` 恢复为空占位结构。

## 9. 混淆

AAR 已携带 consumer ProGuard 规则，用于保留 `com.uikit.insight` API 和 JavaScript Bridge。宿主正常启用 R8 即可。

## 10. 许可证与分发

UIKitInsight 可依据以下任一授权使用：

- PolyForm Free Trial 1.0.0；
- 有效的 UIKitInsight Commercial Integration License。

商业许可可在约定范围内允许闭源集成、生产使用和集成产品分发。公开材料提及 UIKitInsight 时必须保留 `NOTICE` 中的广告致谢，不得冒充项目方或贡献者背书。`NOTICE` 本身不增加任何额外授权。

分发包附带：

- `LICENSE`
- `NOTICE`
- `COMMERCIAL-INTEGRATION-LICENSE.md`

## 11. 产物

- AAR：`UIKitInsight-release.aar`
- 包名：`com.uikit.insight`
- 内置入口：`file:///android_asset/uikit_insight/index.html`
