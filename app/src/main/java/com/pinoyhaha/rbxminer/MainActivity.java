package com.pinoyhaha.rbxminer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class MainActivity extends Activity {

    private WebView webview1;
    private TextView loadingText;

    // CHANGE THIS to your real Vercel site link.
    // Example: https://proofs.vercel.app
    private static final String APP_URL = "https://your-site.vercel.app?v=12";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webview1 = findViewById(R.id.webview1);
        loadingText = findViewById(R.id.loadingText);

        WebSettings settings = webview1.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webview1.setWebChromeClient(new WebChromeClient());
        webview1.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingText.setVisibility(View.GONE);
                webview1.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                loadingText.setText("Failed to load. Check internet or URL.");
            }
        });

        webview1.setVisibility(View.INVISIBLE);
        webview1.loadUrl(APP_URL);
    }

    @Override
    public void onBackPressed() {
        if (webview1 != null && webview1.canGoBack()) {
            webview1.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
