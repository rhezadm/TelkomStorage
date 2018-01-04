package com.telkomsigma.telkomstorage.ui.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;

public class RegisterActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.telkomsigma.telkomstorage.R.layout.activity_register);

        webView = (WebView) findViewById(com.telkomsigma.telkomstorage.R.id.wv_register);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("http://telkommail.id/index.php/register/personal");

    }
}
