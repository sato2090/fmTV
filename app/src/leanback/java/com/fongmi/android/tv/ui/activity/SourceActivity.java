package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.LiveChecker;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.InvalidChannel;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.databinding.ActivitySourceBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SourceActivity extends BaseActivity implements LiveChecker.Listener {

    private ActivitySourceBinding mBinding;
    private boolean testing;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SourceActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySourceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.sync.requestFocus();
        refreshInfo();
    }

    @Override
    protected void initEvent() {
        mBinding.sync.setOnClickListener(this::onSync);
        mBinding.testAll.setOnClickListener(this::onTestAll);
        mBinding.clear.setOnClickListener(this::onClear);
    }

    private void refreshInfo() {
        Live home = LiveConfig.get().getHome();
        int groups = 0;
        int channels = 0;
        for (Group group : home.getGroups()) {
            if (group.isKeep()) continue;
            groups++;
            channels += group.getChannel().size();
        }
        String url = LiveConfig.getUrl();
        long time = LiveSetting.getSyncTime();
        mBinding.sourceName.setText(TextUtils.isEmpty(home.getName()) ? "-" : home.getName());
        mBinding.sourceUrl.setText(TextUtils.isEmpty(url) ? "-" : url);
        mBinding.sourceStat.setText(getString(R.string.source_stat, groups, channels));
        mBinding.sourceInvalid.setText(getString(R.string.source_invalid, InvalidChannel.count()));
        mBinding.sourceSyncTime.setText(time == 0 ? getString(R.string.source_never) : getString(R.string.source_last_sync, format(time)));
    }

    private String format(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private void onSync(View view) {
        if (testing) return;
        Notify.progress(this);
        LiveConfig.load(Config.live(), new Callback() {
            @Override
            public void success() {
                InvalidChannel.deleteAll();
                LiveSetting.putSyncTime(System.currentTimeMillis());
                App.post(() -> {
                    Notify.dismiss();
                    RefreshEvent.live();
                    if (isFinishing() || isDestroyed()) return;
                    refreshInfo();
                    Notify.show(R.string.source_sync_success);
                });
            }

            @Override
            public void error(String msg) {
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show(msg);
                });
            }
        });
    }

    private void onTestAll(View view) {
        if (testing) {
            LiveChecker.cancel();
            mBinding.status.setText(R.string.source_test_stopped);
            return;
        }
        testing = true;
        mBinding.testAll.setText(R.string.source_test_cancel);
        mBinding.status.setText(R.string.source_testing);
        Live home = LiveConfig.get().getHome();
        LiveChecker.testAll(home, home.getGroups(), this);
    }

    private void onClear(View view) {
        if (testing) return;
        InvalidChannel.deleteAll();
        RefreshEvent.live();
        refreshInfo();
        Notify.show(R.string.source_clear_done);
    }

    @Override
    public void onProgress(int done, int total, int invalid) {
        if (isFinishing() || isDestroyed()) return;
        mBinding.status.setText(getString(R.string.source_test_progress, done, total, invalid));
    }

    @Override
    public void onFinish(int total, int invalid) {
        testing = false;
        LiveChecker.applyInvalid(LiveConfig.get().getHome());
        RefreshEvent.live();
        if (isFinishing() || isDestroyed()) return;
        mBinding.testAll.setText(R.string.source_test_all);
        mBinding.status.setText(getString(R.string.source_test_done, total, invalid));
        refreshInfo();
    }

    @Override
    protected void onDestroy() {
        if (testing) LiveChecker.cancel();
        super.onDestroy();
    }
}
