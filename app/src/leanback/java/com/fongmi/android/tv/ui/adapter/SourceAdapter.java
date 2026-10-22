package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.InvalidChannel;
import com.fongmi.android.tv.bean.LiveSource;
import com.fongmi.android.tv.databinding.AdapterSourceBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<LiveSource> mItems;

    public SourceAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<LiveSource> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public LiveSource get(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveSource item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.url.setText(item.getUrl());
        holder.binding.stat.setText(ResUtil.getString(R.string.source_stat, item.getGroupCount(), item.getChannelCount()).concat(" · ").concat(ResUtil.getString(R.string.source_invalid, InvalidChannel.count(item.getUrl()))));
        holder.binding.state.setText(item.isEnable() ? R.string.source_enabled : R.string.source_disabled);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            mListener.onLongClick(item);
            return true;
        });
    }

    public interface OnClickListener {

        void onItemClick(LiveSource item);

        void onLongClick(LiveSource item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSourceBinding binding;

        ViewHolder(@NonNull AdapterSourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
