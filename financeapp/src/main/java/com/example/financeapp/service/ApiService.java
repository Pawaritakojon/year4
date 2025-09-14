package com.example.financeapp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ApiService {

    private final String exchangeApiKey = "af67262a26c9af53d2b9aba5";

    public Map<String, Object> getExchangeRates() {
        String apiUrl = "https://v6.exchangerate-api.com/v6/" + exchangeApiKey + "/latest/THB";

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response = restTemplate.getForObject(apiUrl, Map.class);

        if (response == null || !"success".equals(response.get("result"))) {
            throw new RuntimeException("Failed to fetch exchange rates");
        }

        Map<String, Object> rates = (Map<String, Object>) response.get("conversion_rates");

        Map<String, Object> selectedRates = new HashMap<>();
        selectedRates.put("THB", 1);
        selectedRates.put("USD", rates.get("USD"));
        selectedRates.put("EUR", rates.get("EUR"));
        selectedRates.put("JPY", rates.get("JPY"));

        return selectedRates;
    }
}
