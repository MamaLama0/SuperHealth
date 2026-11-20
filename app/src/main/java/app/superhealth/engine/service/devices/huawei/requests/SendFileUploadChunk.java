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

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.FileUpload;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;
import app.superhealth.engine.service.devices.huawei.HuaweiUploadManager;

public class SendFileUploadChunk extends Request {
    HuaweiUploadManager huaweiUploadManager;
    public SendFileUploadChunk(HuaweiSupportProvider support,
                               HuaweiUploadManager watchfaceManager) {
        super(support);
        this.huaweiUploadManager = watchfaceManager;
        this.serviceId = FileUpload.id;
        this.commandId = FileUpload.FileNextChunkSend.id;
        this.addToResponse = false;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            boolean isEncrypted = huaweiUploadManager.getFileUploadInfo().getEncrypt() && paramsProvider.areTransactionsCrypted();
            return new FileUpload.FileNextChunkSend(this.paramsProvider).serializeFileChunk(
                    huaweiUploadManager.getFileUploadInfo().getCurrentChunk(),
                    huaweiUploadManager.getFileUploadInfo().getCurrentUploadPosition(),
                    huaweiUploadManager.getFileUploadInfo().getUnitSize(),
                    huaweiUploadManager.getFileUploadInfo().getFileId(),
                    isEncrypted
            );
        } catch(HuaweiPacket.SerializeException e) {
            throw new RequestCreationException(e.getMessage());
        }
    }
}
