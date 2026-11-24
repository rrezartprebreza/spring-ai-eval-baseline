package io.github.rrezartprebreza.evalbaseline;

/**
 * Summary of the scores a case reported, one score per run between 0 and 1. Kept as count, sum
 * and sum of squares so it fits in the baseline file and two summaries can be compared with a
 * t-test.
 *
 * @param count number of runs that reported a score
 * @param sum   sum of the scores
 * @param sumSq sum of the squared scores
 */
public record ScoreStats(int count, double sum, double sumSq) {

    /** No scores reported. */
    public static final ScoreStats NONE = new ScoreStats(0, 0, 0);

    public ScoreStats {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    public static ScoreStats of(double... scores) {
        ScoreStats s = NONE;
        for (double v : scores) {
            s = s.plus(v);
        }
        return s;
    }

    public ScoreStats plus(double score) {
        if (Double.isNaN(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("a score must be between 0 and 1, got " + score);
        }
        return new ScoreStats(count + 1, sum + score, sumSq + score * score);
    }

    public ScoreStats plus(ScoreStats other) {
        return new ScoreStats(count + other.count, sum + other.sum, sumSq + other.sumSq);
    }

    public boolean present() {
        return count > 0;
    }

    /** Mean score, or NaN without scores. */
    public double mean() {
        return count == 0 ? Double.NaN : sum / count;
    }

    /** Sample variance, 0 for fewer than two scores. */
    public double variance() {
        if (count < 2) {
            return 0;
        }
        double v = (sumSq - sum * sum / count) / (count - 1);
        return Math.max(0, v);
    }
}
