package app.superhealth.engine.service.devices.xiaomi;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.model.NotificationSpec;
import app.superhealth.engine.proto.xiaomi.Notification3;
import app.superhealth.engine.proto.xiaomi.XiaomiProto;
import app.superhealth.engine.service.devices.xiaomi.services.XiaomiNotificationService;
import app.superhealth.engine.test.GBTestApplication;

/**
 * Message privacy modes leave sender, title and body null. The notification must still carry
 * title and body, as empty strings.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26, application = GBTestApplication.class)
public class XiaomiNotificationSendTest {

    private XiaomiSupport support;
    private XiaomiNotificationService notificationService;

    @Before
    public void setUp() {
        support = Mockito.mock(XiaomiSupport.class);
        Mockito.when(support.getDevice()).thenReturn(
                new GBDevice("00:11:22:33:44:55", "Testie", "Testie", "Test Folder", DeviceType.TEST));
        notificationService = new XiaomiNotificationService(support);
    }

    private Notification3 send(final NotificationSpec spec) {
        notificationService.onNotification(spec);

        final ArgumentCaptor<XiaomiProto.Command> captor = ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support).sendCommand(Mockito.anyString(), captor.capture());
        return captor.getValue().getNotification().getNotification2().getNotification3();
    }

    private static NotificationSpec spec(final String title, final String body) {
        final NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("com.example.chat");
        spec.setSourceName("Chat");
        spec.setTitle(title);
        spec.setBody(body);
        return spec;
    }

    @Test
    public void sendsEmptyTitleAndBodyWhenAllContentHidden() {
        final Notification3 notification = send(spec(null, null));

        Assert.assertTrue(notification.hasTitle());
        Assert.assertEquals("", notification.getTitle());
        Assert.assertTrue(notification.hasBody());
        Assert.assertEquals("", notification.getBody());
    }

    @Test
    public void sendsEmptyBodyWhenOnlyBodyHidden() {
        final Notification3 notification = send(spec("Alice", null));

        Assert.assertEquals("Alice", notification.getTitle());
        Assert.assertTrue(notification.hasBody());
        Assert.assertEquals("", notification.getBody());
    }

    @Test
    public void sendsTitleAndBody() {
        final Notification3 notification = send(spec("Alice", "Hello"));

        Assert.assertEquals("Alice", notification.getTitle());
        Assert.assertEquals("Hello", notification.getBody());
    }
}
