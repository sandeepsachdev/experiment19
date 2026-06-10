package com.example.freetotv.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountriesTest {

    private final Countries countries = new Countries();

    @Test
    void resolvesByName() {
        assertThat(countries.resolveCode("Australia", "ZZ")).isEqualTo("AU");
        assertThat(countries.resolveCode("united kingdom", "ZZ")).isEqualTo("GB");
    }

    @Test
    void resolvesByCode() {
        assertThat(countries.resolveCode("au", "ZZ")).isEqualTo("AU");
        assertThat(countries.resolveCode("US", "ZZ")).isEqualTo("US");
    }

    @Test
    void fallsBackWhenBlankOrUnknown() {
        assertThat(countries.resolveCode(null, "AU")).isEqualTo("AU");
        assertThat(countries.resolveCode("", "AU")).isEqualTo("AU");
        assertThat(countries.resolveCode("Atlantis", "AU")).isEqualTo("AU");
    }

    @Test
    void exposesNameForCode() {
        assertThat(countries.nameFor("AU")).isEqualTo("Australia");
        assertThat(countries.nameFor("ZZ")).isEqualTo("ZZ");
        assertThat(countries.all()).first().extracting(Countries.Country::name).isEqualTo("Australia");
    }
}
