/**
 *   Nextcloud Android client application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2016 Andy Scherzinger
 * Copyright (C) 2016 Nextcloud
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.telkomsigma.telkomstorage.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.sectionedrecyclerview.SectionedRecyclerViewAdapter;
import com.telkomsigma.telkomstorage.datamodel.MediaFolderType;
import com.telkomsigma.telkomstorage.datamodel.SyncedFolderDisplayItem;
import com.telkomsigma.telkomstorage.datamodel.ThumbnailsCacheManager;
import com.telkomsigma.telkomstorage.utils.ThemeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to display all auto-synced folders and/or instant upload media folders.
 */
public class SyncedFolderAdapter extends SectionedRecyclerViewAdapter<SyncedFolderAdapter.MainViewHolder> {

    private final Context mContext;
    private final int mGridWidth;
    private final int mGridTotal;
    private final ClickListener mListener;
    private final List<SyncedFolderDisplayItem> mSyncFolderItems;
    private final boolean mLight;

    public SyncedFolderAdapter(Context context, int gridWidth, ClickListener listener, boolean light) {
        mContext = context;
        mGridWidth = gridWidth;
        mGridTotal = gridWidth * 2;
        mListener = listener;
        mSyncFolderItems = new ArrayList<>();
        mLight = light;

        shouldShowHeadersForEmptySections(true);
    }

    public void setSyncFolderItems(List<SyncedFolderDisplayItem> syncFolderItems) {
        mSyncFolderItems.clear();
        mSyncFolderItems.addAll(syncFolderItems);
    }

    public void setSyncFolderItem(int location, SyncedFolderDisplayItem syncFolderItem) {
        mSyncFolderItems.set(location, syncFolderItem);
        notifyDataSetChanged();
    }

    public void addSyncFolderItem(SyncedFolderDisplayItem syncFolderItem) {
        mSyncFolderItems.add(syncFolderItem);
        notifyDataSetChanged();
    }

    public void removeItem(int section) {
        mSyncFolderItems.remove(section);
        notifyDataSetChanged();
    }

    @Override
    public int getSectionCount() {
        return mSyncFolderItems.size();
    }

    @Override
    public int getItemCount(int section) {
        if (mSyncFolderItems.get(section).getFilePaths() != null) {
            return mSyncFolderItems.get(section).getFilePaths().size();
        } else {
            return 1;
        }
    }

    public SyncedFolderDisplayItem get(int section) {
        return mSyncFolderItems.get(section);
    }

    @Override
    public void onBindHeaderViewHolder(final MainViewHolder holder, final int section) {
        holder.mainHeaderContainer.setVisibility(View.VISIBLE);

        holder.title.setText(mSyncFolderItems.get(section).getFolderName());

        if (MediaFolderType.VIDEO == mSyncFolderItems.get(section).getType()) {
            holder.type.setImageResource(com.telkomsigma.telkomstorage.R.drawable.ic_video_18dp);
        } else if (MediaFolderType.IMAGE == mSyncFolderItems.get(section).getType()) {
            holder.type.setImageResource(com.telkomsigma.telkomstorage.R.drawable.ic_image_18dp);
        } else {
            holder.type.setImageResource(com.telkomsigma.telkomstorage.R.drawable.ic_folder_star_18dp);
        }

        holder.syncStatusButton.setVisibility(View.VISIBLE);
        holder.syncStatusButton.setTag(section);
        holder.syncStatusButton.setOnClickListener(v -> {
            mSyncFolderItems.get(section).setEnabled(!mSyncFolderItems.get(section).isEnabled());
            setSyncButtonActiveIcon(holder.syncStatusButton, mSyncFolderItems.get(section).isEnabled());
            mListener.onSyncStatusToggleClick(section, mSyncFolderItems.get(section));
        });
        setSyncButtonActiveIcon(holder.syncStatusButton, mSyncFolderItems.get(section).isEnabled());

        holder.syncStatusButton.setVisibility(View.VISIBLE);
        holder.syncStatusButton.setTag(section);
        holder.syncStatusButton.setOnClickListener(v -> {
            mSyncFolderItems.get(section).setEnabled(!mSyncFolderItems.get(section).isEnabled());
            setSyncButtonActiveIcon(holder.syncStatusButton, mSyncFolderItems.get(section).isEnabled());
            mListener.onSyncStatusToggleClick(section, mSyncFolderItems.get(section));
        });
        setSyncButtonActiveIcon(holder.syncStatusButton, mSyncFolderItems.get(section).isEnabled());

        if (mLight) {
            holder.menuButton.setVisibility(View.GONE);
        } else {
            holder.menuButton.setVisibility(View.VISIBLE);
            holder.menuButton.setTag(section);
            holder.menuButton.setOnClickListener(v -> mListener.onSyncFolderSettingsClick(section,
                    mSyncFolderItems.get(section)));
        }
    }


