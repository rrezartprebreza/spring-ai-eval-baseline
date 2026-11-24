package io.github.rrezartprebreza.evalbaseline.springai;

/**
 * Price of input (prompt) and output (completion) tokens, in your own currency per million
 * tokens, as providers usually publish them.
 */
public record TokenPrices(double inputPerMillion, double outputPerMillion) {

    public TokenPrices {
        if (inputPerMillion < 0 || outputPerMillion < 0) {
            throw new IllegalArgumentException("prices must not be negative");
        }
    }

    public static TokenPrices perMillion(double input, double output) {
        return new TokenPrices(input, output);
    }

    /** Cost of one answer; missing counts count as zero. */
    public double cost(Integer promptTokens, Integer completionTokens) {
        double in = promptTokens == null ? 0 : promptTokens;
        double out = completionTokens == null ? 0 : completionTokens;
        return (in * inputPerMillion + out * outputPerMillion) / 1_000_000;
    }
}
