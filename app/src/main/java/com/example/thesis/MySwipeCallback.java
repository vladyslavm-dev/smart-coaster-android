package com.example.thesis;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ItemTouchHelper callback that enables swipe-to-delete on
 * the patient event list with a confirmation dialog.
 */
public class MySwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final PatientEventAdapter adapter;
    private final Context context;

    public MySwipeCallback(Context context, PatientEventAdapter adapter) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.context = context;
        this.adapter = adapter;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        // Drag-and-drop reordering is not supported here.
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        final int position = viewHolder.getAdapterPosition();
        final long eventId = adapter.getItemId(position);

        new AlertDialog.Builder(context)
                .setTitle("Delete Entry?")
                .setMessage("Are you sure you want to delete?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Remove by stable ID so both adapter and DataManager stay in sync.
                    adapter.deleteEventById(eventId);
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Revert swipe if user cancels.
                    adapter.notifyItemChanged(position);
                })
                .show();
    }
}
