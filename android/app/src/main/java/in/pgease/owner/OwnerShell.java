package in.pgease.owner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Native behavior for the remote owner app: history back, pull-to-refresh,
 * downloads, external links, an offline panel, and gateway popups.
 */
public final class OwnerShell {

    private static final String MODAL_JS =
        "(function(){" +
        "var sandbox=document.getElementById('easebuzz-sandbox-overlay');" +
        "if(sandbox){sandbox.remove();return 'closed';}" +
        "var open=document.querySelector('[role=\"dialog\"][data-state=\"open\"], [role=\"alertdialog\"][data-state=\"open\"], [data-state=\"open\"][vaul-drawer], [vaul-drawer][data-state=\"open\"]');" +
        "if(!open) return 'none';" +
        "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',keyCode:27,which:27,bubbles:true,cancelable:true}));" +
        "var still=document.querySelector('[role=\"dialog\"][data-state=\"open\"], [role=\"alertdialog\"][data-state=\"open\"], [data-state=\"open\"][vaul-drawer], [vaul-drawer][data-state=\"open\"]');" +
        "return still ? 'stuck' : 'closed';" +
        "})()";

    private static final String PULL_JS =
        "(function(){" +
        "if(window.__pgeasePullInstalled) return;" +
        "window.__pgeasePullInstalled=true;" +
        "function blocked(target){" +
        "if(document.querySelector('[role=\"dialog\"][data-state=\"open\"], [role=\"alertdialog\"][data-state=\"open\"], #easebuzz-sandbox-overlay')) return true;" +
        "var el=target;" +
        "while(el && el!==document.documentElement){" +
        "var oy=window.getComputedStyle(el).overflowY;" +
        "if((oy==='auto'||oy==='scroll'||oy==='overlay') && el.scrollHeight>el.clientHeight+8 && el.scrollTop>0) return true;" +
        "el=el.parentElement;}" +
        "return (window.scrollY||document.documentElement.scrollTop||0)>2;}" +
        "document.addEventListener('touchstart',function(e){" +
        "if(window.PGEaseShell&&window.PGEaseShell.setPullEnabled){window.PGEaseShell.setPullEnabled(!blocked(e.target));}" +
        "},{passive:true,capture:true});" +
        "})()";

    private final MainActivity activity;
    private final ActivityResultLauncher<String> notificationPermission;
    private final FileDownloads downloads;
    private NavigationPolicy policy = new NavigationPolicy();
    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ShellWebChromeClient chrome;
    private View offlineOverlay;
    private View popupOverlay;
    private FrameLayout popupContainer;
    private TextView popupTitle;
    private WebView popupWebView;
    private View onboardingView;
    private String homeUrl = "https://app.pgease.com?app=1";
    private boolean paymentFlow;
    private boolean mainFrameFailed;
    private volatile boolean pullAllowed = true;
    private long lastBackAt;

