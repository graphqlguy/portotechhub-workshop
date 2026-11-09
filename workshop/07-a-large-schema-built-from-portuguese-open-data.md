# Chapter 7: A large schema built from Portuguese open data

> **User story** · As a vet in Porto, I want to ask the assistant about the weather, the beaches, prices and pets in Portugal, so that the answers come from official sources while I stay in the clinic's application.

The schema grows from the clinic's 6 root fields to 523, read from nine public services of Portugal, Porto and the European Union. Its 619,000 characters come to about 158,000 tokens, about 19 times the 8,192 tokens that the model on your laptop reads at a time. The code is ready on a branch: you switch to it, and ask the assistant questions that it answers with the three tools of chapter 6.

By the end of this chapter, you will:

- Run the application against nine public services, live or from recorded answers
- Ask questions that the three tools answer from a schema of 158,000 tokens
- Know why everything a field needs goes in its description

## 1. Switch to the checkpoint

Stop the backend. Commit your work of chapter 6, or set it aside with `git stash`, then fetch the branch of this chapter and switch to it:

```bash
git add -A
git commit -m "My chapter 6"
git fetch origin
git switch checkpoint-07-large-schema
```

Start the backend:

```bash
./mvnw spring-boot:run
```

At the first start, GATool embeds the new schema with the embedding model of chapter 6:

```text
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool is embedding 3529 schema coordinates, which is one call to your embedding model for each of them. The result is cached, so later starts read it.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 3529 schema coordinates with EMBEDDING and returning up to 2000 tokens of results per call.
```

The embedding took 40 seconds on my laptop (MacBook M5), and 3 minutes on an older MacBook M1. Later starts read the vectors from the cache. Every new field calls a service on the internet. On a slow or missing connection, start with the recorded answers of step 3.

## 2. What we integrated with

Each service has a schema file of its own in `src/main/resources/graphql`:

| File | What it reads | Who runs it | Root fields |
|---|---|---|---|
| `apa.graphqls` | Beaches and bathing water, rivers, floods, the coast, protected areas, air and noise | APA, the Portuguese Environment Agency | 178 |
| `sns.graphqls`, `sns-emergency.graphqls` | Hospitals, emergency departments, consultations, waiting times and health spending | SNS, the National Health Service | 149 |
| `ine.graphqls` | Official statistics: population, businesses by activity, prices, crime, trade and more | INE, Statistics Portugal | 88 |
| `turismo.graphqls` | The local lodging register, hotels, tourism businesses and routes | Turismo de Portugal | 45 |
| `eurostat.graphqls` | Prices, population, unemployment and business statistics of every EU country | Eurostat | 21 |
| `porto.graphqls`, `porto-places-and-buses.graphqls` | Porto's air quality, noise, weather, buses, parking and points of interest | Porto Digital | 20 |
| `ipma.graphqls` | Forecasts, warnings, earthquakes and weather observations | IPMA, the Portuguese Institute for Sea and Atmosphere | 14 |
| `dgeg.graphqls` | The fuel prices of every filling station | DGEG, the Directorate-General for Energy and Geology | 1 |
| `ropi.graphqls` | The parks and gardens of Porto | Porto Digital (ROPI) | 1 |

Together they add 517 root fields to the clinic's 6. I counted about 158,000 tokens with the `cl100k_base` encoding of [tiktoken](https://github.com/openai/tiktoken), about 19 times the 8,192 tokens that chapter 5 gave `qwen3:8b`, and one `searchSchema` call returns at most 2,000 of them. Every service answers without a key. Most publish their API with documentation; DGEG's fuel prices come from the API behind its own portal, which its terms allow us to reuse if we name DGEG and give the time of each price. `src/main/resources/mock-upstreams/SOURCES.md` lists the terms of each service.

## 3. Without wifi

Stop the backend and start it with the `mock` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

```text
INFO ... o.s.s.p.u.MockUpstreamConfiguration      : External services are mocked with recorded responses on port 9091
```

