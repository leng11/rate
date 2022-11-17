package com.ups.dap.app;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.openapitools.rate.client.ApiClient;
import org.openapitools.rate.client.model.RATERequestWrapper;
import org.openapitools.rate.client.model.RATEResponseWrapper;
import org.openapitools.rate.client.model.RateResponseRatedShipment;
import org.openapitools.rate.client.model.RateResponseResponse;
import org.openapitools.rate.client.model.RatedShipmentGuaranteedDelivery;
import org.openapitools.rate.client.model.RatedShipmentRatedPackage;
import org.openapitools.rate.client.model.ResponseAlert;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.dap.app.tool.CreateRequestEnricher;
import com.ups.dap.app.tool.Util;
import com.ups.dap.patch.RateApi;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@AllArgsConstructor
public class RateDemo implements CommandLineRunner {
	private static final String TRANSPORTATION_CHARGES_KEY = "TransportationCharges";
	private static final String BASE_SERVICE_CHARGE_KEY = "BaseServiceCharge";
	private static final String SERVICE_OPTIONS_CHARGES_KEY = "ServiceOptionsCharges";
	private static final String TOTAL_CHARGES_KEY = "TotalCharges";

	private static final String SCENARIO_NAME_LOG_FORMAT = "Rate Response for scenario: {}";
	private static final String RATED_PACKAGE_LOG_FORMAT = "\t\t\t{}: {} {}";
	
	RestTemplate restTemplate;
	AppConfig appConfig; 

	@Override
	public void run(String... args) throws Exception {
		try {
			// Get an access token.
			String accessToken = Util.getAccessToken(appConfig, restTemplate);
			
			// Get Rate information.
			rateActivity(accessToken);
		
		} catch (Exception ex) {
			applicationErrorHandler(ex);
		}
	}
	
	private void rateActivity(final String accessToken) {
		// Prepare Rate api access.
		final RateApi rateApi = initializeRateApi(restTemplate, appConfig.getRateBaseUrl(), accessToken);
		
		ObjectMapper objectMapper = new ObjectMapper();
					
		// Run through different Rate request, ie. simple rate, TNT rate, mutliPiece rate, standard account rate, 
		//          international rate, invalid requestoption(like "RateDD"), invalid additionalinfo (like "RateDD").
		// Each iteration will create a RATERequestWrapper from a pre-determined json file verses
		// creating a RATERequestWrapper object and calling a setter for particular attribute.
		for(Map.Entry<String, List<String>> entry : appConfig.getScenarioProperties().entrySet()) {
			try {
				RATERequestWrapper rateRequestWrapper = Util.createRequestFromJsonFile(entry.getKey(),
																							entry.getValue().get(AppConfig.SCENARIO_PROPERTIES_JSON_FILE_NAME),
																							RATERequestWrapper.class,
																							appConfig,
																							Arrays.asList(new CreateRequestEnricher() {}));
			
				// create a 32 character unique id.
				final String transId = UUID.randomUUID().toString().replace("-", "");
				log.info("transId: {}", transId);
			
				final String requestOption = entry.getValue().get(AppConfig.SCENARIO_PORPERTY_REQUEST_OPTION_PARAMETER);			
				String additionalInfo = null;
				if(entry.getValue().size() > AppConfig.SCENARIO_PORPERTY_ADDITIONAL_INFO_PARAMETER) {
					additionalInfo = entry.getValue().get(AppConfig.SCENARIO_PORPERTY_ADDITIONAL_INFO_PARAMETER);
				}
			
				// Get a rate information for a particular shipment.
				final RATEResponseWrapper rateResponseWrapper = Util.jsonResultPreprocess(rateApi.rate(appConfig.getRateVersion(),
																													requestOption,
																													rateRequestWrapper,
																													transId,
																													appConfig.getTransactionSrc(),
																													additionalInfo),
																							Util.getJsonToObjectConversionMap(),
																							RATEResponseWrapper.class);
				
				log.info("response json: [{}]", objectMapper.writeValueAsString(rateResponseWrapper));
				processResult(entry.getKey(), rateResponseWrapper);
			} catch(HttpClientErrorException httpClientException) {
				log.warn(SCENARIO_NAME_LOG_FORMAT, entry.getKey());
				log.warn("Http exception - " + httpClientException.getStatusCode(), httpClientException);
			} catch(JsonProcessingException jsonProcessingException) {
				log.warn(SCENARIO_NAME_LOG_FORMAT, entry.getKey());
				log.warn("failed to write object into json format for logging.", jsonProcessingException);
			} catch(Exception ex) {
				log.warn(SCENARIO_NAME_LOG_FORMAT, entry.getKey());
				log.warn("failed to run scenario.", ex);
			}
			log.info("\n");
		}
	}
	
