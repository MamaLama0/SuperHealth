/*  Copyright (C) 2024 Me7c7

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

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.MusicControl;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendUploadMusicFileInfoResponse extends Request {
    short songIndex;
    String songName;
    String songArtist;


    public SendUploadMusicFileInfoResponse(HuaweiSupportProvider support, short songIndex, String songName, String songArtist) {
        super(support);
        this.serviceId = MusicControl.id;
        this.commandId = MusicControl.UploadMusicFileInfo.id;
        this.songIndex = songIndex;
        this.songName = songName;
        this.songArtist = songArtist;
        this.addToResponse = false;
    }

    @Override
    protected List<byte[]> createRequest() throws Request.RequestCreationException {
        try {
            return new MusicControl.UploadMusicFileInfo.UploadMusicFileInfoResponse(this.paramsProvider,  this.songIndex, this.songName, this.songArtist).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new Request.RequestCreationException(e);
        }
    }
}