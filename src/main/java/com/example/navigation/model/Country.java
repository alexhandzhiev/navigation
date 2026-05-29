package com.example.navigation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Country(String cca3, List<String> borders) {

    public Country {
        borders = borders == null ? List.of() : List.copyOf(borders);
    }
}
