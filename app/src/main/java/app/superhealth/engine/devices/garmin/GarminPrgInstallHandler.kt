package app.superhealth.engine.devices.garmin

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.install.InstallHandlerV2
import app.superhealth.engine.install.InstallItem
import app.superhealth.engine.install.ParsedFiles
import app.superhealth.engine.install.Compatibility
import app.superhealth.engine.service.devices.garmin.GarminPrgFile
import app.superhealth.engine.util.UriUtils.fileNameOf
import org.slf4j.LoggerFactory

class GarminPrgInstallHandler(
    private val context: Context,
    private val coordinator: GarminCoordinator,
) : InstallHandlerV2 {
    override fun prepare(files: List<Uri>): ParsedFiles {
        val prgFiles = files.associateWith { GarminPrgFile.open(context, it) }
        val notPrg = prgFiles.filterValues { it == null }.keys
        if (notPrg.isNotEmpty()) {
            LOG.warn("Not PRG files: {}", notPrg)
            return ParsedFiles.Unsupported(
                context.getString(
                    R.string.install_files_not_supported,
                    notPrg.joinToString(", ") { fileNameOf(context, it) })
            )
        }

        // TODO parse prg and identify type and name
        val items = prgFiles.values.filterNotNull().map {
            GarminPrgInstallItem(it, coordinator.defaultIconResource)
        }
        return ParsedFiles.Supported(items, context.getString(R.string.fw_upgrade_notice, "PRG"))
    }

    override fun checkCompatibility(parsedFiles: ParsedFiles.Supported, device: GBDevice): Compatibility {
        // FIXME this might not be the correct capability
        if (coordinator.supports(device, GarminCapability.CONNECTIQ_APP_MANAGEMENT)) {
            return Compatibility.Compatible()
        }
        if (!GBApplication.getDevicePrefs(device).installUnsupportedFiles()) {
            return Compatibility.Incompatible(context.getString(R.string.fwapp_install_device_not_supported))
        }
        return Compatibility.Compatible(listOf(context.getString(R.string.install_unsupported_files_warning)))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GarminPrgInstallHandler::class.java)
    }
}

class GarminPrgInstallItem(val file: GarminPrgFile, @DrawableRes icon: Int) :
    InstallItem(file.name, icon, file.size)
