package org.mifos.connector.ams.pesacore.camel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "pesacore")
public class PesaCoreProperties {

    private List<Country> countries;


    public Country getCountryByCurrency(String currency){
        if(CollectionUtils.isEmpty(countries)){
           throw new RuntimeException("No country is configured");
        }
        return countries.stream().filter(country -> StringUtils.hasText(country.getCurrency()) && country.getCurrency().equalsIgnoreCase(currency))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Country with currency: " + currency + " is not configured"));
    }

    public class Country{

        private String countryId;
        private String currency;

        public Country() {
        }

        public String getCountryId() {
            return countryId;
        }

        public void setCountryId(String countryId) {
            this.countryId = countryId;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}


