using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Amazon.Lambda.Core;
using Amazon.Lambda.APIGatewayEvents;
using Alpaca.Markets;
using OpenTelemetry;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

[assembly: LambdaSerializer(typeof(Amazon.Lambda.Serialization.Json.JsonSerializer))]

namespace DemoLambdaFunction
{
    public class Function
    {
        private static TracerProvider tracerProvider;

        static Function()
        {
            // Create and configure the TracerProvider for OpenTelemetry
            tracerProvider = Sdk.CreateTracerProviderBuilder()
                .SetResourceBuilder(ResourceBuilder.CreateDefault().AddService("OrderHistoryService"))
                .AddSource("OrderHistoryService")
                .AddJaegerExporter(o =>
                {
                    o.AgentHost = "13.49.243.139"; // Specify the correct Jaeger endpoint
                    o.AgentPort = 6831;
                })
                .Build();
        }

        // Lambda function handler
        public async Task<APIGatewayProxyResponse> FunctionHandler(APIGatewayProxyRequest request, ILambdaContext context)
        {
            try
            {
                Console.WriteLine("Alpaca API Lambda Function - Retrieve and Display Orders");

                // Start an OpenTelemetry span for tracing
                using var span = tracerProvider.GetTracer("OrderHistoryService").StartActiveSpan("OrderHistoryExecution");

                // Initialize the Alpaca client with API credentials
                var alpacaClient = Environments.Paper.GetAlpacaTradingClient(new SecretKey("PKAGH9GY39KUFP40A564", "4WOePK0nZ5AfoHqkdGMaTKWB5A4aS3HUPRdHdhG3"));

                // Retrieve a list of orders from Alpaca
                var orders = await alpacaClient.ListOrdersAsync(new ListOrdersRequest());

                List<OrderDetails> orderDetailsList = new List<OrderDetails>();

                if (orders.Any())
                {
                    foreach (var order in orders)
                    {
                        // Extract order details
                        string orderSideString = order.OrderSide.ToString();
                        string orderTypeString = order.OrderType.ToString();
                        string orderStatusString = order.OrderStatus.ToString();

                        orderDetailsList.Add(new OrderDetails
                        {
                            OrderId = order.OrderId.ToString(),
                            Symbol = order.Symbol,
                            OrderSide = orderSideString,
                            Quantity = order.Quantity ?? 0,
                            OrderType = orderTypeString,
                            OrderStatus = orderStatusString
                        });
                    }
                }

                // Serialize order details to JSON
                string responseJson = Newtonsoft.Json.JsonConvert.SerializeObject(orderDetailsList);

                // Set a custom attribute in the OpenTelemetry span
                span.SetAttribute("OrderCount", orders.Count());

                return new APIGatewayProxyResponse
                {
                    StatusCode = 200,
                    Body = responseJson,
                    Headers = new Dictionary<string, string> { { "Content-Type", "application/json" } }
                };
            }
            catch (Exception ex)
            {
                // Start a new span for error handling
                using var span = tracerProvider.GetTracer("LambdaService").StartActiveSpan("ErrorHandling");
                span.SetAttribute("ErrorMessage", ex.Message);

                return new APIGatewayProxyResponse
                {
                    StatusCode = 500,
                    Body = $"{{ \"error\": \"{ex.Message}\" }}",
                    Headers = new Dictionary<string, string> { { "Content-Type", "application/json" } }
                };
            }
        }

        // Define a class to hold order details
        public class OrderDetails
        {
            public string OrderId { get; set; }
            public string Symbol { get; set; }
            public string OrderSide { get; set; }
            public decimal Quantity { get; set; }
            public string OrderType { get; set; }
            public string OrderStatus { get; set; }
        }
    }
}
