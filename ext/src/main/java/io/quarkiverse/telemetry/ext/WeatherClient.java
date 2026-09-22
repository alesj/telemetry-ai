package io.quarkiverse.telemetry.ext;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "weather-api")
@ClientHeaderParam(name = "Host", value = "api.open-meteo.com")
@Path("/v1")
public interface WeatherClient {

    @GET
    @Path("/forecast")
    @Produces(MediaType.APPLICATION_JSON)
    String forecast(
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("daily") String daily,
            @QueryParam("forecast_days") int forecastDays,
            @QueryParam("timezone") String timezone);
}
