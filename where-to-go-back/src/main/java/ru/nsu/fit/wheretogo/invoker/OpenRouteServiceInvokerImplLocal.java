package ru.nsu.fit.wheretogo.invoker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.nsu.fit.wheretogo.dto.route.ORSDirectionsDto;
import ru.nsu.fit.wheretogo.dto.route.model.DirectionRequest;
import ru.nsu.fit.wheretogo.dto.route.model.HealthCheck;

import java.util.List;

@Profile("dev")
@Component
public class OpenRouteServiceInvokerImplLocal implements OpenRouteServiceInvoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouteServiceInvokerImplLocal.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String ORS_BASE_URL = "http://localhost:8080";
    private static final String HEALTH_CHECK = "/ors/v2/health";
    private static final String NOT_READY_STR = "not ready";
    private static final String DIRECTION_DRIVING_CAR_URL = "/ors/v2/directions/driving-car/geojson";
    private static final String DIRECTION_WALKING_URL = "/ors/v2/directions/foot-walking/geojson";

    @Override
    public String health() {
        var url = ORS_BASE_URL + HEALTH_CHECK;

        LOGGER.debug("Check ORS health");
        var result = restTemplate.exchange(url, HttpMethod.GET, null, HealthCheck.class);

        if (result.getBody() != null) {
            LOGGER.debug("ORS health:{}", result.getBody().getStatus());
            return result.getBody().getStatus();
        }

        return NOT_READY_STR;
    }

    @Override
    public ORSDirectionsDto getDirectionDriving() {
        var url = ORS_BASE_URL + DIRECTION_DRIVING_CAR_URL;
        var request = DirectionRequest
                .builder()
                .coordinates(List.of(List.of("83.62234", "54.56074"), List.of("82.71999","54.97187")))
                .build();

        var result = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request),
                ORSDirectionsDto.class);

        if (result.getStatusCode().equals(HttpStatus.OK) && result.getBody() != null) {
            return result.getBody();
        }

        return null;
    }

    @Override
    public ORSDirectionsDto getDirectionWalking() {
        var url = ORS_BASE_URL + DIRECTION_WALKING_URL;


        return null;
    }

}
