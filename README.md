# Jaeger_Microservices
# Stock Trading and Asset Service

This repository contains two AWS Lambda functions for stock trading and asset retrieval using Alpaca API. One function is implemented in Java and the other in .NET. These Lambda functions are integrated with OpenTelemetry for distributed tracing using Jaeger.

## Java Lambda Function

### Overview

The Java Lambda function (`StockInfoLambda.java`) retrieves information about a stock symbol and uses Alpaca API to get stock details. It demonstrates how to use OpenTelemetry for tracing.

### Dependencies

- AWS Lambda
- Alpaca API
- OpenTelemetry
- Jaeger

### Configuration

Make sure to configure the following environment variables in your AWS Lambda function for proper functioning:

- `ALPACA_API_KEY`: Your Alpaca API Key.
- `ALPACA_SECRET_KEY`: Your Alpaca API Secret Key.

### Building and Deployment

You can package and deploy this Lambda function as per AWS Lambda documentation.

### Usage

Once deployed, you can invoke the function by providing a JSON payload with a stock symbol as input.

## .NET Lambda Function

### Overview

The .NET Lambda function (`Function.cs`) retrieves and displays a list of active assets using the Alpaca API. It also introduces random time delays to simulate a more complex function.

### Dependencies

- AWS Lambda
- Alpaca API
- OpenTelemetry
- Jaeger

### Configuration

- `ALPACA_API_KEY`: Your Alpaca API Key.
- `ALPACA_SECRET_KEY`: Your Alpaca API Secret Key.

### Building and Deployment

You can package and deploy this Lambda function as per AWS Lambda documentation.

### Usage

Once deployed, you can invoke the function through API Gateway by providing an HTTP request. It will retrieve and display a list of active assets with random time delays.

## How to Use

1. Deploy the Lambda functions as per AWS Lambda documentation.
2. Configure environment variables with your Alpaca API credentials.
3. Invoke the Lambda functions and provide the required input parameters.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

## Acknowledgments

- Alpaca API
- OpenTelemetry
- Jaeger
