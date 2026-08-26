/*
 * Copyright (C) 2026 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.activities.launcher.widgets.pins;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import adrianogba.stario.launcher.R;
import adrianogba.stario.launcher.apps.Category;
import adrianogba.stario.launcher.apps.LauncherApplication;
import adrianogba.stario.launcher.themes.ThemedActivity;
import adrianogba.stario.launcher.ui.icons.AdaptiveIconView;
import adrianogba.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter;
import adrianogba.stario.launcher.ui.recyclers.async.InflationType;

import java.util.function.Supplier;

class PinnedAppsGroupAdapter extends AsyncRecyclerAdapter<PinnedAppsGroupAdapter.ViewHolder> {
    private final int startingIndex;
    private final Category category;

    public PinnedAppsGroupAdapter(ThemedActivity activity, Category category, int startingIndex) {
        super(activity, InflationType.SYNCED);

        this.category = category;
        this.startingIndex = startingIndex;
    }

    public class ViewHolder extends AsyncViewHolder {
        private AdaptiveIconView icon;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void onInflated() {
            icon = itemView.findViewById(R.id.icon);
        }
    }

    @Override
    public void onBind(@NonNull ViewHolder viewHolder, int index) {
        viewHolder.icon.setApplication(getApplication(index));
    }

    @Override
    protected int getLayout(int viewType) {
        return R.layout.pinned_application_group_item;
    }

    @Override
    protected Supplier<PinnedAppsGroupAdapter.ViewHolder> getHolderSupplier(int viewType) {
        return PinnedAppsGroupAdapter.ViewHolder::new;
    }

    protected LauncherApplication getApplication(int index) {
        return category != null ?
                category.get(index + startingIndex) : LauncherApplication.FALLBACK_APP;
    }

    @Override
    public int getTotalItemCount() {
        return Math.min(category != null ? Math.max(0, category.getSize() - startingIndex) : 0, 4);
    }
}
