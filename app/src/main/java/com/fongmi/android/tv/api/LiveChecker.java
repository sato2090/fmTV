package com.fongmi.android.tv.api;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.InvalidChannel;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Task;

import java.util.ArrayList;
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

    private static final long TIMEOUT = 6000;
    private static final int PARALLEL = 12;
    private static final AtomicBoolean cancelled = new AtomicBoolean();

    public interface Listener {

        void onProgress(int done, int total, int invalid);

        void onFinish(int total, int invalid);
    }

    public static void cancel() {
        cancelled.set(true);
    }

    public static void applyInvalid(Live live) {
        Set<String> keys = InvalidChannel.keys();
        if (keys.isEmpty()) return;
        for (Group group : live.getGroups()) {
            if (group == null || group.isKeep()) continue;
            group.getChannel().removeIf(channel -> keys.contains(InvalidChannel.key(live.getName(), channel.getName())));
        }
    }

    public static boolean isCancelled() {
        return cancelled.get();
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

    public static void testAll(Live live, List<Group> groups, Listener listener) {
        List<Channel> channels = collect(groups);
        cancelled.set(false);
        Task.execute(() -> {
            int total = channels.size();
            if (total == 0) {
                App.post(() -> listener.onFinish(0, 0));
                return;
            }
            AtomicInteger done = new AtomicInteger();
            AtomicInteger invalid = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
            CountDownLatch latch = new CountDownLatch(total);
            for (Channel channel : channels) pool.execute(() -> {
                try {
                    if (!cancelled.get() && Boolean.FALSE.equals(check(channel))) {
                        InvalidChannel.save(InvalidChannel.key(live.getName(), channel.getName()));
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
                latch.await(10, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow();
            int marked = invalid.get();
            App.post(() -> listener.onFinish(total, marked));
        });
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
