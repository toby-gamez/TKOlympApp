using System.Numerics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace TkOlympApp.Services;

public class BigIntegerJsonConverter : JsonConverter<BigInteger>
{
    public override BigInteger Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.Null)
        {
            return default;
        }

        string s;
        if (reader.TokenType == JsonTokenType.String)
        {
            s = reader.GetString() ?? string.Empty;
            if (string.IsNullOrWhiteSpace(s)) return default;
        }
        else if (reader.TokenType == JsonTokenType.Number)
        {
            s = Encoding.UTF8.GetString(reader.ValueSpan);
        }
        else
        {
            throw new JsonException($"Unexpected token parsing BigInteger: {reader.TokenType}");
        }

        if (BigInteger.TryParse(s, out var value))
            return value;

        throw new JsonException($"Unable to parse BigInteger from '{s}'");
    }

    public override void Write(Utf8JsonWriter writer, BigInteger value, JsonSerializerOptions options)
    {
        writer.WriteStringValue(value.ToString());
    }
}
