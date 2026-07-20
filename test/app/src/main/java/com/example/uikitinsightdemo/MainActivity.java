package com.example.uikitinsightdemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.uikit.insight.NewInsightKt;
import com.uikit.insight.NewUIInsightPlay;
import com.uikit.insight.OnCardNo;
import com.uikit.insight.UIInsightCss;
import com.uikit.insight.UIInsightPlayConfig;
import com.uikit.insight.UIEventStruct;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private NewUIInsightPlay insight;
    private ExecutorService cardNoExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        UIInsightCss css = new UIInsightCss(
                "uikit_insight/index.html",
                18f,
                156f,
                88f,
                "#f7faf7",
                "#18813b"
        );
        insight = NewInsightKt.NewInsight(new DemoConfig(), css);
        insight.getOnCardNo().enroll(new OnCardNo() {
            @Override
            public void event(String str) {
                runOnUiThread(() -> showSelection("体检编号: " + str));
            }
        });
        cardNoExecutor = Executors.newSingleThreadExecutor();
        cardNoExecutor.execute(() -> insight.getOnCardNo().run());
        insight.OnClickUIEvent(new UIEventStruct() {
            @Override
            public void onOpenScanner() {
                showSelection("打开扫码");
            }

            @Override
            public void onManualBarcodeInput() {
                showSelection("手动输入条码");
            }

            @Override
            public void onConfigureBackendAddress() {
                showSelection("配置后端地址");
            }

            @Override
            public void onCameraInfraredSwitch() {
                showSelection("相机/红外 切换");
            }

            @Override
            public void onOpenSourceLicenses() {
                showSelection("开放源代码许可");
            }

        });
        insight.Display(this);
    }

    private void showSelection(String label) {
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (insight != null) {
            insight.Destory();
        }
        if (cardNoExecutor != null) {
            cardNoExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private static final class DemoConfig implements UIInsightPlayConfig {
        @Override
        public String getIp() {
            return "127.0.0.1";
        }

        @Override
        public String getFirstRoute() {
            return "http://127.0.0.1:8123/status.json?mode=bypass";
        }

        @Override
        public String getSecondRoute() {
            return "http://127.0.0.1:8123/browser?mode=bypass";
        }

        @Override
        public boolean getBypass() {
            return true;
        }
    }
}
