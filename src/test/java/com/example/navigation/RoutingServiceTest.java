package com.example.navigation;

import com.example.navigation.exception.NoRouteException;
import com.example.navigation.service.CountryDataService;
import com.example.navigation.service.RoutingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private static RoutingService routingService;

    @BeforeAll
    static void setUp() {
        CountryDataService data = new CountryDataService(new ClassPathResource("countries.json"));
        routingService = new RoutingService(data);
    }

    @Test
    void findsShortestRouteForBriefSample() {
        assertThat(routingService.findRoute("CZE", "ITA"))
                .containsExactly("CZE", "AUT", "ITA");
    }

    @Test
    void returnsSingleHopWhenOriginEqualsDestination() {
        assertThat(routingService.findRoute("DEU", "DEU"))
                .containsExactly("DEU");
    }

    @Test
    void islandNationHasNoLandRoute() {
        assertThatThrownBy(() -> routingService.findRoute("ISL", "DEU"))
                .isInstanceOf(NoRouteException.class);
    }

    @Test
    void unknownCountryRejected() {
        assertThatThrownBy(() -> routingService.findRoute("XXX", "DEU"))
                .isInstanceOf(NoRouteException.class);
    }

    @Test
    void acceptsLowercaseCca3Codes() {
        assertThat(routingService.findRoute("cze", "ita"))
                .startsWith("CZE").endsWith("ITA");
    }

    @Test
    void routingIsDirectionAgnostic() {
        // Regression: upstream declares LKA→IND one-way. Without graph
        // symmetrisation, LKA→IND returns a route but IND→LKA returns 400.
        List<String> forward = routingService.findRoute("LKA", "IND");
        List<String> reverse = routingService.findRoute("IND", "LKA");

        assertThat(reverse).containsExactlyElementsOf(forward.reversed());
    }
}
