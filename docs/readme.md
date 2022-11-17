# Rate
## Introduction

The Rating API allows applications to look up and compare rates for UPS services:

The Rating API gives client applications the ability to:

- Find the rate for a specific UPS service for a specific package or shipment
- Compare available rates and services for a specific package or shipment
- Request negotiated rates for a specific package or shipment
>Note: Customers must be authorized and activated for negotiated rates. 
The API provides the negotiated rates that apply to the selected service. For more 
information on negotiated rates, including authorization to receive them, please check 
with your UPS account representative.


## Run Procedure
- fork a copy of the repo in GitHub
- get a local copy of the project
```sh
git clone <forked repo url>
```
- build project
```sh
cd <project home>
mvn clean package
```
- update your information in src/main/resources/application.properties file

|Property Name|Description|
| :------: | :------: |
|```api.oauth.partner.client.id```| client id obtained in the onboarding process|
|```api.oauth.partner.secret```| client secret obtained in the onboarding process|
>    These are the properties in a section marked with "# UPS partner specific properties 
(change them!)" where you update with your client specific information like client id and secret.


- run com.ups.dap.RateApplication
```sh
java -jar rate-x.x.x.jar             # ex. java -jar rate-0.0.1-SNAPSHOT.jar
```
- check console output for application result


## Code Walk Through
There are 2 notable classes in this tutorial, namely com.ups.dap.app.AppConfig 
and com.ups.dap.app.RateDemo.  The AppConfig class is a configuration class leveraging 
Spring injection to incorporate the property value from src/main/resources/application.properties 
file.  The RateDemo is to illustrate how to use the Rate api.

```java
 String accessToken = Util.getAccessToken(appConfig, restTemplate);
```
> Get an access token via OAuth client_credentials grant type.

```java
 // Prepare TNT api access.
 final RateApi rateApi = initializeRateApi(restTemplate, appConfig.getRateBaseUrl(), accessToken);
```
> initializeRateApi function is to create a Rate api object with the base url and populated 
the HTTP Authorization header with the access token.
					
```java
    RATERequestWrapper rateRequestWrapper = Util.createRequestFromJsonFile(entry.getKey(),
														entry.getValue().get(AppConfig.SCENARIO_PROPERTIES_JSON_FILE_NAME),
														RATERequestWrapper.class,
														appConfig,
														Arrays.asList(new CreateRequestEnricher() {}));
```
> It reconstructs a RATERequestWrapper object from a json file which includes 
origin/destination address and other required information.  In a typical application, 
a RATERequestWrapper object would be created via a default constructor followed by 
a set of setter to populate the necessary attribute.

```java
	// Get a rate information for a particular shipment.
	RATEResponseWrapper rateResponseWrapper = rateApi.rate(appConfig.getRateVersion(),
														requestOption,
														rateRequestWrapper,
														transId,
														appConfig.getTransactionSrc(),
														additionalInfo);
```
> A RATEResponseWrapper object will be returned from a backend server for a particular 
shipment specified in the RATERequestWrapper object.  The RATEResponseWrapper 
has ResponseStatus and Alert object including the status and alert in respective section. The detail rate information will be in the RatedShipment object.


### Data Schema 
- [Request Schema RATERequestWrapper](../docs/RATERequestWrapper.md)

- [Response Schema RATEResponseWrapper](../docs/RATEResponseWrapper.md)

