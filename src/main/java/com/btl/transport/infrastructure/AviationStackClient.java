package com.btl.transport.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class AviationStackClient {

    private final WebClient webClient;
    private final String apiKey;

    public AviationStackClient(WebClient aviationStackWebClient,
                               @Value("${aviationstack.api-key}") String apiKey) {
        this.webClient = aviationStackWebClient;
        this.apiKey = apiKey;
    }

    /**
     * @param airportCode the program's airport (IATA); arrivals are matched on arrival airport and
     *                    departures on departure airport. Null skips the airport filter.
     */
    public Optional<AviationStackFlightData> fetchFlight(String flightIata, String airportCode, boolean arrival) {
        try {
            AviationStackResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1/flights")
                    .queryParam("access_key", apiKey)
                    .queryParam("flight_iata", flightIata)
                    .queryParamIfPresent(arrival ? "arr_iata" : "dep_iata", Optional.ofNullable(airportCode))
                    .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    log.warn("AviationStack 4xx for flight {}: {}", flightIata, clientResponse.statusCode());
                    return clientResponse.createException();
                })
                .bodyToMono(AviationStackResponse.class)
                .block();

            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.data().get(0));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                log.warn("AviationStack rate limited — skipping poll cycle for {}", flightIata);
            } else {
                log.error("AviationStack error for flight {}: {}", flightIata, e.getMessage());
            }
            return Optional.empty();
        }
    }

    public record AviationStackResponse(List<AviationStackFlightData> data) {}

    public record AviationStackFlightData(
        AviationStackFlight flight,
        AviationStackArrival arrival,
        AviationStackDeparture departure,
        String flight_status
    ) {}

    public record AviationStackFlight(String iata, String number) {}

    public record AviationStackArrival(
        String airport, String iata,
        String scheduled, String estimated, String actual
    ) {}

    public record AviationStackDeparture(
        String airport, String iata, String scheduled
    ) {}
}
