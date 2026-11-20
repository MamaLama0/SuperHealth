/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniel
    Dakhno, José Rebelo, Petr Vaněk, Taavi Eomäe

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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.util.DeviceHelper;
import app.superhealth.engine.util.GB;

/**
 * Adapter for displaying GBDeviceCandate instances.
 */
public class DeviceCandidateAdapter extends ArrayAdapter<GBDeviceCandidate> {
    private final ColorMatrixColorFilter grayscaleFilter;

    public DeviceCandidateAdapter(final Context context, final List<GBDeviceCandidate> deviceCandidates) {
        super(context, 0, deviceCandidates);
        final ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        grayscaleFilter = new ColorMatrixColorFilter(colorMatrix);
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull final ViewGroup parent) {
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_device_candidate, parent, false);
            final ImageView imageView = view.findViewById(R.id.candidate_image);
            imageView.setColorFilter(grayscaleFilter);
        }
        final ImageView deviceImageView = view.findViewById(R.id.candidate_image);
        final TextView deviceNameLabel = view.findViewById(R.id.candidate_name);
        final TextView deviceTypeLabel = view.findViewById(R.id.candidate_type);
        final TextView deviceAddressLabel = view.findViewById(R.id.candidate_address);
        final TextView deviceStatusLabel = view.findViewById(R.id.candidate_status);
        final TextView deviceRssiLabel = view.findViewById(R.id.candidate_rssi);

        final GBDeviceCandidate device = getItem(position);
        if (device == null) {
            return view;
        }

        final DeviceType deviceType = DeviceHelper.getInstance().resolveDeviceType(device);
        final DeviceCoordinator coordinator = deviceType.getDeviceCoordinator();

        deviceNameLabel.setText(device.getName());
        deviceTypeLabel.setText(coordinator.getDeviceNameResource());
        deviceAddressLabel.setText(device.getMacAddress());

        deviceImageView.setImageResource(coordinator.getDefaultIconResource());

        if (device.getRssi() > GBDevice.RSSI_UNKNOWN) {
            deviceRssiLabel.setText(getContext().getString(R.string.device_rssi_dbm, GB.formatRssi(device.getRssi())));
            deviceRssiLabel.setVisibility(View.VISIBLE);
        } else {
            deviceRssiLabel.setVisibility(View.GONE);
        }

        final List<String> statusLines = new ArrayList<>();
        if (device.isBonded()) {
            statusLines.add(getContext().getString(R.string.device_is_currently_bonded));
        }
        if (!deviceType.isSupported()) {
            statusLines.add(getContext().getString(R.string.device_unsupported));
        }
        if (coordinator.isExperimental()) {
            statusLines.add(getContext().getString(R.string.device_experimental));
        }
        deviceStatusLabel.setText(TextUtils.join("\n", statusLines));
        deviceStatusLabel.setVisibility(statusLines.isEmpty() ? View.GONE : View.VISIBLE);

        return view;
    }
}