### Sample Request/Response
- Simple Rate request
```json
{
    "RateRequest": {
      "Request": {
        "RequestOption": "Rate",
        "SubVersion": "2108",
        "TransactionReference": {
          "CustomerContext": "CustomerContext"
        }
      },
      "Shipment": {
        "Shipper": {
          "Name": "ShipperName",
          "ShipperNumber": "7E6Y36",
          "Address": {
            "AddressLine": "2311   RD",
            "City": "TIMONIUM",
            "StateProvinceCode": "MD",
            "PostalCode": "21093",
            "CountryCode": "US"
          }
        },
        "ShipTo": {
          "Name": "ShipToName",
          "Address": {
            "AddressLine": "ShipToAddressLine",
            "City": "Alpharetta",
            "StateProvinceCode": "GA",
            "PostalCode": "30005",
            "CountryCode": "US"
          }
        },
        "ShipFrom": {
          "Name": "ShipFromName",
          "Address": {
            "AddressLine": "ShipFromAddressLine",
            "City": "TIMONIUM",
            "StateProvinceCode": "MD",
            "PostalCode": "21093",
            "CountryCode": "US"
          }
        },
        "PaymentDetails": {
          "ShipmentCharge": {
            "Type": "01",
            "BillShipper": {
              "AccountNumber": "7E6Y36"
            }
          }
        },
        "Service": {
          "Code": "03",
          "Description": "Ground"
        },
        "NumOfPieces": "1",
        "Package": {
          "PackagingType": {
            "Code": "02",
            "Description": "Packaging"
          },
          "Dimensions": {
            "UnitOfMeasurement": {
              "Code": "IN",
              "Description": "Inches"
            },
            "Length": "5",
            "Width": "5",
            "Height": "5"
          },
          "PackageWeight": {
            "UnitOfMeasurement": {
              "Code": "LBS",
              "Description": "Pounds"
            },
            "Weight": "1"
          },
          "SimpleRate" : {
            "Code" : "XS"
          }
        }
      }
    }
}
```
- An international Rate request 
```json
{
  "RateRequest" : {
    "Request" : {
      "RequestOption" : "Rate",
      "SubVersion" : "1901",
      "TransactionReference" : {
        "CustomerContext" : "Verify success returned when international shipment is created with payment option as Bill Shipper with account number"
      }
    },
    "PickupType" : {
      "Code" : "01",
      "Description" : "pickup"
    },
    "CustomerClassification" : {
      "Code" : "1607",
      "Description" : "CustomerClassification"
    },
    "Shipment" : {
      "OriginRecordTransactionTimestamp" : "2016-07-14T12:01:33.999",
      "Shipper" : {
        "Name" : "Shipper_Name",
        "ShipperNumber" : "7E6Y36",
        "Address" : {
          "AddressLine" : "Morris Road",
          "City" : "Alpharetta",
          "StateProvinceCode" : "GA",
          "PostalCode" : "30005",
          "CountryCode" : "US"
        }
      },
      "ShipTo" : {
        "Name" : "ShipToName",
        "Address" : {
          "AddressLine" : "ShipToAddress",
          "City" : "STARZACH",
          "StateProvinceCode" : "GA",
          "PostalCode" : "72181",
          "CountryCode" : "DE"
        }
      },
      "ShipFrom" : {
        "Name" : "ShipFromName",
        "Address" : {
          "AddressLine" : "ShipFromAddressLine",
          "City" : "Alpharetta",
          "StateProvinceCode" : "GA",
          "PostalCode" : "30005",
          "CountryCode" : "US"
        }
      },
      "PaymentDetails" : {
        "ShipmentCharge" : {
          "Type" : "01",
          "BillShipper" : {
            "AccountNumber" : "7E6Y36"
          }
        }
      },
      "Service" : {
        "Code" : "96",
        "Description" : "UPS Worldwide Express Freight"
      },
      "NumOfPieces" : "10",
      "Package" : {
        "PackagingType" : {
          "Code" : "30",
          "Description" : "Pallet"
        },
        "Dimensions" : {
          "UnitOfMeasurement" : {
            "Code" : "IN",
            "Description" : "Inches"
          },
          "Length" : "5",
          "Width" : "5",
          "Height" : "5"
        },
        "PackageWeight" : {
          "UnitOfMeasurement" : {
            "Code" : "LBS",
            "Description" : "LBS"
          },
          "Weight" : "10"
        },
        "PackageServiceOptions" : { },
        "OversizeIndicator" : "",
        "MinimumBillableWeightIndicator" : ""
      },
      "ShipmentServiceOptions" : { }
    }
  }
}
```


