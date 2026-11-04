package com.hameed.grades;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_FILE = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    private static final String BRIDGE_JS =
        "(function(){var B=window.AndroidBridge;if(!B||window.__br)return;window.__br=1;"
      + "window.print=function(){window.dispatchEvent(new Event('beforeprint'));B.print()};"
      + "var b64=function(blob){return new Promise(function(res,rej){var r=new FileReader();"
      + "r.onload=function(){res(String(r.result).split(',')[1]||'')};r.onerror=rej;r.readAsDataURL(blob)})};"
      + "window.claude={use:function(n){if(n!=='downloads')return Promise.resolve(null);"
      + "return Promise.resolve({save:function(o){return b64(o.data).then(function(d){B.save(o.filename,d);return true})}})}};"
      + "var o0=window.open;window.open=function(u){if(u&&/^(https?|mailto|tel):/.test(String(u))){B.open(String(u));return null}"
      + "return o0?o0.apply(window,arguments):null};})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportMultipleWindows(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openExternal(u.toString());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(BRIDGE_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(BRIDGE_JS, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("حسنًا", (d, w) -> result.confirm())
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("موافق", (d, w) -> result.confirm())
                    .setNegativeButton("إلغاء", (d, w) -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                final EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setCancelable(false)
                    .setPositiveButton("موافق", (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("إلغاء", (d, w) -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) { }
    }

    private void printPage() {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter("طباعة");
        pm.print("طباعة", adapter, new PrintAttributes.Builder().build());
    }

    private String mimeFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    public class Bridge {
        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> printPage());
        }

        @JavascriptInterface
        public void open(final String url) {
            runOnUiThread(() -> openExternal(url));
        }

        @JavascriptInterface
        public void save(final String filename, final String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                String name = filename.replace("/", "_");
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mimeFor(name));
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("no uri");
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                toast("تم الحفظ في مجلد التنزيلات: " + name);
            } catch (Exception e) {
                toast("تعذر حفظ الملف");
            }
        }

        private void toast(final String t) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, t, Toast.LENGTH_LONG).show());
        }
    }
}
