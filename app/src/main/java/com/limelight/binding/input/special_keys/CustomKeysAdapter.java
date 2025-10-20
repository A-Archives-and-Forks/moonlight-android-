package com.limelight.binding.input.special_keys;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;

import java.util.Collections;
import java.util.List;

public class CustomKeysAdapter extends RecyclerView.Adapter<CustomKeysAdapter.ViewHolder> {

    public interface Callbacks {
        void onClick(SpecialKeyManager.CustomKey key, int position);
        void onListReordered(List<SpecialKeyManager.CustomKey> newList);
    }

    private final List<SpecialKeyManager.CustomKey> items;
    private final Callbacks callbacks;
    private final LayoutInflater inflater;

    // Provide a way to start drag from outside (ItemTouchHelper)
    public interface StartDragListener {
        void requestDrag(RecyclerView.ViewHolder viewHolder);
    }
    private final StartDragListener startDragListener;

    public CustomKeysAdapter(Context context,
                             RecyclerView recyclerView,
                             List<SpecialKeyManager.CustomKey> items,
                             Callbacks callbacks,
                             StartDragListener startDragListener) {
        this.inflater = LayoutInflater.from(context);
        this.items = items;
        this.callbacks = callbacks;
        this.startDragListener = startDragListener;
    }

    @NonNull
    @Override
    public CustomKeysAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_custom_key, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomKeysAdapter.ViewHolder holder, int position) {
        SpecialKeyManager.CustomKey key = items.get(position);
        holder.labelText.setText(key.label);

        // Click item to show edit/delete dialog
        holder.itemView.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onClick(key, position); // we will treat this as "request edit/delete dialog"
            }
        });

        // Drag handle logic stays the same
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (startDragListener != null) {
                    startDragListener.requestDrag(holder);
                }
            }
            return false;
        });
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<SpecialKeyManager.CustomKey> getItems() {
        return items;
    }

    public void updateList(List<SpecialKeyManager.CustomKey> newList) {
        items.clear();
        items.addAll(newList);
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size())
            return;
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        if (callbacks != null) callbacks.onListReordered(items);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final TextView labelText;
        final TextView keysText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            labelText = itemView.findViewById(R.id.label_text);
            keysText = itemView.findViewById(R.id.keys_text);
        }
    }
}
