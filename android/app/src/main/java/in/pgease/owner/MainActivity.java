package in.pgease.owner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private OwnerShell shell;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        CookieManager.getInstance().setAcceptCookie(true);
        SystemBarStyling.enable(this);
        super.onCreate(savedInstanceState);
        removeTitleBar();
        SystemBarStyling.enable(this);
        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.WHITE);

        shell = new OwnerShell(this);
        if (getBridge() != null) {
            shell.install();
            shell.openAppLink(getIntent());
        }

        getOnBackPressedDispatcher().addCallback(
            this,
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (shell != null) {
                        shell.onBackPressed();
                    }
                }
            }
        );

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().evaluateJavascript("if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) { window.Capacitor.Plugins.SplashScreen.hide(); }", null);
            }
        }, 3000);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        removeTitleBar();
    }

    @Override
    public void onResume() {
        super.onResume();
        removeTitleBar();
    }

    private void removeTitleBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        int containerId = getResources().getIdentifier("action_bar_container", "id", "android");
        if (containerId != 0) {
            View actionBar = findViewById(containerId);
            if (actionBar != null && actionBar.getParent() instanceof ViewGroup) {
                ((ViewGroup) actionBar.getParent()).removeView(actionBar);
            } else if (actionBar != null) {
                actionBar.setVisibility(View.GONE);
            }
        }
        View decor = getWindow().getDecorView();
        ViewCompat.requestApplyInsets(decor);
        decor.post(() -> ViewCompat.requestApplyInsets(decor));
    }

    @Override
    public void onPause() {
        if (shell != null) {
            shell.flushCookies();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shell != null) {
            shell.openAppLink(intent);
        }
    }
}
