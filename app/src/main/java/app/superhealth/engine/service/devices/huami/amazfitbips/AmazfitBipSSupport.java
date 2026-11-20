/*  Copyright (C) 2020-2024 Andreas Shimokawa, beardhatcode, José Rebelo,
    Zhong Jianxin

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
package app.superhealth.engine.service.devices.huami.amazfitbips;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.amazfitbips.AmazfitBipSFWHelper;
import app.superhealth.engine.model.CallSpec;
import app.superhealth.engine.model.WeatherSpec;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.huami.amazfitbip.AmazfitBipSupport;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperation2020;
import app.superhealth.engine.service.devices.huami.operations.update.UpdateFirmwareOperationNew;
import app.superhealth.engine.util.Version;

public class AmazfitBipSSupport extends AmazfitBipSupport {
    @Override
    public byte getCryptFlags() {
        return (byte) 0x80;
    }

    @Override
    protected boolean notificationHasExtraHeader() {
        return true;
    }

    @Override
    protected int notificationMaxLength() {
        return 512;
    }

    @Override
    protected byte getAuthFlags() {
        return 0x00;
    }

    @Override
    public boolean supportsSunriseSunsetWindHumidity() {
        Version version = new Version(gbDevice.getFirmwareVersion());
        return (!isDTH(version) && (version.compareTo(new Version("2.1.1.50")) >= 0) || (version.compareTo(new Version("4.1.5.55")) >= 0));
    }

    @Override
    public String windSpeedString(WeatherSpec weatherSpec){
        return weatherSpec.getWindSpeed() + "km/h";
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        onSetCallStateNew(callSpec);
    }

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new AmazfitBipSFWHelper(uri, context);
    }

    @Override
    public UpdateFirmwareOperation createUpdateFirmwareOperation(Uri uri) {
        Version version = new Version(gbDevice.getFirmwareVersion());
        if ((!isDTH(version) && (version.compareTo(new Version("2.1.1.50")) >= 0) || (version.compareTo(new Version("4.1.5.55")) >= 0))) {
            return new UpdateFirmwareOperation2020(uri, this);
        }

        return new UpdateFirmwareOperationNew(uri, this);
    }

    @Override
    protected AmazfitBipSSupport setDisplayItems(TransactionBuilder builder) {
        setDisplayItemsNew(builder, false, true, R.array.pref_bips_display_items_default);
        return this;
    }

    @Override
    protected void setShortcuts(TransactionBuilder builder) {
        setDisplayItemsNew(builder, true, true, R.array.pref_bips_display_items_default);
    }

    private boolean isDTH(Version version) {
        return version.compareTo(new Version("4.0.0.00")) >= 0;
    }
}
