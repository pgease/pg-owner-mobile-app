package in.pgease.owner;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.Logger;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File inputs can pick documents or take a photo. {@code capture} still opens the camera directly.
 * {@code window.open} is handed to the shell so gateway pages can return to the app.
 */
public class ShellWebChromeClient extends BridgeWebChromeClient {

    private final Activity activity;
    private final OwnerShell shell;
    private final ActivityResultLauncher<Intent> chooserLauncher;
    private ValueCallback<Uri[]> pendingCallback;
    private Uri pendingCameraUri;

    public ShellWebChromeClient(Bridge bridge, OwnerShell shell) {
        super(bridge);
        this.shell = shell;
        this.activity = bridge.getActivity();
        this.chooserLauncher = bridge.registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> deliverChooserResult(result.getResultCode(), result.getData())
        );
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        if (fileChooserParams == null) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            return true;
        }
        if (fileChooserParams.isCaptureEnabled() && !acceptsImage(fileChooserParams)) {
            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
        }
        if (fileChooserParams.isCaptureEnabled()) {
            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
        }
        if (!acceptsImage(fileChooserParams)) {
            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
        }
        showCameraOrFiles(filePathCallback, fileChooserParams);
        return true;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        return shell.openPopupWindow(resultMsg);
    }

    private void showCameraOrFiles(ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
        cancelPending();
        pendingCallback = filePathCallback;
        Intent fileIntent;
        try {
            fileIntent = params.createIntent();
        } catch (Exception ex) {
            deliver(null);
            return;
        }
        if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        Uri cameraUri = null;
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(activity.getPackageManager()) != null) {
            try {
                cameraUri = createImageUri();
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cameraIntent.setClipData(ClipData.newUri(activity.getContentResolver(), "photo", cameraUri));
                List<ResolveInfo> cameras = activity
                    .getPackageManager()
                    .queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo info : cameras) {
                    activity.grantUriPermission(
                        info.activityInfo.packageName,
                        cameraUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                }
            } catch (Exception ex) {
                Logger.warn("Camera capture file could not be created: " + ex.getMessage());
                cameraUri = null;
            }
        }
        pendingCameraUri = cameraUri;

        Intent chooser = Intent.createChooser(fileIntent, activity.getString(R.string.chooser_upload));
        if (cameraUri != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { cameraIntent });
        }
        try {
            chooserLauncher.launch(chooser);
        } catch (Exception ex) {
            deliver(null);
        }
    }

    private void deliverChooserResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            deliver(null);
            return;
        }
        if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
            int count = data.getClipData().getItemCount();
            Uri[] uris = new Uri[count];
            for (int i = 0; i < count; i++) {
                uris[i] = data.getClipData().getItemAt(i).getUri();
            }
            deliver(uris);
            return;
        }
        Uri[] parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        if (parsed != null && parsed.length > 0 && parsed[0] != null) {
            deliver(parsed);
            return;
        }
        if (pendingCameraUri != null && cameraFileHasBytes(pendingCameraUri)) {
            deliver(new Uri[] { pendingCameraUri });
            return;
        }
        deliver(null);
    }

    private void deliver(Uri[] value) {
        ValueCallback<Uri[]> callback = pendingCallback;
        pendingCallback = null;
        pendingCameraUri = null;
        if (callback != null) {
            callback.onReceiveValue(value);
        }
    }

    private void cancelPending() {
        if (pendingCallback != null) {
            pendingCallback.onReceiveValue(null);
            pendingCallback = null;
        }
        pendingCameraUri = null;
    }

    private boolean cameraFileHasBytes(Uri uri) {
        try (android.content.res.AssetFileDescriptor descriptor = activity.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            return descriptor != null && descriptor.getLength() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private Uri createImageUri() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            throw new IOException("Pictures directory unavailable");
        }
        File photo = File.createTempFile("JPEG_" + stamp + "_", ".jpg", dir);
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photo);
    }

    static boolean acceptsImage(FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) {
            return true;
        }
        boolean sawType = false;
        for (String type : types) {
            if (type == null || type.trim().isEmpty()) {
                continue;
            }
            sawType = true;
            String value = type.trim().toLowerCase(Locale.US);
            if ("*/*".equals(value) || "image".equals(value) || value.startsWith("image/") || value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp") || value.endsWith(".heic")) {
                return true;
            }
        }
        return !sawType;
    }
}
