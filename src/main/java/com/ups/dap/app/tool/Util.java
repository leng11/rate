package com.ups.dap.app.tool;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.openapitools.oauth.client.ApiClient;
import org.openapitools.oauth.client.api.OAuthApi;
import org.openapitools.oauth.client.model.GenerateTokenSuccessResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.dap.app.AppConfig;
import com.ups.dap.app.RateDemo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Util {
	private static final Map<String, API_TYPE> JSON_OBJECT_TO_TARGET_TYPE = new HashMap<>();
	static {
		JSON_OBJECT_TO_TARGET_TYPE.put("\"RateResponse\".\"RatedShipment\"", API_TYPE.ARRAY);
		JSON_OBJECT_TO_TARGET_TYPE.put("\"RateResponse\".\"RatedShipment\".\"ItemizedCharges\"", API_TYPE.ARRAY);
		JSON_OBJECT_TO_TARGET_TYPE.put("\"RateResponse\".\"RatedShipment\".\"RatedPackage\"", API_TYPE.ARRAY);
		JSON_OBJECT_TO_TARGET_TYPE.put("\"RateResponse\".\"RatedShipment\".\"RatedShipmentAlert\"", API_TYPE.ARRAY);
	}
	
	private enum API_TYPE { ARRAY }
	private static String clientCredentials = "client_credentials";
	private static String basicAuth = "Basic ";
	
	public static Map<String, API_TYPE> getJsonToObjectConversionMap() {
		return Collections.unmodifiableMap(JSON_OBJECT_TO_TARGET_TYPE);
	}
	
	public static String getAccessToken(final AppConfig appConfig, final RestTemplate restTemplate) {
		// First try to re-use previously obtained access token.
		// If yes, use it.
		// Otherwise, get an access token and store for future use.
		String accessToken = appConfig.getPreviousObtainedToken();
		if(null == accessToken) {
			accessToken = appConfig.getAccessTokenStore().get(appConfig.getClientID());
		}
		
		if(null == accessToken) {
			OAuthApi oauthApi = new OAuthApi(new ApiClient(restTemplate));
			final String encodedClientIdAndSecret = Base64.getEncoder().encodeToString(
																			(appConfig.getClientID() + ':' + appConfig.getSecret()).
																			getBytes(StandardCharsets.UTF_8));
			oauthApi.getApiClient().setBasePath(appConfig.getOauthBaseUrl());
			oauthApi.getApiClient().addDefaultHeader(HttpHeaders.AUTHORIZATION, basicAuth + encodedClientIdAndSecret);
			log.info("ecnoded clientId and secret: [{}]", encodedClientIdAndSecret);
			
			try {
				GenerateTokenSuccessResponse generateAccessTokenResponse = oauthApi.generateToken(clientCredentials, null);
				accessToken = generateAccessTokenResponse.getAccessToken();		
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
		log.info("access token [{}]", accessToken);
		appConfig.getAccessTokenStore().put(appConfig.getClientID(), accessToken);
		return accessToken;
	}
	
	public static <T> T createRequestFromJsonFile(final String scenarioName, final String filePath,
			final Class<T> requestClass, final AppConfig appConfig, final List<CreateRequestEnricher> enrichers) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			T request = mapper.readValue(RateDemo.class.getClassLoader().getResourceAsStream(filePath), requestClass);

			enrichers.forEach(enricher -> enricher.enrich(scenarioName, appConfig, request));

			return request;
		} catch (Exception ex) {
			throw new IllegalStateException("failed to constrcut object from [" + filePath + ']', ex);
		}
	}
	
	public static void dayRoll(final Calendar startDay, int days) {
		int offsetBy = 1;
        if(0 > days) {
        	offsetBy = -1;
        	days = Math.abs(days);
        }
        for (int i=0; i<days; i++) {
            do {
            	startDay.add(Calendar.DAY_OF_MONTH, offsetBy);
            } while (!isWeekDay(startDay));
        }
	}
	
	public static boolean isWeekDay(Calendar cal) {
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        return(dayOfWeek != Calendar.SUNDAY && dayOfWeek != Calendar.SATURDAY); 
    } 
	
	public static <T> T jsonResultPreprocess(final String resultResponse, final Map<String, API_TYPE> jsonObject2TargetType, Class<T> targetClassType) throws JsonProcessingException {
		AtomicReference<String> response = new AtomicReference<>(resultResponse);
		
		Consumer<Map.Entry<String, API_TYPE>> convertObjectToArray = entry -> {
																				final String elementString = entry.getKey();
																				
																				String updatedResponse = response.get();
																				
																				// find the end position of last element.
																				SimpleEntry<String, Integer> pointer = indexOf(elementString, updatedResponse);
																				
																				if(pointer.getValue() != -1) {
																					updatedResponse = updateJsonResponse(updatedResponse, pointer);
																				}

																				// store the updated response for next element processing in the jsonObject2TargetType.
																				response.set(updatedResponse);
																			};
																	
		// Currently converting object to array of object.
		jsonObject2TargetType.entrySet().stream().
											filter(entry->entry.getValue() == API_TYPE.ARRAY).
											forEach(convertObjectToArray::accept);
		
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.readValue(response.get(), targetClassType);
	}
	
	private static String updateJsonResponse(final String response, final SimpleEntry<String, Integer> pointer) {
		int position = pointer.getValue();
		String lastElement = pointer.getKey();
		
		String updatedResponse = response;
		while(-1 != position) {
			position = updatedResponse.indexOf(":", position);
			
			// Is last element already an array in resultResponse?
			boolean arrayType = false;
			boolean done = false;
			for(int i=position+1; i<updatedResponse.length(); i++) {
				if(updatedResponse.charAt(i) == '{') {
					// non-array
					position = i;
					done = true;
				} else if(updatedResponse.charAt(i) == '[') {
					arrayType = true;
					done = true;
				}
				
				if(done) {
					break;	
				}
			}
			
			if(!arrayType) {
				StringBuilder builder = new StringBuilder(updatedResponse.substring(0, position));
				builder.append('[').append(updatedResponse.substring(position, updatedResponse.length()));
				updatedResponse = addClosingArray(builder.toString(), position);
			}
			position = updatedResponse.indexOf(lastElement, position);
		}
		return updatedResponse;
	}
	
	private static SimpleEntry<String, Integer> indexOf(final String elementString, final String response) {
		int position = 0;
		final String [] elements = elementString.split("\\.");
		String lastElement = null;
		for(String element : elements) {
			position = response.indexOf(element, position);
			if(-1 == position) {
				return new SimpleEntry<>(lastElement, position);
			}
			lastElement = element;
			position += lastElement.length();
		}
		
		if(-1 == position || null == lastElement) {
			throw new NoSuchElementException(elementString + " does not exist in response");
		}

		return new SimpleEntry<>(lastElement, position);
	}
	
	private static String addClosingArray(final String response, int position) {
		position = response.indexOf('{', position);
		if(-1 == position) {
			throw new NoSuchElementException("internal error - cannot find beginning of element.");
		}
		
		int outstandingParathesis = 0;
		
		for(int i=position+1; i<response.length(); i++) {
			if(response.charAt(i) == '}') {
				if(outstandingParathesis == 0) {
				// found the element of element.
				StringBuilder builder = new StringBuilder(response.substring(0, i+1));
				builder.append(']').append(response.substring(i+1, response.length()));
				return builder.toString();
				} else {
					outstandingParathesis--;
				}
			}
			if(response.charAt(i) == '{') {
				outstandingParathesis++;
			}
		}
		throw new NoSuchElementException("incomplete response - missing ending element parathesis [" + response + ']');
	}
	
	private Util() {
	}
}
