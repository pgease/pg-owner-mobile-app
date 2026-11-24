package in.pgease.owner;

import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Brand-coloured system bars that stay clear of the WebView on Android 15+. */
public final class SystemBarStyling {

    public static final int BRAND = 0xFF008080;

    private SystemBarStyling() {}

    public static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity, SystemBarStyle.dark(BRAND), SystemBarStyle.dark(BRAND));
        Window window = activity.getWindow();
        window.setStatusBarColor(BRAND);
        window.setNavigationBarColor(BRAND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    /** Pads {@code view} by the status bar, navigation bar, and cutout. */
    public static void padForSystemBars(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int top = Math.max(status.top, cutout.top);
            int bottom = nav.bottom;
            int left = Math.max(Math.max(status.left, nav.left), cutout.left);
            int right = Math.max(Math.max(status.right, nav.right), cutout.right);
            v.setPadding(left, top, right, bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