    @Override
    public void onBindViewHolder(MainViewHolder holder, int section, int relativePosition, int absolutePosition) {
        if (mSyncFolderItems.get(section).getFilePaths() != null) {
            File file = new File(mSyncFolderItems.get(section).getFilePaths().get(relativePosition));

            ThumbnailsCacheManager.MediaThumbnailGenerationTask task =
                    new ThumbnailsCacheManager.MediaThumbnailGenerationTask(holder.image);

            ThumbnailsCacheManager.AsyncMediaThumbnailDrawable asyncDrawable =
                    new ThumbnailsCacheManager.AsyncMediaThumbnailDrawable(
                            mContext.getResources(),
                            ThumbnailsCacheManager.mDefaultImg,
                            task
                    );
            holder.image.setImageDrawable(asyncDrawable);

            task.execute(file);

            // set proper tag
            holder.image.setTag(file.hashCode());

            holder.itemView.setTag(relativePosition % mGridWidth);

            if (mSyncFolderItems.get(section).getNumberOfFiles() > mGridTotal && relativePosition >= mGridTotal - 1) {
                holder.counterValue.setText(Long.toString(mSyncFolderItems.get(section).getNumberOfFiles() - mGridTotal));
                holder.counterBar.setVisibility(View.VISIBLE);
                holder.thumbnailDarkener.setVisibility(View.VISIBLE);
            } else {
                holder.counterBar.setVisibility(View.GONE);
                holder.thumbnailDarkener.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public MainViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                viewType == VIEW_TYPE_HEADER ?
                        com.telkomsigma.telkomstorage.R.layout.synced_folders_item_header : com.telkomsigma.telkomstorage.R.layout.grid_sync_item, parent, false);
        return new MainViewHolder(v);
    }

    public interface ClickListener {
        void onSyncStatusToggleClick(int section, SyncedFolderDisplayItem syncedFolderDisplayItem);
        void onSyncFolderSettingsClick(int section, SyncedFolderDisplayItem syncedFolderDisplayItem);
    }

    static class MainViewHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView title;
        private final ImageView type;
        private final ImageButton menuButton;
        private final ImageButton syncStatusButton;
        private final LinearLayout counterBar;
        private final TextView counterValue;
        private final ImageView thumbnailDarkener;

        private final RelativeLayout mainHeaderContainer;

        private MainViewHolder(View itemView) {
            super(itemView);
            mainHeaderContainer = (RelativeLayout) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.header_container);
            image = (ImageView) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.thumbnail);
            title = (TextView) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.title);
            type = (ImageView) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.type);
            menuButton = (ImageButton) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.settingsButton);
            syncStatusButton = (ImageButton) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.syncStatusButton);
            counterBar = (LinearLayout) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.counterLayout);
            counterValue = (TextView) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.counter);
            thumbnailDarkener = (ImageView) itemView.findViewById(com.telkomsigma.telkomstorage.R.id.thumbnailDarkener);
        }
    }

    private void setSyncButtonActiveIcon(ImageButton syncStatusButton, boolean enabled) {
        if (enabled) {
            syncStatusButton.setImageDrawable(ThemeUtils.tintDrawable(com.telkomsigma.telkomstorage.R.drawable.ic_cloud_sync_on,
                    ThemeUtils.primaryColor()));
        } else {
            syncStatusButton.setImageResource(com.telkomsigma.telkomstorage.R.drawable.ic_cloud_sync_off);
        }
    }
}