- A TNT Rate request
```json
{
    "RateRequest": {
      "Request": {
        "RequestOption": "Rate",
        "SubVersion": "2108",
        "TransactionReference": {
          "CustomerContext": "CustomerContext"
        }
      },
      "Shipment": {
          "Shipper": {
            "Name": "ShipperName",
            "ShipperNumber": "7E6Y36",
            "Address": {
              "AddressLine": "ShipperAddressLine",
              "City": "TIMONIUM",
              "StateProvinceCode": "MD",
              "PostalCode": "21093",
              "CountryCode": "US"
            }
          },
          "ShipTo": {
            "Name": "ShipToName",
            "Address": {
              "AddressLine": "ShipToAddressLine",
              "City": "Alpharetta",
              "StateProvinceCode": "GA",
              "PostalCode": "30005",
              "CountryCode": "US"
            }
          },
          "ShipFrom": {
            "Name": "ShipFromName",
            "Address": {
              "AddressLine": "ShipFromAddressLine",
              "City": "TIMONIUM",
              "StateProvinceCode": "MD",
              "PostalCode": "21093",
              "CountryCode": "US"
            }
          },
          "PaymentDetails": {
            "ShipmentCharge": {
              "Type": "01",
              "BillShipper": {
                "AccountNumber": "7E6Y36"
              }
            }
          },
          "Service": {
            "Code": "03",
            "Description": "Ground"
          },
          "NumOfPieces": "1",
          "Package": {
            "PackagingType": {
              "Code": "02",
              "Description": "Packaging"
            },
            "Dimensions": {
              "UnitOfMeasurement": {
                "Code": "IN",
                "Description": "Inches"
              },
              "Length": "5",
              "Width": "5",
              "Height": "5"
            },
            "PackageWeight": {
              "UnitOfMeasurement": {
                "Code": "LBS",
                "Description": "Pounds"
              },
              "Weight": "1"
            }
          },
          "DeliveryTimeInformation": {
            "PackageBillType": "03",
            "Pickup": {
              "Date": "20230101",
              "Time": "1000"
            }
          }
        }
      }
    }
}
```

- A successful Simple Rate response
```json
{
  "RateResponse": {
    "Response": {
      "ResponseStatus": {
        "Code": "1",
        "Description": "Success"
      },
      "Alert": [
        {
          "Code": "110971",
          "Description": "Your invoice may vary from the displayed reference rates"
        }
      ],
      "TransactionReference": {
        "CustomerContext": "testing",
        "TransactionIdentifier": "ciewssoasc2b9N16KDjmjl"
      }
    },
    "RatedShipment": {
      "Service": {
        "Code": "03",
        "Description": ""
      },
      "RatedShipmentAlert": {
        "Code": "110971",
        "Description": "Your invoice may vary from the displayed reference rates"
      },
      "BillingWeight": {
        "UnitOfMeasurement": {
          "Code": "LBS",
          "Description": "Pounds"
        },
        "Weight": "0.0"
      },
      "TransportationCharges": {
        "CurrencyCode": "USD",
        "MonetaryValue": "9.45"
      },
      "BaseServiceCharge": {
        "CurrencyCode": "USD",
        "MonetaryValue": "0.00"
      },
      "ServiceOptionsCharges": {
        "CurrencyCode": "USD",
        "MonetaryValue": "0.00"
      },
      "TotalCharges": {
        "CurrencyCode": "USD",
        "MonetaryValue": "9.45"
      },
      "RatedPackage": {
        "TransportationCharges": {
          "CurrencyCode": "USD",
          "MonetaryValue": "9.45"
        },
        "BaseServiceCharge": {
          "CurrencyCode": "USD",
          "MonetaryValue": "9.45"
        },
        "ServiceOptionsCharges": {
          "CurrencyCode": "USD",
          "MonetaryValue": "0.00"
        },
        "ItemizedCharges": {
          "Code": "553",
          "CurrencyCode": "USD",
          "MonetaryValue": "0.00"
        },
        "TotalCharges": {
          "CurrencyCode": "USD",
          "MonetaryValue": "9.45"
        },
        "Weight": "0.7",
        "BillingWeight": {
          "UnitOfMeasurement": {
            "Code": "LBS",
            "Description": "Pounds"
          },
          "Weight": "0.0"
        },
        "SimpleRate": {
          "Code": "XS"
        }
      }
    }
  }
}
```

- An invalid BillShipper.AccountNumber
```json
{
  "response": {
    "errors": [
      {
        "code": "111580",
        "message": "Missing or Invalid account number for Payment Details."
      }
    ]
  }
}
```


### Related tutorial

|Name|Description|
| :------: | :------: |
|OAuth|[Get/refresh access token](http://localhost:8080/GitHub/link/placeHolder)|

### Glossary

|Term|Definition|
| :------: | :------: |
