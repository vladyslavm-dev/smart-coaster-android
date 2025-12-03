package com.example.thesis;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter showing the list of WaterEvent entries
 * for a single patient. Uses stable IDs based on a uniqueId per event
 * so swipe deletion can refer to a consistent identifier.
 */
public class PatientEventAdapter extends RecyclerView.Adapter<PatientEventAdapter.ViewHolder> {

    private final Context adapterContext;
    private final int patientIndex;
    private List<WaterEvent> data; // local snapshot of the DataManager list

    public PatientEventAdapter(Context context, int patientIndex) {
        this.adapterContext = context.getApplicationContext();
        this.patientIndex = patientIndex;
        // Initial load from DataManager.
        data = new ArrayList<>(DataManager.getInstance(adapterContext)
                .getEventsForPatient(patientIndex));

        // Each item has a stable uniqueId.
        setHasStableIds(true);
    }

    /**
     * Reloads the adapter data from the DataManager snapshot.
     */
    public void refreshData() {
        data.clear();
        data.addAll(DataManager.getInstance(adapterContext)
                .getEventsForPatient(patientIndex));
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        // Use each event’s uniqueId to keep IDs stable across updates.
        return data.get(position).getUniqueId();
    }

    @Override
    public @NonNull ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_event, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WaterEvent e = data.get(position);

        // Convert "I" => "Intake", "R" => "Refill" for display.
        String displayType = e.type.equals("I") ? "Intake"
                : e.type.equals("R") ? "Refill"
                : e.type;

        holder.tvTimestamp.setText(e.timestamp);
        holder.tvType.setText(displayType);
        holder.tvAmount.setText(String.format("%.2f", e.amount));
        holder.tvCup.setText(e.cupName);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /**
     * Deletes an event based on its unique ID and updates both
     * DataManager and the local adapter list.
     */
    public void deleteEventById(long eventId) {
        // 1) Remove from DataManager.
        DataManager.getInstance(adapterContext).removeEvent(patientIndex, eventId);

        // 2) Remove from the local list for the adapter.
        int foundPos = -1;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getUniqueId() == eventId) {
                foundPos = i;
                break;
            }
        }
        if (foundPos >= 0) {
            data.remove(foundPos);
            notifyItemRemoved(foundPos);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimestamp, tvType, tvAmount, tvCup;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvType = itemView.findViewById(R.id.tv_type);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvCup = itemView.findViewById(R.id.tv_cup);
        }
    }
}
