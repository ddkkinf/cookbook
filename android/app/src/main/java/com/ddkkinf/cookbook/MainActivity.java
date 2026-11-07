package com.ddkkinf.cookbook;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KitchenAlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
