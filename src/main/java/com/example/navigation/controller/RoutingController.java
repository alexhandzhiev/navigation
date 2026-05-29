package com.example.navigation.controller;

import com.example.navigation.model.RouteResponse;
import com.example.navigation.service.RoutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/routing/{origin}/{destination}")
    public RouteResponse route(@PathVariable String origin, @PathVariable String destination) {
        List<String> route = routingService.findRoute(origin, destination);
        return new RouteResponse(route);
    }
}
