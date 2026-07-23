package app.meridian;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Meridian — нативная оболочка приложения.
 * Загружает встроенную веб-часть из assets (офлайн, полный экран).
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
        s.setDomStorageEnabled(true);     // localStorage — сохранение операций/целей
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient());

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
}
