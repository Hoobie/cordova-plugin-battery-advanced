package pl.edu.agh.mcc;

import org.apache.cordova.CallbackContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BatteryAdvancedTest {

    @Mock
    private CallbackContext callbackContext;
    @Spy
    private BatteryAdvanced batteryAdvanced = new BatteryAdvanced();

    @Before
    public void setUp() throws Exception {
        doReturn(100.0).when(batteryAdvanced).getAveragePower(anyString());
        doReturn(null).when(batteryAdvanced).readCpuInfo();
    }

    @Test
    public void shouldStartMeasurements() throws Exception {
        // when
        batteryAdvanced.startMeasurements(callbackContext);

        // then
        verify(callbackContext).success();
    }
}