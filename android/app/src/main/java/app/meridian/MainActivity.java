package app.meridian;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
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
        web.addJavascriptInterface(new MonoBridge(), "MonoNative");

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
}
