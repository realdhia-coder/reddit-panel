package com.rdpanel.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Iterator;

public class MainActivity extends Activity {

    private WebView web;
    private String injected = "";

    private static boolean allowed(String url) {
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
            return "https".equals(u.getScheme()) &&
                    (h.equals("v.redd.it") || h.equals("api.redgifs.com") || h.equals("media.redgifs.com"));
        } catch (Exception e) {
            return false;
        }
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void xhr(final int id, final String opts) {
            new Thread(() -> {
                JSONObject res = new JSONObject();
                try {
                    JSONObject o = new JSONObject(opts);
                    String url = o.getString("url");
                    if (!allowed(url)) {
                        res.put("error", "host not allowed");
                    } else {
                        int timeout = o.optInt("timeout", 15000);
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setRequestMethod(o.optString("method", "GET"));
                        c.setConnectTimeout(timeout);
                        c.setReadTimeout(timeout);
                        JSONObject hd = o.optJSONObject("headers");
                        if (hd != null) {
                            Iterator<String> it = hd.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                c.setRequestProperty(k, hd.getString(k));
                            }
                        }
                        int code = c.getResponseCode();
                        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        if (in != null) {
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            in.close();
                        }
                        byte[] data = out.toByteArray();
                        res.put("status", code);
                        res.put("type", c.getContentType());
                        if (o.optBoolean("blob")) res.put("b64", Base64.encodeToString(data, Base64.NO_WRAP));
                        else res.put("text", new String(data, "UTF-8"));
                    }
                } catch (SocketTimeoutException e) {
                    try { res.put("timeout", true); } catch (Exception ignored) {}
                } catch (Exception e) {
                    try { res.put("error", String.valueOf(e)); } catch (Exception ignored) {}
                }
                final String js = "window.__gmDone(" + id + "," + JSONObject.quote(res.toString()) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        injected = asset("hls.min.js") + "\n;" + asset("shim.js") + "\n;" + asset("panel.js");

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String host = r.getUrl().getHost() == null ? "" : r.getUrl().getHost();
                if (host.endsWith("reddit.com") || host.endsWith("redd.it") || host.endsWith("redditstatic.com")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                String host = Uri.parse(url).getHost();
                if (host != null && host.endsWith("reddit.com")) v.evaluateJavascript(injected, null);
            }
        });

        web.loadUrl("https://www.reddit.com/r/popular/top/?t=month");
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }
}
