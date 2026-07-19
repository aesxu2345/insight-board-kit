# UIKitInsight Demo

独立 Android Studio demo 项目，用来验证根项目生成的 `UIKitInsight-release.aar`。

先在根项目构建 AAR：

```bash
./gradlew -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home :UIKitInsight:assembleRelease
```

再构建 demo：

```bash
cd test
./gradlew -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home :app:assembleDebug
```

也可以在 Android Studio 里直接打开 `test/` 目录运行。
