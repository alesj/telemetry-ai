package io.quarkiverse.telemetry.ext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
@Path("/weather")
public class WeatherResource {
    private static final Logger log = Logger.getLogger(WeatherResource.class);

    @RestClient
    WeatherClient weatherClient;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response forecast(@QueryParam("city") String city,
            @QueryParam("days") Integer days) {
        if (city == null) {
            city = "london";
        }
        if (days == null || days < 1) {
            days = 3;
        }

        double[] coords = cityCoords(city);
        log.infof("Weather forecast: city=%s, lat=%.2f, lon=%.2f, days=%d", city, coords[0], coords[1], days);

        try {
            String result = weatherClient.forecast(
                    coords[0], coords[1],
                    "temperature_2m_max,temperature_2m_min,precipitation_sum,weathercode",
                    days,
                    "auto");
            return Response.ok(result).build();
        } catch (Exception e) {
            log.errorf("Weather API call failed: %s", e.getMessage());
            return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    private double[] cityCoords(String city) {
        return switch (city.toLowerCase()) {
            case "london" -> new double[] { 51.51, -0.13 };
            case "paris" -> new double[] { 48.86, 2.35 };
            case "new york", "nyc" -> new double[] { 40.71, -74.01 };
            case "tokyo" -> new double[] { 35.68, 139.69 };
            case "berlin" -> new double[] { 52.52, 13.41 };
            case "sydney" -> new double[] { -33.87, 151.21 };
            default -> new double[] { 51.51, -0.13 };
        };
    }
}