A [WireMock](https://wiremock.org) server inside the application answers in place of the nine services, from 915 answers in `src/main/resources/mock-upstreams`. The answers that change with time were recorded on 5 November 2026 at 22:21 UTC, and the rest on 5 and 6 November. The profile also sets the application's clock to that moment, so "tomorrow" and "right now" ask for the recorded answers on any later day. A call with the arguments of a recording gets that recording, including the errors a service returns for arguments it rejects. With other arguments, most services answer with an empty result. IPMA, DGEG and Eurostat answer with a recording made for other arguments, so Lisbon's forecast answers with Porto's. INE answers a call for another period with a recording of the same indicator, place and category, and each value in it carries its own period, such as 2013. The questions of step 4 get the recorded answers.

The tests start the application with this profile, so they run without the internet.

## 4. Ask the assistant

The system prompt of chapter 6 names the clinic's data alone. In this chapter, it says where the clinic is and names the topics of the public services, so the model searches the schema before it decides that a question is outside a clinic's API. An MCP client's model reads the same topics in the server's instructions, `spring.ai.mcp.server.instructions` in `application.properties`.

Open the chat panel in the frontend, and ask the questions below. Each one shows the answer to expect, the request to the public service behind it, which you can send yourself with `curl` or a browser, and the part of the response that answers it. The answers come from the recordings; a live answer can differ, and the questions about today change every day. I asked each question five times with `qwen3:8b` under the `mock` profile, each time in a new conversation, and an answer took between 5 and 234 seconds. I counted an answer as right when it gave the expected values with their right period. The model called the right field in 81 of the 90 runs, and 21 of the 30 wrong answers came from runs that had called it: the note under each question says what went wrong.

### What is the weather forecast for Porto tomorrow?

**Expected answer:** light rain, with a 100% chance of rain, from 8 to 17 °C (the forecast for 6 November 2026). The answer changes every day.

```
GET https://api.ipma.pt/open-data/forecast/meteorology/cities/daily/1131200.json
```
```json
{"forecastDate": "2026-11-06", "tMin": "7.7", "tMax": "17.1", "precipitaProb": "100.0", "idWeatherType": 10, "predWindDir": "E", ...}
```

IPMA forecasts five days for each city, and 1131200 is Porto. Weather type 10 is "Light rain" in IPMA's list of types, which `ipmaWeatherTypes` reads.

**In my runs:** right in 2 of 5. In the other three, one answer called weather type 10 "heavy rain or showers" without asking `ipmaWeatherTypes`, which names it light rain; one gave a forecast of its own after four queries that failed; and one asked Porto's own weather forecast, found it empty, and stopped.

### How clean is the air in Porto right now?

**Expected answer:** the readings of the five stations that report, such as PM10 58 and NO₂ 87.7 micrograms per cubic metre at Pólo Asprela at 22:15 UTC. The answer changes every hour.

```
GET https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?type=AirQualityObserved&options=keyValues&q=dateObserved>2026-11-05T21:20:59Z
```
```json
{"name": "Pólo Asprela", "dateObserved": "2026-11-05T22:15:00.00Z", "pm10": 58, "pm25": 29, "no2": 87.7, "o3": 0.2, "co": 946}
```

Five of Porto Digital's 13 stations still report, so the query asks for the ones that reported in the last hour. The broker sends raw readings without a clean-or-polluted index, so the model judges "clean" itself.

**In my runs:** right in 5 of 5.

### Which park is closest to the Alfândega?

**Expected answer:** Parque das Virtudes, 257 m away, then Jardim de João Chagas, 487 m away.

```
GET https://api.ropi.porto.digital/CitySDK/pois?category=Parques%20e%20Jardins
```
```json
{"label": [{"lang": "pt-PT", "value": "Parque das Virtudes"}], "location": {"point": [{"Point": {"posList": "41.14359837 -8.61843109"}}]}, ...}
```

ROPI, Porto's register of points of interest, lists 35 parks and gardens. The application finds the Alfândega among Porto Digital's points of interest, at 41.14321, -8.62146, and computes the distances.

**In my runs:** right in 5 of 5.

### Which bus is the fastest right now?

**Expected answer:** bus 3553 of line 803, at 15.3 metres per second, which is 55.1 km/h. The answer changes every minute.

```
GET https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?type=Vehicle&options=keyValues&q=observationDateTime>2026-11-05T22:15:59Z;vehicleType==bus&orderBy=!speed&limit=3
```
```json
{"name": "STCP 803 3553", "annotations": ["stcp:route:803", ...], "speed": 15.3, "observationDateTime": "2026-11-05T22:25:05.00Z", ...}
```

The broker sorts the buses that reported in the last five minutes by speed itself. It does not say the unit of `speed`; comparing two positions of 137 buses a minute apart showed metres per second, so the field converts it to km/h.

**In my runs:** right in 4 of 5. In the other one, the model asked for the ten fastest buses and named line 204 at 38.9 km/h, from further down the list.

### Where is the cheapest 95 petrol in the Porto district?

**Expected answer:** 1.869 € a litre at PLENERGY - VILA NOVA DE GAIA III, updated on 3 November at 17:05, according to DGEG. The answer changes when a station changes its price.

```
GET https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos?idsTiposComb=3201&idDistrito=13&qtdPorPagina=1000&pagina=1
```
```json
{"Nome": "PLENERGY - VILA NOVA DE GAIA III", "Municipio": "Vila Nova de Gaia", "Preco": "1,869 €", "DataAtualizacao": "2026-11-03 17:05", "Latitude": 41.07227, "Longitude": -8.625206, ...}
```

3201 is DGEG's id of 95 petrol, and 13 the district of Porto, with 452 stations. Each price comes with the time the station last changed it, which DGEG's terms ask every reuser to give.

**In my runs:** right in 5 of 5.

### Where is the cheapest diesel within 3 km of the Alfândega?

**Expected answer:** 2.149 € a litre at AUCHAN Canidelo, 2.5 km away, according to DGEG.

```
GET https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos?idsTiposComb=2101&idDistrito=13&qtdPorPagina=1000&pagina=1
```
```json
{"Nome": "AUCHAN Canidelo", "Municipio": "Vila Nova de Gaia", "Preco": "2,149 €", "DataAtualizacao": "2026-11-04 08:55", ...}
```

The same request with 2101, DGEG's id of diesel. The application keeps the 22 stations within 3 km of the Alfândega and sorts them by price.

**In my runs:** right in 5 of 5.

### Which beaches in Porto have a blue flag?

**Expected answer:** 9 of Porto's 10 beaches, such as Homem do Leme, Ingleses and Gondarém. Castelo do Queijo is the one without.

```
GET https://sniambgeoogc.apambiente.pt/getogc/rest/services/Dashboard/Praias_dashboard/MapServer/0/query?where=concelho='PORTO'&outFields=nome_praia,bandeira_azul,qualidade_agua_balnear_dsc&returnGeometry=false&f=json
```
```json
{"nome_praia": "Castelo do Queijo", "concelho": "PORTO", "bandeira_azul": 0, "qualidade_agua_balnear_dsc": "Água adequada a banhos"}
```

APA lists the 761 bathing waters of Portugal, and writes the municipality in capitals. All 10 of Porto's have water fit for bathing.

**In my runs:** right in 5 of 5.

### How many people live in Porto, and how has that changed in ten years?

**Expected answer:** 267,236 people in 2023, against 234,453 in 2013, which is 14% more.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0008273&Dim1=S7A2023&Dim2=11A1312&Dim3=T&Dim4=T&lang=EN
```
```json
{"2023": [{"geocod": "11A1312", "geodsg": "Porto", "ind_string": "267 236", "valor": "267236"}]}
```

One request returns one year, so the model asks twice, with `S7A2023` and `S7A2013`, and compares the two. 11A1312 is INE's code of the municipality of Porto, which `inePlaces` finds by name.

**In my runs:** right in 1 of 5. In the other four, two answers compared 2011 with 2023 and called it ten years; one sent Porto's municipal code to `inePopulationByRegion`, whose places are regions, and stopped at the error; and one came after 234 seconds, with advice on GraphQL aliases.

### Which hospital in the Norte region had the most emergency visits in August?

**Expected answer:** Unidade Local de Saúde de São João, with 21,063 visits, then Trás-os-Montes e Alto Douro, with 20,661.

```
GET https://transparencia.sns.gov.pt/api/explore/v2.1/catalog/datasets/atendimentos-em-urgencia-triagem-manchester/records?where=regiao="Região de Saúde Norte" and tempo>=date'2026-07'
```
```json
{"tempo": "2026-08", "instituicao": "Unidade Local de Saúde de São João, EPE", "no_de_atendimentos_em_urgencia_su_triagem_manchester_vermelha": 801, ...}
```

The dataset says "por mês" (per month), but its counts add up from January: São João's 168,830 visits in August are the visits since 1 January. August alone is 168,830 minus July's 147,767, and `snsEmergencyVisitsInMonth` does that subtraction for every hospital.

**In my runs:** right in 5 of 5.

### What was the strongest earthquake in Portugal in the last month, and did people feel it?

**Expected answer:** a magnitude 4.2 earthquake north-east of Bragança on 15 October 2026, which people felt in Vinhais.

```
GET https://api.ipma.pt/open-data/observation/seismic/7.json
```
```json
{"time": "2026-10-15T20:34:46", "magnitud": "4.2", "obsRegion": "NE Bragança", "depth": 16, "sensed": true, "local": "Vinhais (Bragança)"}
```

Area 7 is mainland Portugal and Madeira, and the file holds the last 30 days, 329 earthquakes. The Azores have their own file, area 3.

**In my runs:** right in 3 of 5. In the other two, one answer gave the 4.2 and that people felt it without saying where, and one stopped at the area argument without calling `introspectType`, which lists its two values.

### How many local lodging units are registered in Porto?

**Expected answer:** 9,942, out of 112,000 in Portugal.

```
GET https://geo.turismodeportugal.pt/server/rest/services/TDP/OpenData_AL/MapServer/6/query?where=Concelho='Porto'&returnCountOnly=true&f=json
```
```json
{"count": 9942}
```

The local lodging register (RNAL) of Turismo de Portugal is an ArcGIS layer, and the field asks the server for the count alone, without the 9,942 records.

**In my runs:** right in 5 of 5.

### How many pet clinics are there in Porto?

**Expected answer:** 105 pet clinics in 2024, run by 97 businesses.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0012934&Dim1=S7A2024&Dim2=11A1312&Dim3=75000&Dim4=T&lang=EN
```
```json
{"2024": [{"geocod": "11A1312", "geodsg": "Porto", "dim_3": "75000", "dim_3_t": "Veterinary activities", "valor": "105"}]}
```

INE calls pet clinics "veterinary activities", code 75000, so the description says that this count covers pet clinics. Indicator 0012934 counts establishments, the clinics themselves; 0014064 counts the businesses that run them.

**In my runs:** right in 1 of 5. In the other four, the model asked for 2023, a year before INE's latest, and answered with that year's 104 businesses.

### How many crimes against pets were recorded in Porto last year?

**Expected answer:** 36 in 2025.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0012261&Dim1=S7A2025&Dim2=11A1312&Dim3=10&lang=EN
```
```json
{"2025": [{"geocod": "11A1312", "geodsg": "Porto", "dim_3": "10", "dim_3_t": "Crimes against pet animals", "valor": "36"}]}
```

The figures are the police's, published by INE: crime category 10 is the killing, ill-treatment and abandonment of pets. Portugal recorded 1,665 such crimes that year.

**In my runs:** right in 5 of 5.

### Has the price of a visit to a pet clinic changed in the last year?

**Expected answer:** yes, it rose 4.08% from September 2025 to September 2026, while prices overall rose 3.61%. The answer changes every month.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0014647&Dim1=S3A202609&Dim2=PT&Dim3=0945&lang=EN
```
```json
{"September 2026": [{"geocod": "PT", "dim_3": "0945", "dim_3_t": "Veterinary and other services for pets", "ind_string": "4,08", "valor": "4.08"}]}
```

0945 is the category of veterinary and other services for pets in the consumer price index, and the indicator is the change from the same month a year earlier. INE publishes a month about six weeks after it ends.

**In my runs:** right in 1 of 5. In the other four, one answer read twelve months from Eurostat and gave a range of 3.9% to 4.4%; one asked INE for November 2026, which INE has yet to publish, and stopped at the error that says to leave the month out; one sent the month as "Oct", which the mock answers with September's recording, and gave 4.08% as October's change; and one came in five seconds, without a call, saying that the API does not track prices.

### Has the number of pet care businesses in Portugal changed?

**Expected answer:** yes, from 1,117 in 2024 to 1,359 in 2025, which is 22% more.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0014972&Dim1=T&Dim2=PT&Dim3=96992&Dim4=T&lang=EN
```
```json
{"2024": [{"dim_3_t": "Pet care services activities", "valor": "1117"}], "2025": [{"dim_3_t": "Pet care services activities", "valor": "1359"}]}
```

Pet care, code 96992, is grooming, pet sitting, training and boarding. INE publishes this series for Portugal alone, from 2024, and `Dim1=T` asks for every year.

**In my runs:** right in 4 of 5. In the other one, the model compared 930 businesses in 2023, from the older classification, with 1,117 in 2024, and left out 2025.

### Which countries does Portugal import dog and cat food from?

**Expected answer:** Spain first, with 163.1 million euros of the 304.9 million in 2025, then France with 51.2 million and Germany with 22.4 million.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0005719&Dim1=S7A2025&Dim3=230910&lang=EN
```
```json
{"geocod": "ES", "geodsg": "Spain", "dim_3": "230910", "dim_3_t": "Dog or cat food, put up for retail sale", "valor": "163093227"}
```

The answer mixes countries with totals, such as World and Intra European Union, so the description tells the model that a country has a two-letter code.

**In my runs:** right in 0 of 5. Three answers listed the first countries of INE's answer, in its order and without their values, and missed Spain and France, the two largest. One sent `place: PT`, which is the country the goods come from, and answered that the imports were €0. One said, after two queries that failed, that the field leaves out the countries of origin.

### How does the change in pet clinic prices in Portugal compare with the EU?

**Expected answer:** in September 2026, pet clinic prices were 4.1% higher than a year earlier in Portugal, and 3.3% higher in the EU. The answer changes every month.

```
GET https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/prc_hicp_minr?geo=PT&geo=EU27_2020&coicop18=CP0945&unit=RCH_A&time=2026-09&lang=en
```
```json
{"value": {"0": 3.3, "1": 4.1}, "dimension": {"geo": {"category": {"index": {"EU27_2020": 0, "PT": 1}}}}, ...}
```

Eurostat answers in JSON-stat: each value is keyed by a position, and `dimension` says which country each position belongs to. The field turns that into a country and a value for each row.

**In my runs:** right in 2 of 5. In the other three, one answer used `eurostatPriceLevels`, the price levels of health care against the EU, in place of the change of prices; one gave Portugal 3.3% and the EU 4.1%, the two swapped; and one gave both 4.1%.

### How many pet shops are there in Porto?

**Expected answer:** 24 businesses with 36 shops in 2024.

```
GET https://www.ine.pt/ine/json_indicador/pindica.jsp?op=2&varcd=0014064&Dim1=S7A2024&Dim2=11A1312&Dim3=47762&Dim4=T&lang=EN
```
```json
{"2024": [{"geocod": "11A1312", "geodsg": "Porto", "dim_3": "47762", "dim_3_t": "Retail sale of pet animals and pet food in specialised stores", "valor": "24"}]}
```

The same indicators as the pet clinics, with the activity code of pet shops, 47762. The model can give either count, and the two together answer the question best.

**In my runs:** right in 2 of 5. In the other three, two answers asked for 2023 and gave that year's 22 businesses, and one stopped after two queries that failed.

## 5. Everything a field needs goes in its description

The model sees a field through two tools: `searchSchema` ranks it by its declaration and its description, and `introspectType` shows the descriptions of its arguments and of its type. What the model needs to use a field must therefore be in those descriptions from the first version:

- the unit of every number, such as metres per second for the speed of a bus;
- what one request returns, such as one year of an INE indicator;
- a trap in the data, such as counts that add up from January;
- the codes an argument takes, such as `11A1312` for Porto or `75000` for pet clinics.

I asked each question five times, changed a description or the shape of a field where the model went wrong, and asked all 18 again. I made these changes:

- **A value in a description becomes an answer without a call.** The first description of `turismoLocalLodging` said that Porto has about 9,900 units. In four runs of five, the model answered 9,900 straight from the description, without a query. With the sentence gone, it queried in every run and answered 9,942. A later run answered "Portugal at 85 in 2024" from the description of `eurostatPriceLevels`, so we took every figure out of the descriptions: the counts of records, such as "about 7,700 dams", and the example values copied from records, such as 84.4 for the bed occupancy of a hospital. A description documents the API, and the data stays in the answers.
- **An example value can come back as data.** With "such as 1.75" on the price of a station, one run answered "1.75 € at Shell", a price and a station the model invented. A field that holds a value now gives its unit and its scale, such as "a fraction, where 0.5 is 50 percent".
- **The model writes names where the API wants codes.** It asked INE for `place: "Porto"`, `importedProduct: "dog and cat food"` and `sex: "all"`, and Eurostat for `geo=Portugal`. A description that asked for codes did not change that, so the server now turns a name into its code: `11A1312`, `230910`, `T` and `PT`. In the round after the change, the pet clinics went from 0 to 5 runs of 5, and the dog and cat food from 0 to 4.
- **A name invites the question it sounds like.** "Has the price changed in the last year?" sent the model to `eurostatConsumerPriceYearlyChange`, the change of a whole calendar year's average, in most runs. Renamed `eurostatConsumerPriceCalendarYearChange`, it gave way to the field of the last twelve months in 4 runs of 5.
- **Two fields for one dataset make the model choose, and it can choose the wrong one.** The SNS's emergency visits had a field for the raw dataset, whose counts add up from January, and one for the visits of a month. The model read the running totals as monthly visits, so the raw field went.
- **An error names what the model sent.** INE answers a year it does not have with "Code(s) in Dim1 not valid for the indicator", and the model had sent `year: 2026`, an argument whose name the error does not mention. The field now adds the argument behind each dimension INE names: "Dim1 is the year: leave it out for the latest year." In the last round, four runs asked INE for a period it does not have, and three of them then left the period out. Eurostat answers a month together with `lastTimePeriod` with a 400 in its own parameter names, so `eurostatConsumerPriceChange` rejects `month` together with `latestPeriods` before it calls Eurostat, in the names of its own arguments.
- **The model reads an argument's description only when it asks for it.** `searchSchema` returns a field with its description and the names and types of its arguments, and only `introspectType` shows what each argument means. INE's `allPeriods` returns one observation for each period. In five of the seven runs that set it, the model gave a value the wrong year or added two years together, and two answers said the values "likely represent different periods", which `period` would have told them. The description of `allPeriods` now says to select `period`, and since runs that skip `introspectType` miss it, the field also rejects `allPeriods` without `period`, with an error that says why. In the last round, the six runs that got an answer for every period gave each value its right year, though two of them then took 2011 to 2023 for ten years.

## Recap

- The schema reads nine public services through 517 root fields, about 158,000 tokens, and the three tools of chapter 6 find the right field in it.
- The `mock` profile answers from recorded responses and pins the clock to the moment they were recorded, so the chapter works without wifi.
- A description says everything the model needs to use its field: units, codes, what one request returns, and the traps in the data.
