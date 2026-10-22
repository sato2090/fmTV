package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.LiveSource;

import java.util.List;

@Dao
public abstract class LiveSourceDao extends BaseDao<LiveSource> {

    @Query("SELECT * FROM LiveSource ORDER BY sort")
    public abstract List<LiveSource> findAll();

    @Query("DELETE FROM LiveSource")
    public abstract void deleteAll();
}
