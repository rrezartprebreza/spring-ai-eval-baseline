package io.github.rrezartprebreza.evalbaseline;

/** Small statistics helpers: Welch's t-test and the Student t distribution. */
final class Stats {

    private Stats() {
    }

    /**
     * One-sided Welch t-test: probability of a mean at least this much lower if the true means
     * were equal. Returns NaN when either side has fewer than two scores.
     */
    static double welchLower(ScoreStats base, ScoreStats current) {
        if (base.count() < 2 || current.count() < 2) {
            return Double.NaN;
        }
        double vb = base.variance() / base.count();
        double vc = current.variance() / current.count();
        double se2 = vb + vc;
        double diff = current.mean() - base.mean();
        if (se2 <= 0) {
            return diff < 0 ? 0 : 1;
        }
        double t = diff / Math.sqrt(se2);
        double df = se2 * se2 / (vb * vb / (base.count() - 1) + vc * vc / (current.count() - 1));
        return studentCdf(t, df);
    }

    /** P(T <= t) for a Student t distribution with {@code df} degrees of freedom. */
    static double studentCdf(double t, double df) {
        double x = df / (df + t * t);
        double tail = 0.5 * regularizedBeta(x, df / 2, 0.5);
        return t > 0 ? 1 - tail : tail;
    }

    /** Regularized incomplete beta function I_x(a, b). */
    static double regularizedBeta(double x, double a, double b) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaFraction(x, a, b) / a;
        }
        return 1 - front * betaFraction(1 - x, b, a) / b;
    }

    /** Continued fraction for the incomplete beta function (modified Lentz). */
    private static double betaFraction(double x, double a, double b) {
        final double tiny = 1e-300;
        double c = 1;
        double d = 1 - (a + b) * x / (a + 1);
        d = Math.abs(d) < tiny ? tiny : d;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 300; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2));
            d = 1 + aa * d;
            d = Math.abs(d) < tiny ? tiny : d;
            c = 1 + aa / c;
            c = Math.abs(c) < tiny ? tiny : c;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1));
            d = 1 + aa * d;
            d = Math.abs(d) < tiny ? tiny : d;
            c = 1 + aa / c;
            c = Math.abs(c) < tiny ? tiny : c;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-12) {
                break;
            }
        }
        return h;
    }

    /** Lanczos approximation of ln Γ(x) for x > 0. */
    static double logGamma(double x) {
        double[] g = {676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
                12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        }
        x -= 1;
        double a = 0.99999999999980993;
        double t = x + 7.5;
        for (int i = 0; i < g.length; i++) {
            a += g[i] / (x + i + 1);
        }
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }
}
