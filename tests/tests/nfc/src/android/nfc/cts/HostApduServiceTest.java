package android.nfc.cts;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HostApduServiceTest {
  private CtsMyHostApduService service;

  @Before
  public void setUp() {
    if (Looper.myLooper() == null) {
      Looper.prepare();
    }
    service = new CtsMyHostApduService();
  }

  @Test
  public void testOnBind() {
    Intent serviceIntent
          = new Intent(CtsMyHostApduService.SERVICE_INTERFACE);
    Assert.assertNotNull(service.onBind(serviceIntent));
  }

  @Test
  public void testSendResponseApdu() {
    try {
      byte[] responseApdu = new byte[0];
      service.sendResponseApdu(responseApdu);
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected Exception: " + e);
    }
  }

  @Test
  public void testNotifyUnhandled() {
    try {
      service.ctsNotifyUnhandled();
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected Exception: " + e);
    }
  }

  @Test
  public void testProcessCommandApdu() {
    byte[] result = service.processCommandApdu(new byte[0], new Bundle());
    Assert.assertNotNull(result);
    Assert.assertTrue(result.length == 0);
  }

  @Test
  public void testOnDeactivated() {
    try {
      service.onDeactivated(CtsMyHostApduService.DEACTIVATION_LINK_LOSS);
      service.onDeactivated(CtsMyHostApduService.DEACTIVATION_DESELECTED);
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected Exception: " + e);
    }
  }
}
