package io.zerodreamcat.zako.flash;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    public static final String TAG = "ZakoFlash";
    public static Context application;
    public static IRemoteService server;

    public App() {
        application = this;
    }
}
