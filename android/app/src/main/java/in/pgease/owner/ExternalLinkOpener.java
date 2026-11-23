package in.pgease.owner;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

/** Sends tel, mailto, UPI, WhatsApp, and other non-app links out of the WebView. */
public final class ExternalLinkOpener {

    private ExternalLinkOpener() {}

    public static void openApp(Activity activity, Uri uri) {
        if (uri == null) {
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        try {
            if ("intent".equals(scheme)) {
                openIntentUri(activity, uri);
                return;
            }
            Intent intent;
            if ("mailto".equals(scheme)) {
                intent = new Intent(Intent.ACTION_SENDTO, uri);
            } else if ("tel".equals(scheme)) {
                intent = new Intent(Intent.ACTION_DIAL, uri);
            } else if ("sms".equals(scheme) || "smsto".equals(scheme)) {
                intent = new Intent(Intent.ACTION_SENDTO, uri);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            activity.startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            toast(activity, R.string.no_app_for_link);
        } catch (Exception ex) {
            toast(activity, R.string.no_app_for_link);
        }
    }

    public static void openBrowser(Activity activity, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(SystemBarStyling.BRAND)
                .setNavigationBarColor(SystemBarStyling.BRAND)
                .build();
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(colors)
                .build();
            tabs.launchUrl(activity, uri);
        } catch (ActivityNotFoundException ex) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException nested) {
                toast(activity, R.string.no_app_for_link);
            }
        }
    }

    private static void openIntentUri(Activity activity, Uri uri) {
        try {
            Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            activity.startActivity(intent);
        } catch (Exception ex) {
            String fallback = null;
            try {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                fallback = parsed.getStringExtra("browser_fallback_url");
            } catch (Exception ignored) {
                fallback = null;
            }
            if (fallback != null && !fallback.isEmpty()) {
                openBrowser(activity, Uri.parse(fallback));
            } else {
                toast(activity, R.string.no_app_for_link);
            }
        }
    }

    private static void toast(Activity activity, int message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
