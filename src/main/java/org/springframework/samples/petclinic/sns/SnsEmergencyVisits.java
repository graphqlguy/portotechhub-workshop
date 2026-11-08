package org.springframework.samples.petclinic.sns;

import java.net.URI;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.samples.petclinic.upstream.InvalidArgumentException;
import org.springframework.samples.petclinic.upstream.UpstreamGet;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The visits to the emergency departments of public hospitals in one month, from the
 * dataset <code>atendimentos-em-urgencia-triagem-manchester</code> of the SNS's
 * transparency portal. The dataset adds the visits up from January, so each month holds
 * the visits since 1 January. The visits of one month are that month's values minus the
 * month before, and January's are its own.
 */
public class SnsEmergencyVisits {

	private static final String DATASET = "atendimentos-em-urgencia-triagem-manchester";

	private static final String PREFIX = "no_de_atendimentos_em_urgencia_su_triagem_manchester_";

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final JsonMapper mapper;

	public SnsEmergencyVisits(String upstream, String baseUrl, RestClient client, JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.mapper = mapper;
	}

	/**
	 * The visits of each hospital in a month, the hospital with the most visits first.
	 * @param month the month, such as 2026-08
	 * @param region the health region or words of its name, such as Norte for Região de
	 * Saúde Norte, or null for every region
	 */
	public List<SnsHospitalEmergencyVisits> inMonth(String month, String region) {
		YearMonth wanted = parse(month);
		Object answer = UpstreamGet.json(upstream, client, mapper, uri(month, region),
				(json) -> (json instanceof Map<?, ?> map) ? map.get("total_count") + " records" : "an answer");
		Map<String, Map<String, Object>> current = new LinkedHashMap<>();
		Map<String, Map<String, Object>> previous = new LinkedHashMap<>();
		if (answer instanceof Map<?, ?> map && map.get("results") instanceof List<?> results) {
			for (Object result : results) {
				@SuppressWarnings("unchecked")
				Map<String, Object> row = (Map<String, Object>) result;
				String hospital = String.valueOf(row.get("instituicao"));
				(wanted.toString().equals(row.get("tempo")) ? current : previous).put(hospital, row);
			}
		}
		boolean january = wanted.getMonthValue() == 1;
		return current.entrySet()
			.stream()
			.map((entry) -> visits(entry.getValue(), january ? null : previous.get(entry.getKey()), month))
			.sorted(Comparator.comparingInt(SnsHospitalEmergencyVisits::total).reversed())
			.toList();
	}

	URI uri(String month, String region) {
		YearMonth wanted = parse(month);
		YearMonth from = (wanted.getMonthValue() == 1) ? wanted : wanted.minusMonths(1);
		String where = "tempo >= date'%s' and tempo <= date'%s'".formatted(from, wanted)
				+ ((region != null && !region.isBlank())
						? " and search(regiao, \"%s\")".formatted(region.replace("\"", "")) : "");
		return UriComponentsBuilder.fromUriString(baseUrl)
			.path("/api/explore/v2.1/catalog/datasets/" + DATASET + "/records")
			.queryParam("where", where)
			.queryParam("limit", 100)
			.encode()
			.build()
			.toUri();
	}

	private static SnsHospitalEmergencyVisits visits(Map<String, Object> row, Map<String, Object> before,
			String month) {
		int red = count(row, before, PREFIX + "vermelha");
		int orange = count(row, before, PREFIX + "laranja");
		int yellow = count(row, before, PREFIX + "amarela");
		int green = count(row, before, PREFIX + "verde");
		int blue = count(row, before, PREFIX + "azul");
		int white = count(row, before, PREFIX + "branca");
		int notTriaged = count(row, before, "no_de_atendimentos_em_sem_triagem_manchester");
		return new SnsHospitalEmergencyVisits(String.valueOf(row.get("instituicao")), String.valueOf(row.get("regiao")),
				month, red + orange + yellow + green + blue + white + notTriaged, red, orange, yellow, green, blue,
				white, notTriaged);
	}

	private static int count(Map<String, Object> row, Map<String, Object> before, String column) {
		int now = (row.get(column) instanceof Number number) ? number.intValue() : 0;
		int earlier = (before != null && before.get(column) instanceof Number number) ? number.intValue() : 0;
		return now - earlier;
	}

	private static YearMonth parse(String month) {
		try {
			return YearMonth.parse(month);
		}
		catch (RuntimeException ex) {
			throw new InvalidArgumentException("Write the month as year and month, such as 2026-08.");
		}
	}

}
