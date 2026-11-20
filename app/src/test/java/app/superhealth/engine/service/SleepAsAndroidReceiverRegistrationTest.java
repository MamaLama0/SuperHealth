/*  Copyright (C) 2026 Dany Mestas

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
package app.superhealth.engine.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.test.TestBase;
import app.superhealth.engine.util.GBPrefs;
import app.superhealth.engine.util.Prefs;

/**
 * Sleep as Android starts tracking while every device may be disconnected, so whether the receiver
 * is registered cannot depend on the connection state. That was the original defect: with nothing
 * connected SuperHealth was not listening at all and the broadcast died in the OS.
 */
public class SleepAsAndroidReceiverRegistrationTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";

    private Prefs prefs;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        prefs = GBApplication.getPrefs();
        prefs.getPreferences().edit()
                .remove(GBPrefs.SLEEP_AS_ANDROID_ENABLED)
                .remove(GBPrefs.SLEEP_AS_ANDROID_DEVICE)
                .apply();
    }

    private void configure(final boolean enabled, final String address) {
        prefs.getPreferences().edit()
                .putBoolean(GBPrefs.SLEEP_AS_ANDROID_ENABLED, enabled)
                .putString(GBPrefs.SLEEP_AS_ANDROID_DEVICE, address)
                .apply();
    }

    private boolean shouldRegister() {
        return DeviceReceiversManager.shouldRegisterSleepAsAndroidReceiver(prefs, true);
    }

    @Test
    public void registeredWhenConfigured() {
        configure(true, ADDRESS);
        Assert.assertTrue(shouldRegister());
    }

    @Test
    public void notRegisteredWhenTheIntegrationIsDisabled() {
        configure(false, ADDRESS);
        Assert.assertFalse(shouldRegister());
    }

    @Test
    public void notRegisteredWhenNoProviderIsChosen() {
        configure(true, "");
        Assert.assertFalse(shouldRegister());
    }

    @Test
    public void notRegisteredWhileTheServiceIsTearingDown() {
        configure(true, ADDRESS);
        Assert.assertFalse(DeviceReceiversManager.shouldRegisterSleepAsAndroidReceiver(prefs, false));
    }

    @Test
    public void reactsToPreferenceChanges() {
        configure(true, ADDRESS);
        Assert.assertTrue(shouldRegister());

        configure(false, ADDRESS);
        Assert.assertFalse(shouldRegister());

        configure(true, ADDRESS);
        Assert.assertTrue(shouldRegister());
    }
}
