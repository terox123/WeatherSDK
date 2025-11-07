import org.junit.jupiter.api.Test;
import org.weather.Mode;
import org.weather.OpenWeatherSdk;
import org.weather.SdkFactory;
import org.weather.WeatherException;

import static org.junit.jupiter.api.Assertions.*;

public class SdkFactoryTest {

    @Test
    void testCreate_ValidKey_ReturnsInstance() throws WeatherException {
        OpenWeatherSdk sdk1 = SdkFactory.create("unique-key", Mode.ON_DEPEND, 300);
        OpenWeatherSdk sdk2 = SdkFactory.create("unique-key", Mode.ON_DEPEND, 300);
        assertSame(sdk1, sdk2); // Same instance
        sdk1.delete();
    }

    @Test
    void testCreate_InvalidKey_ThrowsException() {
        assertThrows(WeatherException.class, () -> SdkFactory.create("", Mode.ON_DEPEND, 300));
        assertThrows(WeatherException.class, () -> SdkFactory.create(null, Mode.ON_DEPEND, 300));
    }

    @Test
    void testRemove_ClearsInstance() throws WeatherException {
        OpenWeatherSdk sdk = SdkFactory.create("remove-key", Mode.ON_DEPEND, 300);
        sdk.delete();
        OpenWeatherSdk newSdk = SdkFactory.create("remove-key", Mode.ON_DEPEND, 300);
        assertNotSame(sdk, newSdk); // New instance
        newSdk.delete();
    }
}