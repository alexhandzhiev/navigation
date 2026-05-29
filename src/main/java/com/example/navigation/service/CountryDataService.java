package com.example.navigation.service;

import com.example.navigation.model.Country;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CountryDataService {

    private static final Logger log = LoggerFactory.getLogger(CountryDataService.class);
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HTTP_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Country>> COUNTRY_LIST = new TypeReference<>() {};

    private final Map<String, Set<String>> adjacency;

    public CountryDataService(@Value("${navigation.countries.source}") Resource source) {
        this.adjacency = buildAdjacency(source);
        log.info("Loaded {} countries into adjacency graph from {}", adjacency.size(), source);
    }

    // Symmetrise: the upstream has at least one one-way declaration (LKA
    // lists IND, IND does not list LKA). Adding both directions keeps the
    // graph undirected regardless of how dirty the input is.
    private static Map<String, Set<String>> buildAdjacency(Resource source) {
        List<Country> countries = readCountries(source);
        Map<String, Set<String>> mutable = HashMap.newHashMap(countries.size());

        for (Country c : countries) {
            if (c.cca3() != null) {
                mutable.put(c.cca3(), new HashSet<>());
            }
        }

        for (Country c : countries) {
            String code = c.cca3();
            if (code == null) {
                continue;
            }
            Set<String> codeNeighbours = mutable.get(code);
            for (String border : c.borders()) {
                Set<String> borderNeighbours = mutable.get(border);
                if (borderNeighbours == null) {
                    continue;
                }
                codeNeighbours.add(border);
                borderNeighbours.add(code);
            }
        }

        mutable.replaceAll((k, v) -> Set.copyOf(v));
        return Map.copyOf(mutable);
    }

    private static List<Country> readCountries(Resource source) {
        try (InputStream in = openStream(source)) {
            return MAPPER.readValue(in, COUNTRY_LIST);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load country data from " + source, e);
        }
    }

    // Route http(s) through HttpClient with timeouts
    private static InputStream openStream(Resource source) throws IOException {
        URL url;
        try {
            url = source.getURL();
        } catch (IOException e) {
            return source.getInputStream();
        }
        String protocol = url.getProtocol();
        return ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                ? openHttp(url)
                : source.getInputStream();
    }

    private static InputStream openHttp(URL url) throws IOException {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(HTTP_CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(url.toURI())
                    .timeout(HTTP_READ_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                try (InputStream body = response.body()) {
                    throw new IOException("Unexpected HTTP " + status + " from " + url);
                }
            }
            return response.body();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    public boolean contains(String cca3) {
        return adjacency.containsKey(cca3);
    }

    public Set<String> bordersOf(String cca3) {
        return adjacency.getOrDefault(cca3, Set.of());
    }
}
