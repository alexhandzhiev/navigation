package com.example.navigation.service;

import com.example.navigation.exception.NoRouteException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RoutingService {

    private final CountryDataService countryData;

    public RoutingService(CountryDataService countryData) {
        this.countryData = countryData;
    }

    /** Shortest route by number of border crossings, inclusive of origin and destination. */
    public List<String> findRoute(String origin, String destination) {
        String from = normalize(origin);
        String to = normalize(destination);

        if (!countryData.contains(from)) {
            throw new NoRouteException("Unknown origin country: " + origin);
        }
        if (!countryData.contains(to)) {
            throw new NoRouteException("Unknown destination country: " + destination);
        }
        if (from.equals(to)) {
            return List.of(from);
        }

        Map<String, String> predecessor = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbour : countryData.bordersOf(current)) {
                if (!visited.add(neighbour)) {
                    continue;
                }
                predecessor.put(neighbour, current);
                if (neighbour.equals(to)) {
                    return reconstruct(predecessor, to);
                }
                queue.add(neighbour);
            }
        }

        throw new NoRouteException("No land route from " + from + " to " + to);
    }

    private List<String> reconstruct(Map<String, String> predecessor, String to) {
        List<String> path = new ArrayList<>();
        for (String node = to; node != null; node = predecessor.get(node)) {
            path.add(node);
        }
        Collections.reverse(path);
        return path;
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
