package org.springframework.samples.petclinic.dgeg;

/**
 * The fuels whose prices DGEG publishes for every filling station, with DGEG's id of
 * each.
 */
public enum DgegFuel {

	/**
	 * Gasolina simples 95: the usual petrol.
	 */
	PETROL_95(3201),

	/**
	 * Gasolina especial 95: the premium petrol.
	 */
	PETROL_95_PREMIUM(3205),

	/**
	 * Gasóleo simples: the usual diesel.
	 */
	DIESEL(2101),

	/**
	 * Gasóleo especial: the premium diesel.
	 */
	DIESEL_PREMIUM(2105);

	private final int id;

	DgegFuel(int id) {
		this.id = id;
	}

	int id() {
		return this.id;
	}

}
