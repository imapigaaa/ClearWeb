package com.clearweb.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.ByteArrayInputStream;

/**
 * 单页 WebView 壳。
 * <p>
 * 设计取舍：不解析、不爬取、不重排页面，直接渲染官方移动版站点，
 * 这样站点改版不会导致应用失效（PHTV 那类解析型客户端就是死在这上面）。
 * 去广告靠两层：
 * 1. 网络层 —— {@link AdBlocker} 拦掉广告域名，脚本根本下载不下来；
 * 2. DOM 层 —— {@link PageCleaner} 删掉已经渲染出来的广告节点和推广区块。
 */
public class MainActivity extends Activity {

    /** 起始页。想换站点只改这一行。 */
    private static final String HOME_URL = "https://www.pornhub.com/";

    /**
     * 去掉 WebView 的 "; wv" 标记，冒充普通 Chrome 移动版。
     * 有些站点会对 WebView 降级甚至拒绝播放，用干净 UA 最稳。
     */
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Mobile Safari/537.36";

    private WebView web;
    private SwipeRefreshLayout refresh;
    private ProgressBar progress;
    private FrameLayout fullscreenLayer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(UA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(0xFFFF9000);
        refresh.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(refresh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        plp.gravity = Gravity.TOP;
        root.addView(progress, plp);

        fullscreenLayer = new FrameLayout(this);
        fullscreenLayer.setBackgroundColor(Color.BLACK);
        fullscreenLayer.setVisibility(View.GONE);
        root.addView(fullscreenLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                web.reload();
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME_URL);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    /** 退出网页全屏播放。必须回调 CustomViewCallback，否则 WebView 会一直认为还在全屏。 */
    private void exitFullscreen() {
        if (customView == null) {
            return;
        }
        fullscreenLayer.removeView(customView);
        fullscreenLayer.setVisibility(View.GONE);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            web.saveState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String host = uri.getHost();
            if (AdBlocker.isAdHost(host)) {
                return true; // 广告落地页：直接吃掉，不跳转
            }
            if (AdBlocker.isSiteHost(host)) {
                return false; // 站内链接：留在 WebView
            }
            // 站外链接：交给系统浏览器，避免困在应用里
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // 没有可用浏览器，忽略
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (AdBlocker.isAdHost(request.getUrl().getHost())) {
                // 返回空响应，广告脚本/图片一个字节都不下载
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            view.evaluateJavascript(PageCleaner.earlyScript(), null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            refresh.setRefreshing(false);
            view.evaluateJavascript(PageCleaner.cleanScript(), null);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                progress.setVisibility(View.GONE);
                refresh.setRefreshing(false);
                Toast.makeText(MainActivity.this, "加载失败，下拉可重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            if (newProgress >= 100) {
                progress.setVisibility(View.GONE);
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            fullscreenLayer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullscreenLayer.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            // 不需要摄像头/麦克风，一律拒绝
            request.deny();
        }
    }
}