    public OwnerShell(MainActivity activity) {
        this.activity = activity;
        this.notificationPermission = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> downloadsOnPermission()
        );
        this.downloads = new FileDownloads(activity, notificationPermission);
    }

    private void downloadsOnPermission() {
        downloads.onNotificationResult();
    }

    public MainActivity activity() {
        return activity;
    }

    public NavigationPolicy policy() {
        return policy;
    }

    public boolean isPaymentFlow() {
        return paymentFlow;
    }

    public void setPaymentFlow(boolean paymentFlow) {
        this.paymentFlow = paymentFlow;
    }

    public void setOnboardingView(View onboardingView) {
        this.onboardingView = onboardingView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void install() {
        if (activity.getBridge() == null || activity.getBridge().getWebView() == null) {
            return;
        }
        webView = activity.getBridge().getWebView();
        homeUrl = activity.getBridge().getAppUrl() != null ? activity.getBridge().getAppUrl() : homeUrl;
        String configuredHost = NavigationPolicy.hostOf(homeUrl);
        policy = new NavigationPolicy(configuredHost);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.WHITE);

        ViewGroup content = activity.findViewById(android.R.id.content);
        content.setBackgroundColor(Color.WHITE);
        if (content.getChildCount() > 0) {
            content.getChildAt(0).setBackgroundColor(Color.WHITE);
        }

        wrapPullToRefresh();
        webView.addJavascriptInterface(new PullBridge(), "PGEaseShell");
        webView.addJavascriptInterface(new DownloadBridge(), "PGEaseDownloads");

        chrome = new ShellWebChromeClient(activity.getBridge(), this);
        activity.getBridge().setWebViewClient(new ShellWebViewClient(activity.getBridge(), this));
        webView.setWebChromeClient(chrome);
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (swipe != null) {
                swipe.setRefreshing(false);
            }
            if (url == null) {
                return;
            }
            if (url.startsWith("blob:")) {
                String name = FileDownloads.guessName(url, contentDisposition, mimeType);
                String js =
                    "(function(){var url=" +
                    JSONObject.quote(url) +
                    ";var name=" +
                    JSONObject.quote(name) +
                    ";var mime=" +
                    JSONObject.quote(mimeType == null ? "" : mimeType) +
                    ";fetch(url).then(function(r){return r.blob();}).then(function(blob){" +
                    "var reader=new FileReader();reader.onloadend=function(){var data=String(reader.result||'');var i=data.indexOf(',');" +
                    "PGEaseDownloads.save(name, blob.type||mime, i>=0?data.substring(i+1):data);};reader.readAsDataURL(blob);" +
                    "}).catch(function(){PGEaseDownloads.failed();});})();";
                webView.evaluateJavascript(js, null);
                return;
            }
            if (url.startsWith("data:")) {
                downloads.saveDataUrl(url, mimeType);
                return;
            }
            startDownload(url, userAgent, contentDisposition, mimeType);
        });
        ensureOfflineOverlay();
        webView.post(() -> webView.evaluateJavascript(PULL_JS, null));
    }

    public void flushCookies() {
        CookieManager.getInstance().flush();
    }

    public void openAppLink(Intent intent) {
        if (intent == null || webView == null || intent.getData() == null) {
            return;
        }
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        String url = intent.getData().toString();
        if (!policy.isAppHost(url)) {
            return;
        }
        dismissPopup();
        paymentFlow = false;
        webView.loadUrl(policy.withAppFlag(url));
    }

    public void onBackPressed() {
        if (popupOverlay != null && popupOverlay.getParent() != null && popupOverlay.getVisibility() == View.VISIBLE) {
            dismissPopup();
            return;
        }
        if (offlineOverlay != null && offlineOverlay.getVisibility() == View.VISIBLE) {
            hideOffline();
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return;
            }
            exitIfRepeated();
            return;
        }
        if (webView == null) {
            exitIfRepeated();
            return;
        }
        webView.evaluateJavascript(MODAL_JS, value -> {
            if (value != null && value.contains("closed")) {
                return;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return;
            }
            exitIfRepeated();
        });
    }

    public void notePageStarted() {
        mainFrameFailed = false;
        hideOffline();
    }

    public void notePageFinished(WebView view) {
        if (swipe != null) {
            swipe.setRefreshing(false);
        }
        if (!mainFrameFailed && view == webView) {
            hideOffline();
            view.evaluateJavascript(PULL_JS, null);
        }
        if (view != null) {
            view.evaluateJavascript("if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) { window.Capacitor.Plugins.SplashScreen.hide(); }", null);
        }
    }

    public void showOffline(String description) {
        mainFrameFailed = true;
        if (swipe != null) {
            swipe.setRefreshing(false);
        }
        ensureOfflineOverlay();
        TextView body = offlineOverlay.findViewById(R.id.offline_body);
        String text = description == null ? "" : description.toLowerCase(Locale.US);
        boolean offline = text.contains("err_internet") || text.contains("err_name_not_resolved") || text.contains("err_connection") || text.contains("err_address") || text.isEmpty();
        body.setText(offline ? R.string.offline_body : R.string.error_body);
        if (offlineOverlay.getParent() == null) {
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(
                offlineOverlay,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
        }
        offlineOverlay.setVisibility(View.VISIBLE);
        offlineOverlay.bringToFront();
        if (onboardingView != null && onboardingView.getParent() != null) {
            onboardingView.bringToFront();
        }
    }

    public void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        String agent = userAgent;
        if (agent == null && webView != null) {
            agent = webView.getSettings().getUserAgentString();
        }
        downloads.start(url, agent, contentDisposition, mimeType);
    }

    public boolean openPopupWindow(Message resultMsg) {
        if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
            return false;
        }
        dismissPopup();
        WebView popup = createPopupWebView();
        popup.setWebViewClient(new PopupClient(popup));
        popup.setWebChromeClient(
            new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                    return chrome != null && chrome.onShowFileChooser(view, callback, params);
                }

                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message message) {
                    return openPopupWindow(message);
                }

                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    if (chrome != null) {
                        chrome.onPermissionRequest(request);
                    } else {
                        request.deny();
                    }
                }

                @Override
                public void onReceivedTitle(WebView view, String title) {
                    if (popupTitle != null && title != null && !title.isEmpty() && !"about:blank".equals(title)) {
                        popupTitle.setText(title);
                    }
                }
            }
        );
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
        popupWebView = popup;
        return true;
    }

    void setPullAllowed(boolean allowed) {
        pullAllowed = allowed;
    }

    private void exitIfRepeated() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt < 2000) {
            activity.finish();
            return;
        }
        lastBackAt = now;
        Toast.makeText(activity, R.string.press_back_again, Toast.LENGTH_SHORT).show();
    }

    private void wrapPullToRefresh() {
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent == null) {
            return;
        }
        int index = parent.indexOfChild(webView);
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        parent.removeView(webView);
        swipe = new SwipeRefreshLayout(activity);
        swipe.setLayoutParams(params);
        swipe.setColorSchemeColors(SystemBarStyling.BRAND);
        swipe.setProgressBackgroundColorSchemeColor(Color.WHITE);
        swipe.setOnChildScrollUpCallback((layout, child) -> {
            if (offlineOverlay != null && offlineOverlay.getVisibility() == View.VISIBLE) {
                return true;
            }
            return !pullAllowed || webView.getScrollY() > 0;
        });
        swipe.setOnRefreshListener(() -> {
            pullAllowed = true;
            webView.reload();
        });
        swipe.addView(webView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(swipe, index);
    }

    private void ensureOfflineOverlay() {
        if (offlineOverlay != null) {
            return;
        }
        offlineOverlay = activity.getLayoutInflater().inflate(R.layout.offline_overlay, null);
        SystemBarStyling.padForSystemBars(offlineOverlay);
        offlineOverlay.findViewById(R.id.offline_retry).setOnClickListener(v -> {
            hideOffline();
            mainFrameFailed = false;
            if (webView == null) {
                return;
            }
            String current = webView.getUrl();
            if (current == null || current.equals("about:blank")) {
                webView.loadUrl(homeUrl);
            } else {
                webView.reload();
            }
        });
        offlineOverlay.setVisibility(View.GONE);
    }

    private void hideOffline() {
        if (offlineOverlay != null) {
            offlineOverlay.setVisibility(View.GONE);
        }
    }

    private void ensurePopupOverlay() {
        if (popupOverlay != null) {
            return;
        }
        popupOverlay = activity.getLayoutInflater().inflate(R.layout.popup_overlay, null);
        SystemBarStyling.padForSystemBars(popupOverlay);
        popupContainer = popupOverlay.findViewById(R.id.popup_container);
        popupTitle = popupOverlay.findViewById(R.id.popup_title);
        popupOverlay.findViewById(R.id.popup_close).setOnClickListener(v -> dismissPopup());
    }

    private void showPopup(WebView popup, String url) {
        ensurePopupOverlay();
        if (popup.getParent() != popupContainer) {
            if (popup.getParent() instanceof ViewGroup) {
                ((ViewGroup) popup.getParent()).removeView(popup);
            }
            popupContainer.addView(
                popup,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
        }
        if (popupTitle != null) {
            String host = NavigationPolicy.hostOf(url);
            popupTitle.setText(host == null ? activity.getString(R.string.popup_title) : host);
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (popupOverlay.getParent() == null) {
            content.addView(popupOverlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        popupOverlay.setVisibility(View.VISIBLE);
        popupOverlay.bringToFront();
        if (onboardingView != null && onboardingView.getParent() != null) {
            onboardingView.bringToFront();
        }
        popupWebView = popup;
    }

    private void dismissPopup() {
        WebView popup = popupWebView;
        popupWebView = null;
        if (popup != null) {
            popup.stopLoading();
            if (popup.getParent() instanceof ViewGroup) {
                ((ViewGroup) popup.getParent()).removeView(popup);
            }
            popup.destroy();
        }
        if (popupOverlay != null) {
            popupOverlay.setVisibility(View.GONE);
            if (popupOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) popupOverlay.getParent()).removeView(popupOverlay);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createPopupWebView() {
        WebView popup = new WebView(activity);
        WebSettings settings = popup.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (webView != null) {
            settings.setUserAgentString(webView.getSettings().getUserAgentString());
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);
        popup.setBackgroundColor(Color.WHITE);
        return popup;
    }

    private final class PopupClient extends WebViewClient {

        private final WebView popup;

        private PopupClient(WebView popup) {
            this.popup = popup;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            String scheme = NavigationPolicy.schemeOf(url);
            if ("about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme) || "javascript".equals(scheme)) {
                return false;
            }
            NavigationPolicy.Decision decision = policy.decide(url, paymentFlow);
            if (decision == NavigationPolicy.Decision.EXTERNAL_APP) {
                ExternalLinkOpener.openApp(activity, request.getUrl());
                view.post(() -> dismissPopup());
                return true;
            }
            if (decision == NavigationPolicy.Decision.DOWNLOAD) {
                startDownload(url, null, null, "application/pdf");
                view.post(() -> dismissPopup());
                return true;
            }
            if (decision == NavigationPolicy.Decision.EXTERNAL_BROWSER) {
                ExternalLinkOpener.openBrowser(activity, request.getUrl());
                view.post(() -> dismissPopup());
                return true;
            }
            if (policy.isAppHost(url)) {
                paymentFlow = false;
                if (webView != null) {
                    webView.loadUrl(policy.withAppFlag(url));
                }
                view.post(() -> dismissPopup());
                return true;
            }
            if (policy.isGatewayHost(url)) {
                paymentFlow = true;
            }
            String popupUrl = url;
            view.post(() -> {
                if (popupWebView == popup) {
                    showPopup(popup, popupUrl);
                }
            });
            return false;
        }
    }

    public final class PullBridge {

        @JavascriptInterface
        public void setPullEnabled(boolean enabled) {
            pullAllowed = enabled;
        }
    }

    public final class DownloadBridge {

        @JavascriptInterface
        public void save(String filename, String mime, String base64) {
            downloads.saveBase64(filename, mime, base64);
        }

        @JavascriptInterface
        public void failed() {
            downloads.failed();
        }
    }
}
