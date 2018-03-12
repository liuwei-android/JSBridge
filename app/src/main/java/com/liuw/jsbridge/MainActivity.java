package com.liuw.jsbridge;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.lzyzsd.jsbridge.BridgeHandler;
import com.github.lzyzsd.jsbridge.BridgeWebView;
import com.github.lzyzsd.jsbridge.CallBackFunction;
import com.github.lzyzsd.jsbridge.DefaultHandler;
import com.google.gson.Gson;
import com.liuw.jsbridge.bean.UserInfo;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class MainActivity extends AppCompatActivity {

    @InjectView(R.id.edit_view)
    EditText mEditText;
    @InjectView(R.id.btn_send)
    Button btnSend;
    @InjectView(R.id.bridge_webview)
    BridgeWebView mBridgeWebview;
    @InjectView(R.id.btn_reset)
    Button btnReset;

    private String TAG = "MainActivity";
    private MyHandlerCallBack.OnSendDataListener mOnSendDataListener;
    private ValueCallback<Uri> mUploadMessage;
    ;
    private ValueCallback<Uri[]> mUploadCallbackAboveL;
    private final static int FILECHOOSER_RESULTCODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        initListener();
        initWebView();
    }

    private void initWebView() {
        //辅助WebView设置处理关于页面跳转，页面请求等操作
        mBridgeWebview.setWebViewClient(new MyWebViewClient(mBridgeWebview, MainActivity.this));
        //Handler做为通信桥梁的作用，接收处理来自H5数据及回传Native数据的处理，当h5调用send()发送消息的时候，调用MyHandlerCallBack
        mBridgeWebview.setDefaultHandler(new MyHandlerCallBack(mOnSendDataListener));
        //WebChromeClient主要辅助WebView处理Javascript的对话框、网站图标、网站title、加载进度等比等，不过它还能处理文件上传操作
        mBridgeWebview.setWebChromeClient(new MyChromeWebClient());
        // 如果不加这一行，当点击界面链接，跳转到外部时，会出现net::ERR_CACHE_MISS错误
        // 需要在androidManifest.xml文件中声明联网权限
        // <uses-permission android:name="android.permission.INTERNET"/>
        if (Build.VERSION.SDK_INT >= 19) {
            mBridgeWebview.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }

        //加载网页地址
        mBridgeWebview.loadUrl("file:///android_asset/web.html");

        //有方法名的都需要注册Handler后使用
        mBridgeWebview.registerHandler("submitFromWeb", new BridgeHandler() {
            @Override
            public void handler(String data, CallBackFunction function) {
                Log.i("liuw", "html返回数据为:" + data);
                if (!TextUtils.isEmpty(data)) {
                    mEditText.setText("通过调用Native方法接收数据：\n" + data);
                }
                function.onCallBack("Native已经接收到数据：" + data + "，请确认！");
            }
        });

        mBridgeWebview.registerHandler("functionOpen", new BridgeHandler() {
            @Override
            public void handler(String data, CallBackFunction function) {
                Toast.makeText(MainActivity.this, "网页在打开你的文件预览", Toast.LENGTH_SHORT).show();
            }
        });

        //应用启动后初始化数据调用，js处理方法connectWebViewJavascriptBridge(function(bridge)
        mBridgeWebview.callHandler("functionInJs", new Gson().toJson(new UserInfo("liuw", "123456")), new CallBackFunction() {
            @Override
            public void onCallBack(String data) {
                mEditText.setText("向h5发送初始化数据成功，接收h5返回值为：\n" + data);
            }
        });

        //对应js中的bridge.init处理，此处需加CallBackFunction,如果只使用mBridgeWebview.send("")；会导致js中只收到通知，接收不到值
        mBridgeWebview.send("来自java的发送消息！！！", new CallBackFunction() {
            @Override
            public void onCallBack(String data) {
                Toast.makeText(MainActivity.this, "bridge.init初始化数据成功" + data, Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void initListener() {
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String data = mEditText.getText().toString();
                //直接调用nativeFunction方法向H5发送数据
                mBridgeWebview.loadUrl("javascript:nativeFunction('" + data + "')");
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBridgeWebview.loadUrl("file:///android_asset/web.html");
            }
        });

        mOnSendDataListener = new MyHandlerCallBack.OnSendDataListener() {
            @Override
            public void sendData(String data) {
                mEditText.setText("通过webview发消息接收到数据：\n" + data);
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage && null == mUploadCallbackAboveL) {
                return;
            }
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            if (mUploadCallbackAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(result);
                mUploadMessage = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILECHOOSER_RESULTCODE
                || mUploadCallbackAboveL == null) {
            return;
        }

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {

            } else {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();

                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }

                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }
        mUploadCallbackAboveL.onReceiveValue(results);
        mUploadCallbackAboveL = null;
    }

    //自定义 WebChromeClient 辅助WebView处理图片上传操作【<input type=file> 文件上传标签,点击会自动调用】
    public class MyChromeWebClient extends WebChromeClient {
        // For Android 3.0-
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            Log.d(TAG, "openFileChoose(ValueCallback<Uri> uploadMsg)");
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            MainActivity.this.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
        }

        // For Android 3.0+
        public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
            Log.d(TAG, "openFileChoose( ValueCallback uploadMsg, String acceptType )");
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            MainActivity.this.startActivityForResult(
                    Intent.createChooser(i, "File Browser"),
                    FILECHOOSER_RESULTCODE);
        }

        //For Android 4.1
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            Log.d(TAG, "openFileChoose(ValueCallback<Uri> uploadMsg, String acceptType, String capture)");
            mUploadMessage = uploadMsg;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            MainActivity.this.startActivityForResult(Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
        }

        // For Android 5.0+会调用此方法
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            Log.d(TAG, "onShowFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture)");
            mUploadCallbackAboveL = filePathCallback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            MainActivity.this.startActivityForResult(
                    Intent.createChooser(i, "File Browser"),
                    FILECHOOSER_RESULTCODE);
            return true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 处理返回键，在webview界面，按下返回键，不退出程序
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mBridgeWebview != null && mBridgeWebview.canGoBack()) {
                mBridgeWebview.goBack();
                return true;
            }else {
                System.exit(0);
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
