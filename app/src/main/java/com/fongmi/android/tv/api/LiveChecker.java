package com.fongmi.android.tv.api;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.InvalidChannel;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.LiveSource;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LiveChecker {

    private static final long TIMEOUT = 5000;
    private static final int PARALLEL = 16;
    private static final AtomicBoolean cancelled = new AtomicBoolean();

    public interface Listener {

        void onProgress(int done, int total, int invalid);

        void onFinish(int total, int invalid);
    }

    public static void cancel() {
        cancelled.set(true);
    }

    public static boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 把被标记为不可用的频道从列表里剔除，仅影响当前展示用副本。
     * 订阅源原始列表不受影响，因此下次检测依然会重新测试这些频道。
     */
    public static void applyInvalid(Live live) {
        Set<String> keys = InvalidChannel.keys();
        if (keys.isEmpty()) return;
        for (Group group : live.getGroups()) {
            if (group == null || group.isKeep()) continue;
            group.getChannel().removeIf(channel -> keys.contains(key(channel, group)));
        }
    }

    public static String key(Channel item) {
        return key(item, item == null ? null : item.getGroup());
    }

    private static String key(Channel item, Group group) {
        return InvalidChannel.key(group == null ? "" : group.getSource(), item == null ? "" : item.getName());
    }

    @Nullable
    public static Boolean check(Channel channel) {
        Map<String, String> headers = channel.getHeaders();
        String ua = Setting.getUa();
        if (ua != null && !ua.isEmpty() && !headers.containsKey("User-Agent")) headers.put("User-Agent", ua);
        boolean probed = false;
        for (String url : channel.getUrls()) {
            if (!isHttp(url)) continue;
            probed = true;
            if (probe(url, headers)) return Boolean.TRUE;
        }
        return probed ? Boolean.FALSE : null;
    }

    /**
     * 测试单个播放地址是否可达。
     */
    public static boolean checkUrl(String url) {
        if (!isHttp(url)) return false;
        return probe(url, new HashMap<>());
    }

    public static void testAll(Live live, List<Group> groups, Listener listener) {
        Task.execute(() -> test(groups, listener));
    }

    /**
     * 测试整个订阅源：使用源原始列表的副本，已失效的频道同样参与测试。
     */
    public static void testAll(LiveSource item, Listener listener) {
        Task.execute(() -> test(item.copy(), listener));
    }

    private static void test(List<Group> groups, Listener listener) {
        List<Channel> channels = collect(groups);
        int total = channels.size();
        if (total == 0) {
            App.post(() -> listener.onFinish(0, 0));
            return;
        }
        cancelled.set(false);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger invalid = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        CountDownLatch latch = new CountDownLatch(total);
        for (Channel channel : channels) pool.execute(() -> {
            try {
                if (cancelled.get()) return;
                Boolean result = check(channel);
                String key = key(channel);
                if (Boolean.TRUE.equals(result)) InvalidChannel.delete(key);
                else if (Boolean.FALSE.equals(result)) {
                    InvalidChannel.save(key);
                    invalid.incrementAndGet();
                }
            } catch (Throwable ignored) {
            } finally {
                int current = done.incrementAndGet();
                int marked = invalid.get();
                App.post(() -> listener.onProgress(current, total, marked));
                latch.countDown();
            }
        });
        pool.shutdown();
        try {
            if (!latch.await(30, TimeUnit.MINUTES)) pool.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        int marked = invalid.get();
        App.post(() -> listener.onFinish(total, marked));
    }

    private static List<Channel> collect(List<Group> groups) {
        List<Channel> items = new ArrayList<>();
        for (Group group : groups) {
            if (group == null || group.isKeep()) continue;
            items.addAll(group.getChannel());
        }
        return items;
    }

    private static boolean isHttp(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static boolean probe(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url).header("Range", "bytes=0-1");
        if (headers != null) headers.forEach(builder::header);
        OkHttpClient client = OkHttp.client(TIMEOUT);
        try (Response response = client.newCall(builder.build()).execute()) {
            int code = response.code();
            return response.isSuccessful() || (code >= 300 && code < 400);
        } catch (Throwable e) {
            return false;
        }
    }
}
