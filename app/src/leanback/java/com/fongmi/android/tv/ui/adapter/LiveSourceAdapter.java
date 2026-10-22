package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.LiveSource;
import com.fongmi.android.tv.databinding.AdapterSubscribeBinding;

import java.util.ArrayList;
import java.util.List;

public class LiveSourceAdapter extends RecyclerView.Adapter<LiveSourceAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<LiveSource> mItems;

    public LiveSourceAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    /**
     * 第一项为「全部频道」搜索入口，其余为已启用的订阅源。
     */
    public void addAll(List<LiveSource> items, String label) {
        mItems.clear();
        mItems.add(LiveSource.search(label));
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public LiveSource get(int position) {
        return mItems.isEmpty() ? null : mItems.get(position);
    }

    public int indexOf(String url) {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).getUrl().equals(url)) return i;
        return -1;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSubscribeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveSource item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(LiveSource item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSubscribeBinding binding;

        ViewHolder(@NonNull AdapterSubscribeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
