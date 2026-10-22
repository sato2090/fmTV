package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.db.AppDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
public class LiveSource {

    public static final String GITEE = "https://raw.giteeusercontent.com/kinnday/publicInfos/raw/master/m3u/";
    public static final String CDN = "https://cdn.jsdelivr.net/gh/iptv-org/iptv@master/streams/";
    public static final String SEARCH = "search";

    private static final Map<String, List<Group>> CACHE = new HashMap<>();

    @NonNull
    @PrimaryKey
    private String url;
    private String name;
    private int sort;
    private int enable;
    private long time;
    private int channelCount;
    private int groupCount;

    public LiveSource() {
    }

    @Ignore
    public LiveSource(String name, String url, int sort, int enable) {
        this.name = name;
        this.url = url;
        this.sort = sort;
        this.enable = enable;
    }

    @Ignore
    public LiveSource(String name, String url) {
        this(name, url, 0, 1);
    }

    public static LiveSource search(String name) {
        return new LiveSource(name, SEARCH);
    }

    public boolean isSearch() {
        return SEARCH.equals(getUrl());
    }

    private static List<LiveSource> preset() {
        List<LiveSource> items = new ArrayList<>();
        items.add(new LiveSource("央视卫视", GITEE + "acore.m3u", 0, 1));
        items.add(new LiveSource("国际精选", GITEE + "acore2.m3u", 1, 0));
        items.add(new LiveSource("台湾", GITEE + "tw.m3u", 2, 0));
        items.add(new LiveSource("新加坡", GITEE + "sg.m3u", 3, 0));
        items.add(new LiveSource("日本", GITEE + "jp.m3u", 4, 0));
        items.add(new LiveSource("韩国", GITEE + "kr.m3u", 5, 0));
        items.add(new LiveSource("法国", GITEE + "fr.m3u", 6, 0));
        items.add(new LiveSource("英国", GITEE + "uk.m3u", 7, 0));
        items.add(new LiveSource("西班牙", GITEE + "es.m3u", 8, 0));
        items.add(new LiveSource("巴西", GITEE + "br.m3u", 9, 0));
        items.add(new LiveSource("印度", CDN + "in.m3u", 10, 0));
        items.add(new LiveSource("美国", CDN + "us.m3u", 11, 0));
        return items;
    }

    public static List<LiveSource> getAll() {
        List<LiveSource> items = AppDatabase.get().getLiveSourceDao().findAll();
        if (!items.isEmpty()) return items;
        AppDatabase.get().getLiveSourceDao().insert(preset());
        return AppDatabase.get().getLiveSourceDao().findAll();
    }

    public static List<LiveSource> getEnabled() {
        List<LiveSource> items = new ArrayList<>();
        List<LiveSource> all = getAll();
        for (LiveSource item : all) if (item.isEnable()) items.add(item);
        if (items.isEmpty() && !all.isEmpty()) {
            all.get(0).enable(1).save();
            items.add(all.get(0));
        }
        return items;
    }

    public static LiveSource home() {
        List<LiveSource> items = getEnabled();
        return items.isEmpty() ? null : items.get(0);
    }

    public static LiveSource find(String url) {
        if (url == null || url.isEmpty()) return null;
        for (LiveSource item : getAll()) if (item.getUrl().equals(url)) return item;
        return null;
    }

    public static Set<String> urls() {
        Set<String> items = new java.util.HashSet<>();
        for (LiveSource item : getEnabled()) items.add(item.getUrl());
        return items;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static void clear(String url) {
        CACHE.remove(url);
    }

    /**
     * 频道全部不可用的分组不再显示，收藏分组始终保留。
     */
    public static void prune(Live home) {
        home.getGroups().removeIf(group -> !group.isKeep() && group.isEmpty());
    }

    /**
     * 把已启用的订阅源合并到直播主分组列表中。
     * 每次调用都会重建一次，因此重复调用不会产生重复分组。
     */
    public static void merge(Live home) {
        List<LiveSource> items = getEnabled();
        String primary = home.getUrl() == null ? "" : home.getUrl();
        LiveSource self = find(primary);
        Set<String> urls = urls();
        urls.add(primary);
        if (self != null && !CACHE.containsKey(primary) && !home.getGroups().isEmpty()) self.hold(new ArrayList<>(home.getGroups()));
        List<Group> groups = new ArrayList<>();
        for (LiveSource item : items) {
            if (self == null && item.getUrl().equals(primary)) continue;
            groups.addAll(item.copy());
        }
        if (self == null) {
            for (Group group : home.getGroups()) if (group.getSource().isEmpty()) group.setSource(primary);
            groups.addAll(0, home.getGroups());
        }
        groups.removeIf(group -> !group.isKeep() && !urls.contains(group.getSource()));
        home.getGroups().clear();
        home.getGroups().addAll(groups);
    }

    public boolean cached() {
        return CACHE.containsKey(url);
    }

    /**
     * 原始分组保存在缓存中，界面使用副本，
     * 因此被标记为不可用的频道在下次重新检测时仍会参与测试。
     */
    public List<Group> copy() {
        List<Group> items = new ArrayList<>();
        for (Group group : original()) {
            Group item = new Group(group.getName());
            item.setPass(group.getPass());
            item.setChannel(new ArrayList<>(group.getChannel()));
            item.setSource(url);
            items.add(item);
        }
        return items;
    }

    public List<Group> original() {
        List<Group> items = CACHE.get(url);
        if (items != null) return items;
        try {
            Live live = new Live(name, url);
            LiveParser.start(live.recent());
            hold(live.getGroups());
        } catch (Throwable e) {
            hold(Collections.emptyList());
        }
        return CACHE.get(url);
    }

    public void hold(List<Group> items) {
        for (Group item : items) item.setSource(url);
        CACHE.put(url, items);
        count(items);
        save();
    }

    private void count(List<Group> items) {
        int channels = 0;
        for (Group item : items) channels += item.getChannel().size();
        setGroupCount(items.size());
        setChannelCount(channels);
        setTime(System.currentTimeMillis());
    }

    public LiveSource enable(int enable) {
        this.enable = enable;
        return this;
    }

    public void toggle() {
        enable(isEnable() ? 0 : 1);
        save();
        LiveSource.syncConfig();
    }

    public void save() {
        if (SEARCH.equals(url)) return;
        AppDatabase.get().getLiveSourceDao().insertOrUpdate(this);
    }

    /**
     * 保证主配置地址始终指向第一个已启用的订阅源。
     */
    public static void syncConfig() {
        LiveSource item = home();
        if (item == null) return;
        Config config = Config.live();
        if (item.getUrl().equals(config.getUrl())) return;
        Config.find(item.getUrl(), item.getName(), 1).update();
    }

    @NonNull
    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public int getEnable() {
        return enable;
    }

    public void setEnable(int enable) {
        this.enable = enable;
    }

    public boolean isEnable() {
        return enable == 1;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public int getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }
}
