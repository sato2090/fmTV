package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;

import java.util.HashSet;
import java.util.Set;

@Entity
public class InvalidChannel {

    @NonNull
    @PrimaryKey
    private String key;
    private long time;

    public InvalidChannel() {
    }

    @Ignore
    public InvalidChannel(@NonNull String key) {
        this.key = key;
        this.time = System.currentTimeMillis();
    }

    /**
     * 失效标记的主键：订阅源地址 + 频道名。
     * 使用订阅源地址而非主源名称，避免多个源之间同名频道互相误杀。
     */
    public static String key(String source, String channel) {
        String name = source == null ? "" : source;
        String value = channel == null ? "" : channel;
        return name.concat(AppDatabase.SYMBOL).concat(value);
    }

    public static void delete(String key) {
        AppDatabase.get().getInvalidChannelDao().delete(key);
    }

    public static void deleteBySource(String source) {
        AppDatabase.get().getInvalidChannelDao().deleteBySource(source.concat(AppDatabase.SYMBOL));
    }

    /**
     * 某个订阅源下被标记为不可用的频道数量。
     */
    public static int count(String source) {
        return AppDatabase.get().getInvalidChannelDao().count(source == null ? "" : source.concat(AppDatabase.SYMBOL));
    }

    public static Set<String> keys() {
        return new HashSet<>(AppDatabase.get().getInvalidChannelDao().keys());
    }

    public static boolean exist(String key) {
        return AppDatabase.get().getInvalidChannelDao().find(key) != null;
    }

    public static int count() {
        return AppDatabase.get().getInvalidChannelDao().count();
    }

    public static void save(String key) {
        AppDatabase.get().getInvalidChannelDao().insertOrUpdate(new InvalidChannel(key));
    }

    public static void deleteAll() {
        AppDatabase.get().getInvalidChannelDao().deleteAll();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
