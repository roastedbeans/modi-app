package com.example.modiv3;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SignalingCardAdapter extends RecyclerView.Adapter<SignalingCardAdapter.ViewHolder> {
    
    private List<SignalingEvent> events = new ArrayList<>();
    private OnCardClickListener onCardClickListener;
    
    public interface OnCardClickListener {
        void onCardClick(SignalingEvent event);
    }
    
    public void setOnCardClickListener(OnCardClickListener listener) {
        this.onCardClickListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_signaling_card, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SignalingEvent event = events.get(position);
        
        // Set technology badge
        holder.technologyBadge.setText(event.getTechnology().name());
        holder.technologyBadge.getBackground().setTint(Color.parseColor(event.getTechnology().getColor()));
        
        // Set direction arrow
        holder.directionArrow.setText(event.getDirection().getArrow());
        holder.directionArrow.setTextColor(Color.parseColor(event.getDirection().getColor()));
        
        // Set content
        holder.messageTitle.setText(event.getMessageTitle());
        holder.description.setText(event.getDescription());
        holder.timestamp.setText(event.getTimestamp());
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (onCardClickListener != null) {
                onCardClickListener.onCardClick(event);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return events.size();
    }
    
    public void addEvent(SignalingEvent event) {
        events.add(0, event); // Add to top
        notifyItemInserted(0);
    }
    
    public void clearEvents() {
        events.clear();
        notifyDataSetChanged();
    }
    
    public void setEvents(List<SignalingEvent> newEvents) {
        events.clear();
        events.addAll(newEvents);
        notifyDataSetChanged();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView technologyBadge;
        TextView directionArrow;
        TextView messageTitle;
        TextView description;
        TextView timestamp;
        
        ViewHolder(View itemView) {
            super(itemView);
            technologyBadge = itemView.findViewById(R.id.technology_badge);
            directionArrow = itemView.findViewById(R.id.direction_arrow);
            messageTitle = itemView.findViewById(R.id.message_title);
            description = itemView.findViewById(R.id.description);
            timestamp = itemView.findViewById(R.id.timestamp);
        }
    }
}