	public static RateApi initializeRateApi(final RestTemplate restTemplate, final String rateBaseUrl, final String accessToken) {
		RateApi rateApi = new RateApi(new ApiClient(restTemplate));
		rateApi.getApiClient().setBasePath(rateBaseUrl);
		rateApi.getApiClient().addDefaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		return rateApi;
	}
	
	private void processResult(final String scenarioName, final RATEResponseWrapper rateResponseWrapper) {
		log.info(SCENARIO_NAME_LOG_FORMAT, scenarioName);
		
		printResponseStatusInfo(rateResponseWrapper.getRateResponse().getResponse());
		printRatedShipmentInfo(rateResponseWrapper.getRateResponse().getRatedShipment());
	}
	
	private void printResponseStatusInfo(final RateResponseResponse response) {
		log.info("\tResponse");
		log.info("\t\tReponseStatus: {}", response.getResponseStatus().getDescription());
		
		final List<ResponseAlert> alerts = response.getAlert();
		if(!alerts.isEmpty()) {
			log.info("\tAlert:");
			alerts.stream().forEach(alert->log.info("\t\tCode: [{}]\tDescription: [{}], )", alert.getCode(), alert.getDescription()));
		}
	}
	
	private void printRatedShipmentInfo(final List<RateResponseRatedShipment> ratedShipments) {
		ratedShipments.forEach(ratedShipment-> {
			log.info("\tRatedShipment");
			log.info("\t\tService: Code[{}]", ratedShipment.getService().getCode());
			printGuaranteedDelivery(ratedShipment.getGuaranteedDelivery());
			log.info("\t\tBillingWeight: {} {}", ratedShipment.getBillingWeight().getWeight(),
													ratedShipment.getBillingWeight().getUnitOfMeasurement().getDescription());
			log.info("\t\tTransportationCharges: {} {}", ratedShipment.getTransportationCharges().getMonetaryValue(),
															ratedShipment.getTransportationCharges().getCurrencyCode());
			log.info("\t\tBaseServiceCharge: {} {}", ratedShipment.getBaseServiceCharge().getMonetaryValue(),
														ratedShipment.getBaseServiceCharge().getCurrencyCode());
			log.info("\t\tServiceOptionsCharges: {} {}", ratedShipment.getServiceOptionsCharges().getMonetaryValue(),
															ratedShipment.getServiceOptionsCharges().getCurrencyCode());
			log.info("\t\tTotalCharges: {} {}", ratedShipment.getTotalCharges().getMonetaryValue(),
													ratedShipment.getTotalCharges().getCurrencyCode());
			printRatedPackage(ratedShipment.getRatedPackage());
		});	
	}
	
	private void printGuaranteedDelivery(final RatedShipmentGuaranteedDelivery guarateedDelivery) {
		if(null != guarateedDelivery) {
			log.info("\t\tGuaranteedDelivery:");
			log.info("\t\t\tBusinessDaysInTransit: {}", guarateedDelivery.getBusinessDaysInTransit());
			
			final String time = guarateedDelivery.getDeliveryByTime();
			if(null != time) {
				log.info("\t\t\tDeliveryByTime: {}", time);
			}
		}
	}
	
	private void printRatedPackage(final List<RatedShipmentRatedPackage> ratedPackages) {
		ratedPackages.forEach(ratedPackage-> {
												log.info("\t\tRatedPackage:");
												log.info(RATED_PACKAGE_LOG_FORMAT, TRANSPORTATION_CHARGES_KEY,
																					ratedPackage.getTransportationCharges().getMonetaryValue(),
																					ratedPackage.getTransportationCharges().getCurrencyCode());
												log.info(RATED_PACKAGE_LOG_FORMAT, BASE_SERVICE_CHARGE_KEY,
																					ratedPackage.getBaseServiceCharge().getMonetaryValue(),
																					ratedPackage.getBaseServiceCharge().getCurrencyCode());
												log.info(RATED_PACKAGE_LOG_FORMAT, SERVICE_OPTIONS_CHARGES_KEY,
																					ratedPackage.getServiceOptionsCharges().getMonetaryValue(),
																					ratedPackage.getServiceOptionsCharges().getCurrencyCode());
												log.info(RATED_PACKAGE_LOG_FORMAT, TOTAL_CHARGES_KEY,
																					ratedPackage.getTotalCharges().getMonetaryValue(),
																					ratedPackage.getTotalCharges().getCurrencyCode());
											});
	}
	
	private void applicationErrorHandler(Exception ex) {
		log.warn("failed to complete request", ex);
	}
}
