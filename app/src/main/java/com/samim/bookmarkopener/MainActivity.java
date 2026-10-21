package com.samim.bookmarkopener;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    chooser = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooser = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    class Bridge {
        @JavascriptInterface
        public String getBrowsers() {
            JSONArray arr = new JSONArray();
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                List<ResolveInfo> list = getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo r : list) {
                    String pkg = r.activityInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", r.loadLabel(getPackageManager()).toString());
                    o.put("pkg", pkg);
                    arr.put(o);
                }
            } catch (Exception e) { }
            return arr.toString();
        }

        @JavascriptInterface
        public void openUrls(final String json, final String pkg) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(MainActivity.this)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Allow 'Display over other apps', then come back and tap Open again", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    }
                });
                return;
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        JSONArray urls = new JSONArray(json);
                        for (int k = 0; k < urls.length(); k++) {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(urls.getString(k)));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.putExtra("create_new_tab", true);
                            if (pkg != null && pkg.length() > 0) {
                                i.setPackage(pkg);
                                i.putExtra("com.android.browser.application_id", pkg);
                            }
                            startActivity(i);
                            Thread.sleep(800);
                        }
                    } catch (Exception e) { }
                }
            }).start();
        }
    }
}
