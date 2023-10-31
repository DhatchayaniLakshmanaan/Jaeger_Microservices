using System; 
using System.Collections.Generic; 
using System.Linq; 
using System.Threading; 
using System.Threading.Tasks; 
using Amazon.Lambda.Core; 
using Amazon.Lambda.APIGatewayEvents; 
using Alpaca.Markets; 
using OpenTelemetry; 
using OpenTelemetry.Resources; 
using OpenTelemetry.Trace; 

// AWS Lambda Serializer attribute
[assembly: LambdaSerializer(typeof(Amazon.Lambda.Serialization.Json.JsonSerializer))]

namespace AssetService2 
{ 
    public class Function 
    { 
        private static TracerProvider tracerProvider; 
        private static readonly Random rnd = new Random(); 

        // Static constructor for initializing the TracerProvider
        static Function() 
        { 
            tracerProvider = Sdk.CreateTracerProviderBuilder()
                .SetResourceBuilder(ResourceBuilder.CreateDefault().AddService("AssetService"))
                .AddSource("AssetService")
                .AddJaegerExporter(o => 
                {
                    o.AgentHost = "16.171.255.173";
                    o.AgentPort = 6831;
                })
                .Build(); 
        } 

        public async Task<APIGatewayProxyResponse> FunctionHandler(APIGatewayProxyRequest request, ILambdaContext context) 
        { 
            try 
            { 
                Console.WriteLine("Alpaca API Lambda Function - Retrieve and Display Assets"); 

                // Start an OpenTelemetry span
                using var span = tracerProvider.GetTracer("AssetService").StartActiveSpan("AssetExecution"); 

                var alpacaClient = Environments.Paper.GetAlpacaTradingClient(new SecretKey("PKAGH9GY39KUFP40A564", "4WOePK0nZ5AfoHqkdGMaTKWB5A4aS3HUPRdHdhG3")); 
                var assets = await alpacaClient.ListAssetsAsync(new AssetsRequest { AssetStatus = AssetStatus.Active }); 

                List<AssetDetails> assetDetailsList = new List<AssetDetails>(); 

                if (assets.Any()) 
                { 
                    foreach (var asset in assets) 
                    { 
                        string assetStatusString = MapAssetStatusToString(asset.Status.ToString()); 

                        assetDetailsList.Add(new AssetDetails 
                        { 
                            Symbol = asset.Symbol, 
                            Name = asset.Name, 
                            Exchange = asset.Exchange.ToString(), 
                            Tradable = asset.IsTradable ? "Yes" : "No", 
                            Status = assetStatusString 
                        }); 
                    } 
                } 

                string responseJson = Newtonsoft.Json.JsonConvert.SerializeObject(assetDetailsList); 

                // Set an OpenTelemetry attribute for asset count
                span.SetAttribute("AssetCount", assets.Count); 

                // Add a random time delay between 20 and 70 seconds
                int delayTimeSeconds = 20 + (int)(rnd.NextDouble() * 50) * 1000; 
                Thread.Sleep(delayTimeSeconds); 

                // Set an OpenTelemetry attribute for delay time in seconds
                span.SetAttribute("DelayTimeSeconds", delayTimeSeconds / 1000); 

                return new APIGatewayProxyResponse 
                { 
                    StatusCode = 200, 
                    Body = responseJson, 
                    Headers = new Dictionary<string, string> { { "Content-Type", "application/json" } } 
                }; 
            } 
            catch (Exception ex) 
            { 
                // Start an OpenTelemetry span for error handling
                using var span = tracerProvider.GetTracer("LambdaService").StartActiveSpan("ErrorHandling"); 

                // Set an OpenTelemetry attribute for the error message
                span.SetAttribute("ErrorMessage", ex.Message); 

                return new APIGatewayProxyResponse 
                { 
                    StatusCode = 500, 
                    Body = $"{{ \"error\": \"{ex.Message}\" }}", 
                    Headers = new Dictionary<string, string> { { "Content-Type", "application/json" } } 
                }; 
            } 
        } 

        // Helper method to map asset status
        private string MapAssetStatusToString(string assetStatus) 
        { 
            switch (assetStatus) 
            { 
                case "active": 
                    return "Active"; 
                case "inactive": 
                    return "Inactive"; 
                default: 
                    return "Unknown"; 
            } 
        } 
    } 
}
