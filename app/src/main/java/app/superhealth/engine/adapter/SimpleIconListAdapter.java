/*  Copyright (C) 2025 José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import app.superhealth.engine.R;
import app.superhealth.engine.model.RunnableListIconItem;

public class SimpleIconListAdapter extends ArrayAdapter<RunnableListIconItem> {
    private final Context context;
    private final List<RunnableListIconItem> items;

    public SimpleIconListAdapter(final Context context, final List<RunnableListIconItem> items) {
        super(context, R.layout.simple_list_item_with_icon, items);
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull final ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.simple_list_item_with_icon, parent, false);
        }

        final ImageView icon = convertView.findViewById(R.id.option_icon);
        final TextView title = convertView.findViewById(R.id.option_title);

        final RunnableListIconItem item = items.get(position);
        icon.setImageResource(item.getIconResId());
        title.setText(item.getTitle());

        return convertView;
    }
}
