package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.LiveChecker;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.InvalidChannel;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.LiveSource;
import com.fongmi.android.tv.databinding.ActivitySourceBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.ui.adapter.SourceAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SourceActivity extends BaseActivity implements SourceAdapter.OnClickListener {

    private ActivitySourceBinding mBinding;
    private SourceAdapter mAdapter;
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
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new SourceAdapter(this));
        mAdapter.addAll(LiveSource.getAll());
        mBinding.syncAll.requestFocus();
    }

    @Override
    protected void initEvent() {
        mBinding.syncAll.setOnClickListener(this::onSyncAll);
        mBinding.testAll.setOnClickListener(this::onTestAll);
        mBinding.clear.setOnClickListener(this::onClear);
    }

    private void onSyncAll(View view) {
        if (testing) return;
        LiveSource.clearCache();
        reload();
    }

    private void reload() {
        Notify.progress(this);
        LiveConfig.load(Config.live(), new Callback() {
            @Override
            public void success() {
                LiveSetting.putSyncTime(System.currentTimeMillis());
                App.post(() -> {
                    Notify.dismiss();
                    RefreshEvent.live();
                    refresh();
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

    private void refresh() {
        if (isFinishing() || isDestroyed()) return;
        mAdapter.addAll(LiveSource.getAll());
    }

    private void onTestAll(View view) {
        if (testing) {
            LiveChecker.cancel();
            mBinding.status.setText(R.string.source_test_stopped);
            return;
        }
        List<LiveSource> items = new ArrayList<>(LiveSource.getEnabled());
        if (items.isEmpty()) return;
        testing = true;
        mBinding.testAll.setText(R.string.source_test_cancel);
        testNext(items, 0, 0);
    }

    private void testNext(List<LiveSource> items, int sumTotal, int sumInvalid) {
        if (items.isEmpty() || !testing) {
            finishTest(sumTotal, sumInvalid);
            return;
        }
        LiveSource item = items.remove(0);
        mBinding.status.setText(getString(R.string.source_test_source, item.getName()));
        LiveChecker.testAll(item, new LiveChecker.Listener() {
            @Override
            public void onProgress(int done, int total, int invalid) {
                if (isFinishing() || isDestroyed()) return;
                mBinding.status.setText(getString(R.string.source_test_detail, item.getName(), done, total, invalid));
            }

            @Override
            public void onFinish(int total, int invalid) {
                App.post(() -> testNext(items, sumTotal + total, sumInvalid + invalid));
            }
        });
    }

    private void finishTest(int total, int invalid) {
        testing = false;
        Live home = LiveConfig.get().getHome();
        LiveChecker.applyInvalid(home);
        LiveSource.prune(home);
        RefreshEvent.live();
        if (isFinishing() || isDestroyed()) return;
        mBinding.testAll.setText(R.string.source_test_all);
        mBinding.status.setText(getString(R.string.source_test_done, total, invalid));
        refresh();
    }

    private void onClear(View view) {
        if (testing) return;
        InvalidChannel.deleteAll();
        LiveSource.clearCache();
        mBinding.status.setText(R.string.source_clear_done);
        reload();
    }

    @Override
    public void onItemClick(LiveSource item) {
        if (testing) return;
        item.toggle();
        mBinding.status.setText(getString(item.isEnable() ? R.string.source_toggle_on : R.string.source_toggle_off, item.getName()));
        LiveSource.clearCache();
        LiveSource.syncConfig();
        reload();
    }

    @Override
    public void onLongClick(LiveSource item) {
        if (testing) return;
        String[] actions = {getString(R.string.source_sync_one), getString(R.string.source_test_one), getString(R.string.source_clear_one)};
        new MaterialAlertDialogBuilder(this).setItems(actions, (dialog, which) -> {
            if (which == 0) syncOne(item);
            else if (which == 1) testOne(item);
            else clearOne(item);
        }).show();
    }

    private void syncOne(LiveSource item) {
        LiveSource.clear(item.getUrl());
        reload();
    }

    private void testOne(LiveSource item) {
        if (testing) return;
        testing = true;
        mBinding.testAll.setText(R.string.source_test_cancel);
        mBinding.status.setText(getString(R.string.source_test_source, item.getName()));
        LiveSource.clear(item.getUrl());
        LiveChecker.testAll(item, new LiveChecker.Listener() {
            @Override
            public void onProgress(int done, int total, int invalid) {
                if (isFinishing() || isDestroyed()) return;
                mBinding.status.setText(getString(R.string.source_test_detail, item.getName(), done, total, invalid));
            }

            @Override
            public void onFinish(int total, int invalid) {
                App.post(() -> finishTest(total, invalid));
            }
        });
    }

    private void clearOne(LiveSource item) {
        InvalidChannel.deleteBySource(item.getUrl());
        LiveSource.clear(item.getUrl());
        reload();
    }

    @Override
    protected void onDestroy() {
        if (testing) LiveChecker.cancel();
        super.onDestroy();
    }
}
