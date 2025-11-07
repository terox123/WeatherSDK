package org.weather;

import java.util.concurrent.ConcurrentHashMap;

public class SdkFactory {

    private static final ConcurrentHashMap<String, OpenWeatherSdk> instances = new ConcurrentHashMap<>();

    public static OpenWeatherSdk create(String key, Mode mode, int pollingTime) throws WeatherException {
        if(key == null || key.isBlank()){
            throw new WeatherException("API key is empty");
        }
        return instances.computeIfAbsent(key, k ->{
                try{
                    return new OpenWeatherSdk(k, mode, pollingTime);
                }catch (Exception ex){
                    throw new RuntimeException(ex);
                }
        });


    }

    static void remove(String key){
        instances.remove(key);
    }

}
