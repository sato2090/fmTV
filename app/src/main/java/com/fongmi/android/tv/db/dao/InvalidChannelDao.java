package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.InvalidChannel;

import java.util.List;

@Dao
public abstract class InvalidChannelDao extends BaseDao<InvalidChannel> {

    @Query("SELECT `key` FROM InvalidChannel")
    public abstract List<String> keys();

    @Query("SELECT * FROM InvalidChannel WHERE `key` = :key")
    public abstract InvalidChannel find(String key);

    @Query("SELECT COUNT(*) FROM InvalidChannel")
    public abstract int count();

    @Query("SELECT COUNT(*) FROM InvalidChannel WHERE `key` LIKE :prefix || '%'")
    public abstract int count(String prefix);

    @Query("DELETE FROM InvalidChannel WHERE `key` LIKE :source || '%'")
    public abstract void deleteBySource(String source);

    @Query("DELETE FROM InvalidChannel WHERE `key` = :key")
    public abstract void delete(String key);

    @Query("DELETE FROM InvalidChannel")
    public abstract void deleteAll();
}
