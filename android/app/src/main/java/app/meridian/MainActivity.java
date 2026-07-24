package app.meridian;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Meridian — нативная оболочка приложения.
 * Загружает встроенную веб-часть из assets (офлайн, полный экран),
 * фиксирует окно (без зума/перетаскивания) и даёт мост к Monobank API.
 */
public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0D1512"));
        getWindow().setNavigationBarColor(Color.parseColor("#0D1512"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0D1512"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // localStorage — операции/цели/баланс/токен
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // респектим <meta viewport width=device-width>, но без зума
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);   // без «резинки» и сдвигов
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());   // включает alert/confirm/prompt
        web.addJavascriptInterface(new MonoBridge(), "MonoNative");
        web.addJavascriptInterface(new NotifyBridge(), "NotifyNative");

        NotificationReceiver.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            web.saveState(outState);
        }
    }

    /**
     * Асинхронный мост к Monobank API из веб-части (обход CORS).
     * Сетевой запрос идёт в фоне, результат возвращается в JS через
     * window.__monoResolve(cbId, base64) — UI не блокируется.
     */
    class MonoBridge {
        @JavascriptInterface
        public void httpGet(final String urlStr, final String token, final String cbId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String result;
                    try {
                        result = doGet(urlStr, token);
                    } catch (Exception e) {
                        result = "__HTTP_ERROR__0__" + e.getMessage();
                    }
                    final String b64 = Base64.encodeToString(
                            result.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    if (web != null) {
                        web.post(new Runnable() {
                            @Override
                            public void run() {
                                web.evaluateJavascript(
                                        "window.__monoResolve && window.__monoResolve('" + cbId + "','" + b64 + "')",
                                        null);
                            }
                        });
                    }
                }
            }).start();
        }

        private String doGet(String urlStr, String token) throws Exception {
            HttpURLConnection c = null;
            try {
                URL url = new URL(urlStr);
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                if (token != null && !token.isEmpty()) {
                    c.setRequestProperty("X-Token", token);
                }
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (is != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                    r.close();
                }
                if (code >= 200 && code < 300) {
                    return sb.toString();
                }
                return "__HTTP_ERROR__" + code + "__" + sb.toString();
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }
    }

    /**
     * Мост локальных уведомлений для веб-части.
     * schedule(id, title, text, whenMs) ставит будильник через AlarmManager;
     * уведомление показывается, даже если приложение закрыто.
     */
    class NotifyBridge {
        @JavascriptInterface
        public String available() {
            return "1";
        }

        @JavascriptInterface
        public void notifyNow(String title, String text) {
            Intent i = new Intent(MainActivity.this, NotificationReceiver.class);
            i.putExtra("id", 999);
            i.putExtra("title", title);
            i.putExtra("text", text);
            sendBroadcast(i);
        }

        @JavascriptInterface
        public void schedule(String id, String title, String text, String whenMs) {
            try {
                int nid = stableId(id);
                long when = Long.parseLong(whenMs);
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am == null) return;
                PendingIntent pi = buildPi(nid, title, text);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } catch (Exception e) {
                // молча игнорируем — уведомления не критичны
            }
        }

        @JavascriptInterface
        public void cancel(String id) {
            try {
                int nid = stableId(id);
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                PendingIntent pi = buildPi(nid, "", "");
                if (am != null) am.cancel(pi);
                NotificationManager nm =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(nid);
            } catch (Exception e) {
                // ignore
            }
        }

        private PendingIntent buildPi(int nid, String title, String text) {
            Intent i = new Intent(MainActivity.this, NotificationReceiver.class);
            i.putExtra("id", nid);
            i.putExtra("title", title);
            i.putExtra("text", text);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getBroadcast(MainActivity.this, nid, i, flags);
        }

        private int stableId(String s) {
            if (s == null) return 1;
            return (s.hashCode() & 0x7fffffff) % 100000 + 1000;
        }
    }
}
