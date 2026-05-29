package com.example.navigation;

import com.example.navigation.model.ErrorResponse;
import com.example.navigation.model.RouteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

// Uses RANDOM_PORT (real Tomcat) rather than MockMvc because the /error
// forward path is part of the contract being asserted; MockMvc skips it.
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "navigation.countries.source=classpath:countries.json"
)
class RoutingControllerTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void briefSampleReturnsExpectedRoute() {
        ResponseEntity<RouteResponse> response = http.getForEntity("/routing/CZE/ITA", RouteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().route()).containsExactly("CZE", "AUT", "ITA");
    }

    @Test
    void caseInsensitiveInputIsAccepted() {
        ResponseEntity<RouteResponse> response = http.getForEntity("/routing/cze/ita", RouteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().route()).startsWith("CZE").endsWith("ITA");
    }

    @Test
    void disconnectedCountriesReturn400WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/routing/USA/AUS", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NO_ROUTE", "No land route from USA to AUS"));
    }

    @Test
    void islandNationReturns400WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/routing/ISL/DEU", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NO_ROUTE", "No land route from ISL to DEU"));
    }

    @Test
    void unknownOriginReturns400WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/routing/XXX/DEU", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NO_ROUTE", "Unknown origin country: XXX"));
    }

    @Test
    void unknownDestinationReturns400WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/routing/DEU/XXX", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NO_ROUTE", "Unknown destination country: XXX"));
    }

    @Test
    void wrongArityReturns404WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.getForEntity("/routing/CZE", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
    }

    @Test
    void wrongMethodReturns405WithJsonBody() {
        ResponseEntity<ErrorResponse> response = http.postForEntity("/routing/CZE/ITA", null, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unacceptableMediaTypeReturns406() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> response = http.exchange(
                "/routing/CZE/ITA", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }
}
