/*  Copyright (C) 2024 Vitalii Tomin

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

package app.superhealth.engine.service.devices.huawei.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.devices.huawei.HuaweiConstants;
import app.superhealth.engine.devices.huawei.HuaweiPacket.CryptoException;
import app.superhealth.engine.devices.huawei.packets.AccountRelated;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendExtendedAccountRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SendExtendedAccountRequest.class);

    public SendExtendedAccountRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = AccountRelated.id;
        this.commandId = AccountRelated.SendExtendedAccountToDevice.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsAccountJudgment() && supportProvider.getDeviceState().supportsAccountSwitch();
    }

    @Override
    protected List<byte[]> createRequest() throws Request.RequestCreationException {
        String account = GBApplication
                .getDeviceSpecificSharedPrefs(supportProvider.getDevice().getAddress())
                .getString(HuaweiConstants.PREF_HUAWEI_ACCOUNT, "").trim();
        try {
            return new AccountRelated.SendExtendedAccountToDevice.Request(
                    paramsProvider,
                    supportProvider.getDeviceState().supportsDiffAccountPairingOptimization(),
                    account)
                    .serialize();
        } catch (CryptoException e) {
            throw new Request.RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() throws Request.ResponseParseException {
        LOG.debug("handle Send Extended Account to Device");
    }
}
