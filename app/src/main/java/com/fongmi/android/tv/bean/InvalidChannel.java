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

    public static String key(String live, String channel) {
        String name = live == null ? "" : live;
        String value = channel == null ? "" : channel;
        return name.concat(AppDatabase.SYMBOL).concat(value);
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
