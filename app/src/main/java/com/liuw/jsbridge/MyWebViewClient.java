package com.liuw.jsbridge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import com.github.lzyzsd.jsbridge.BridgeWebView;
import com.github.lzyzsd.jsbridge.BridgeWebViewClient;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Created by liuw on 2018/3/6.
 * 自定义WebViewClient
 */

class MyWebViewClient extends BridgeWebViewClient {
    private Context mContext;
    public MyWebViewClient(BridgeWebView mBridgeWebview, Context context) {
        super(mBridgeWebview);
        mContext = context;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Log.i("liuw", "url地址为：" + url);
        try {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        //默认操作url地址yy://__QUEUE_MESSAGE__/
        if (url.trim().startsWith("yy:")) {
            return super.shouldOverrideUrlLoading(view, url);
        }
        //特殊情况tel，调用系统的拨号软件拨号【<a href="tel:110">拨打电话110</a>】
        if(url.trim().startsWith("tel")){
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            mContext.startActivity(i);
        }else {
            //特殊情况【调用系统浏览器打开】<a href="https://www.csdn.net">调用系统浏览器</a>
            if(url.contains("csdn")){
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                mContext.startActivity(i);
            } else {//其它非特殊情况全部放行
                view.loadUrl(url);
            }
        }
        return true;
    }
}
