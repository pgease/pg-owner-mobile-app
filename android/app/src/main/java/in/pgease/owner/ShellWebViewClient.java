package in.pgease.owner;

import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

/** Keeps the owner app and payment gateways in the WebView, and sends the rest out. */
public class ShellWebViewClient extends BridgeWebViewClient {

    private final OwnerShell shell;

    public ShellWebViewClient(Bridge bridge, OwnerShell shell) {
        super(bridge);
        this.shell = shell;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        String url = uri.toString();
        NavigationPolicy policy = shell.policy();
        NavigationPolicy.Decision decision = policy.decide(url, shell.isPaymentFlow());

        if (!request.isForMainFrame()) {
            if (decision == NavigationPolicy.Decision.EXTERNAL_APP) {
                ExternalLinkOpener.openApp(shell.activity(), uri);
                return true;
            }
            if (decision == NavigationPolicy.Decision.DOWNLOAD) {
                shell.startDownload(url, null, null, "application/pdf");
                return true;
            }
            if (policy.isGatewayHost(url)) {
                shell.setPaymentFlow(true);
            }
            return false;
        }

        if (decision == NavigationPolicy.Decision.EXTERNAL_APP) {
            ExternalLinkOpener.openApp(shell.activity(), uri);
            return true;
        }
        if (decision == NavigationPolicy.Decision.DOWNLOAD) {
            shell.startDownload(url, null, null, "application/pdf");
            return true;
        }
        if (decision == NavigationPolicy.Decision.EXTERNAL_BROWSER) {
            ExternalLinkOpener.openBrowser(shell.activity(), uri);
            return true;
        }
        if (policy.isAppHost(url)) {
            shell.setPaymentFlow(false);
        } else if (policy.isGatewayHost(url)) {
            shell.setPaymentFlow(true);
        }
        return false;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        shell.notePageStarted();
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        shell.notePageFinished(view);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request == null || !request.isForMainFrame() || error == null) {
            return;
        }
        CharSequence description = error.getDescription();
        if (description != null && description.toString().contains("ERR_ABORTED")) {
            return;
        }
        shell.showOffline(description == null ? "" : description.toString());
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
        shell.showOffline("");
    }
}
