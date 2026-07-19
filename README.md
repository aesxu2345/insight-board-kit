# UIKitInsight

UIKitInsight is an Android AAR UI kit for the breakfast insight experience.
The local UIKit shell uses WebView; the remote second scene uses Mozilla
GeckoView and does not use an iframe or Android System WebView.

## GeckoView dependency

Local AAR consumers must add Mozilla's Maven repository and GeckoView:

```kotlin
repositories { maven("https://maven.mozilla.org/maven2/") }
dependencies {
    implementation("org.mozilla.geckoview:geckoview:152.0.20260713164047")
}
```

GeckoView 152 requires Java 17 and Android minSdk 26.

## Signed routes

`UIInsightPlayConfig.firstRoute` and `secondRoute` normally use the `/<randomString>/<signature>` format from `insight2node`; `NewInsight(...)` verifies both routes with the hardcoded SPKI public key and appends `/provider` to the first route. Set the explicit `bypass` field to `true` only while testing N/A and browser transitions. In bypass mode, every non-blank route string is passed to the relevant network loader unchanged: URL structure is not prevalidated, nonce/signature segments are not parsed, RSA verification is skipped, and the first route is not rewritten. With bypass disabled, RSA verification is the only route protection. Production integrations must use `bypass = false`.


## Sidebar events

Every `NewInsight(...)` result owns an initialized `UIEvent` buffer whose
`UIEventStruct` methods are no-ops until business callbacks replace them:

```java
NewUIInsightPlay insight = NewInsightKt.NewInsight(config, css);
insight.OnClickUIEvent(new UIEventStruct() {
    @Override public void onOpenScanner() { /* open scanner */ }
    @Override public void onManualBarcodeInput() { /* business action */ }
    @Override public void onConfigureBackendAddress() { /* business action */ }
    @Override public void onCameraInfraredSwitch() { /* business action */ }
    @Override public void onOpenSourceLicenses() { /* business action */ }
});
insight.Display(activity);
```

Sidebar callbacks are dispatched on the Android main thread. `Destory()`
removes the JavaScript bridge and restores the empty placeholder structure.

## Licensing

UIKitInsight is available under:

- PolyForm Free Trial License 1.0.0 for default trial/evaluation use; or
- UIKitInsight Commercial Integration License for authorized commercial use.

The Commercial Integration License permits closed-source integration,
production use, and distribution within the agreed written scope.

See:

- [LICENSE](LICENSE)
- [COMMERCIAL-INTEGRATION-LICENSE.md](COMMERCIAL-INTEGRATION-LICENSE.md)
- [NOTICE](NOTICE)

Redistributions and public materials mentioning UIKitInsight must include the
NOTICE acknowledgement and must not imply endorsement by UIKitInsight, the
project owner, or contributors.
