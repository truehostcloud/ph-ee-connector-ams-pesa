package org.mifos.connector.ams.pesacore.pesacore.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * {
 *     "globalClientId": "abcd-1234",
 *     "accountNumber": "123456",
 *     "firstName": "John",
 *     "lastName": "Doe",
 *     "countryId": 1,
 *     "accountBalance": 0.0,
 *     "banned": false,
 *     "active": true
 * }
 */
public class RosterGetClientResponseDTO {

    @JsonProperty("globalClientId")
    private String globalClientId;

    @JsonProperty("accountNumber")
    private String accountNumber;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("countryId")
    private int countryId;

    @JsonProperty("banned")
    private Boolean banned;

    @JsonProperty("active")
    private Boolean active;

    public String getGlobalClientId() {
        return globalClientId;
    }

    public void setGlobalClientId(String globalClientId) {
        this.globalClientId = globalClientId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getCountryId() {
        return countryId;
    }

    public void setCountryId(int countryId) {
        this.countryId = countryId;
    }

    public Boolean getBanned() {
        return banned;
    }

    public void setBanned(Boolean banned) {
        this.banned = banned;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override public String toString() {
        return "RosterGetClientResponseDTO{" + "globalClientId='" + globalClientId + '\'' + ", accountNumber='" + accountNumber + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", countryId=" + countryId + ", banned=" + banned + ", active=" + active + '}';
    }

    /**
     * Converts the {@link RosterGetClientResponseDTO} to a list of {@link CustomData}.
     *
     * @param clientResponseDTO
     *            the response from the Roster API
     * @return a list of {@link CustomData}
     */
    public static List<CustomData> convertToCustomData(RosterGetClientResponseDTO clientResponseDTO) {

        List<CustomData> customDataList = new ArrayList<>();

        customDataList.add(new CustomData("globalClientId", clientResponseDTO.getGlobalClientId()));
        customDataList.add(new CustomData("clientAccountNumber", clientResponseDTO.getAccountNumber()));
        customDataList.add(new CustomData("clientFirstname", clientResponseDTO.getFirstName()));
        customDataList.add(new CustomData("clientLastname", clientResponseDTO.getLastName()));
        customDataList.add(new CustomData("banned", clientResponseDTO.getBanned()));
        customDataList
                .add(new CustomData("active", clientResponseDTO.getActive()));
        customDataList.add(new CustomData("countryId", clientResponseDTO.getCountryId()));

        return customDataList;
    }
}
