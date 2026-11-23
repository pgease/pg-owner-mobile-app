package in.pgease.owner;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Saves WebView downloads into the public Downloads folder. */
public final class FileDownloads {

    private static final int MAX_BYTES = 25 * 1024 * 1024;

    private final Activity activity;
    private final ActivityResultLauncher<String> notificationPermission;
    private boolean askedNotification;
    private Runnable pending;

    public FileDownloads(Activity activity, ActivityResultLauncher<String> notificationPermission) {
        this.activity = activity;
        this.notificationPermission = notificationPermission;
    }

    public void onNotificationResult() {
        Runnable next = pending;
        pending = null;
        if (next != null) {
            next.run();
        }
    }

    public void start(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url == null || url.isEmpty()) {
            return;
        }
        Runnable enqueue = () -> enqueueHttp(url, userAgent, contentDisposition, mimeType);
        if (needsNotificationPrompt()) {
            pending = enqueue;
            askedNotification = true;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        enqueue.run();
    }

    public void saveBase64(String filename, String mime, String base64) {
        if (base64 == null || base64.length() > MAX_BYTES * 2) {
            failed();
            return;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length > MAX_BYTES) {
                failed();
                return;
            }
            writeBytes(sanitize(filename), mime == null || mime.isEmpty() ? "application/octet-stream" : mime, bytes);
        } catch (Exception ex) {
            failed();
        }
    }

    public void saveDataUrl(String dataUrl, String fallbackMime) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) {
                failed();
                return;
            }
            String meta = dataUrl.substring(5, comma);
            String payload = dataUrl.substring(comma + 1);
            String mime = fallbackMime;
            int semi = meta.indexOf(';');
            if (semi > 0) {
                mime = meta.substring(0, semi);
            } else if (!meta.isEmpty()) {
                mime = meta;
            }
            if (meta.contains(";base64")) {
                saveBase64(guessName(null, null, mime), mime, payload);
            } else {
                byte[] bytes = Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
                writeBytes(guessName(null, null, mime), mime, bytes);
            }
        } catch (Exception ex) {
            failed();
        }
    }

    public void failed() {
        activity.runOnUiThread(() -> Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show());
    }

    private boolean needsNotificationPrompt() {
        return (
            Build.VERSION.SDK_INT >= 33 &&
            !askedNotification &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        );
    }

    private void enqueueHttp(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String name = guessName(url, contentDisposition, mimeType);
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            request.setTitle(name);
            request.setDescription(activity.getString(R.string.download_started));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            manager.enqueue(request);
            Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            failed();
        }
    }

    private void writeBytes(String filename, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (item == null) {
                throw new IllegalStateException("insert failed");
            }
            try (OutputStream out = activity.getContentResolver().openOutputStream(item)) {
                if (out == null) {
                    throw new IllegalStateException("stream failed");
                }
                out.write(bytes);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            activity.getContentResolver().update(item, values, null, null);
        } else {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                throw new IllegalStateException("no downloads dir");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("mkdir failed");
            }
            try (FileOutputStream out = new FileOutputStream(new File(dir, filename))) {
                out.write(bytes);
            }
        }
        activity.runOnUiThread(() -> Toast.makeText(activity, R.string.download_saved, Toast.LENGTH_SHORT).show());
    }

    static String guessName(String url, String contentDisposition, String mimeType) {
        String guess = URLUtil.guessFileName(url == null ? "https://app.pgease.com/download" : url, contentDisposition, mimeType);
        return sanitize(guess);
    }

    static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "download";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "download";
        }
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(cleaned.length() - 120);
        }
        return cleaned.toLowerCase(Locale.US).endsWith(".bin") ? cleaned : cleaned;
    }
}
