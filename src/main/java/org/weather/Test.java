package org.weather;

public class Test {
    public static void main(String[] args) throws WeatherException {
        OpenWeatherSdk openWeatherSdk = SdkFactory.create("d0cf25428e05dfbd1edef898a3e89ee0",
                Mode.ON_DEPEND, 100);


        String json = openWeatherSdk.getWeatherByCity("Москва");
        System.out.println(json);
        openWeatherSdk.delete();

    }
}
