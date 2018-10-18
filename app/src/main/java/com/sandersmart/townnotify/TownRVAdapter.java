package com.sandersmart.townnotify;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Jeffrey Sander on 12/22/2017.
 */

public class TownRVAdapter extends RecyclerView.Adapter<TownRVAdapter.ViewHolder> {
    public ArrayList<Bundle> mDataset;
    public Location currentLocation;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView mTextView;
        public ImageView mImageView;
        public ViewHolder(CardView cv) {
            super(cv);
            mTextView = (TextView) cv.findViewById(R.id.townNameText);
            mImageView = (ImageView) cv.findViewById(R.id.townColorImageView);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public TownRVAdapter(ArrayList<Bundle> myDataset) {
        mDataset = myDataset;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public TownRVAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        // create a new view
//        TextView v = (TextView) LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.my_text_view, parent, false);
        CardView cv = (CardView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.town_card_view, parent, false);
        // set the view's size, margins, paddings and layout parameters
        //...
        ViewHolder vh = new ViewHolder(cv);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Location townLocation = mDataset.get(position).getParcelable("Location");
        int distance = Math.round(currentLocation.distanceTo(townLocation) / 1000);
        float bearing = currentLocation.bearingTo(townLocation);
        if(bearing < 0) bearing = 360 + bearing;
        String bearingString = bearingToString(bearing);
        holder.mImageView.setBackgroundColor(mDataset.get(position).getInt("Color", Color.BLACK));
        if(distance == 0) {
            holder.mTextView.setText("You are in " + mDataset.get(position).getString("Town"));
        } else {
            holder.mTextView.setText(mDataset.get(position).getString("Town") + " is about " + distance + " KM  to the " + bearingString);
        }

    }

    private String bearingToString(float bearing) {
        String bearingString = "";
        if(bearing > 337.5 || bearing < 22.5) {
            bearingString = "North";
        } else if(bearing > 22.5 && bearing < 67.5) {
            bearingString = "North East";
        } else if (bearing > 67.5 && bearing < 112.5) {
            bearingString = "East";
        } else if (bearing > 112.5 && bearing < 157.5) {
            bearingString = "South East";
        } else if (bearing > 157.5 && bearing < 202.5) {
            bearingString = "South";
        } else if (bearing > 202.5 && bearing < 247.5) {
            bearingString = "South West";
        } else if (bearing > 247.5 && bearing < 292.5) {
            bearingString = "West";
        } else if (bearing > 292.5) {
            bearingString = "North West";
        }
        return bearingString;
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}