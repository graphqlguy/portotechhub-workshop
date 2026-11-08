package org.springframework.samples.petclinic.dgeg;

/**
 * The price of a fuel at a filling station, as the station reported it to DGEG.
 *
 * @param station the name of the station, such as PLENERGY - VILA NOVA DE GAIA III
 * @param brand the brand, such as PLENERGY
 * @param municipality the municipality
 * @param address the street and number
 * @param fuel the fuel, in DGEG's words, such as Gasolina simples 95
 * @param priceEurosPerLitre the price, in euros per litre
 * @param updatedAt when the station last changed its price, in Lisbon time, such as
 * 2026-11-03 17:05
 * @param distanceKilometres the distance from the place the query gave, or null without a
 * place
 * @param source who publishes the price, which DGEG asks every reuser to name
 */
public record DgegFuelPrice(String station, String brand, String municipality, String address, String fuel,
		Double priceEurosPerLitre, String updatedAt, Double distanceKilometres, String source) {
}
