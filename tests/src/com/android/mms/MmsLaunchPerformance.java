package com.android.mms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.test.LaunchPerformanceBase;

public class MmsLaunchPerformance extends LaunchPerformanceBase {

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        mIntent.setPackage(getTargetContext().getPackageName());
        mIntent.setAction(Intent.ACTION_MAIN);
        start();
    }

    /**
     * Calls LaunchApp and finish.
     */
    @Override
    public void onStart() {
        super.onStart();
        LaunchApp();
        finish(Activity.RESULT_OK, mResults);
    }

}
