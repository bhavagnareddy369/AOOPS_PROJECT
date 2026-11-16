import java.math.BigDecimal;
import java.math.RoundingMode;
public class Case1 {
    public static void main(String[] args) {
        Money price = Money.of("19.99", Currency.EUR);
        Money line = price.times(Quantity.of(3));
        System.out.println("19.99 EUR x 3        = " + line);

        Money discounted = line.minus(Percentage.ofBasisPoints(1500));
        System.out.println("minus 15%            = " + discounted);

        System.out.println("JPY 1500             = " + Money.of("1500", Currency.JPY));
        System.out.println("KWD 1.234            = " + Money.of("1.234", Currency.KWD));
        System.out.println("Equal amounts equal? = "
                + Money.of("10.00", Currency.EUR).equals(Money.of("10", Currency.EUR)));
        attempt("EUR + USD", () -> Money.of("1.00", Currency.EUR).plus(Money.of("1.00", Currency.USD)));
        attempt("negative price", () -> Money.of("-5.00", Currency.EUR));
        attempt("excess precision (JPY 1.5)", () -> Money.of("1.5", Currency.JPY));
        attempt("discount 250%", () -> Percentage.ofBasisPoints(25_000));
        attempt("discount -20%", () -> Percentage.ofBasisPoints(-2_000));
        attempt("quantity 0", () -> Quantity.of(0));
        attempt("quantity -3", () -> Quantity.of(-3));
        attempt("overflow", () -> Money.ofMinor(Long.MAX_VALUE, Currency.USD).times(Quantity.of(2)));
    }

    private static void attempt(String label, Runnable r) {
        try {
            r.run();
            System.out.println(label + " -> UNEXPECTEDLY SUCCEEDED");
        } catch (RuntimeException e) {
            System.out.println(label + " -> rejected: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
    }
}

enum Currency {
    EUR(2), USD(2), JPY(0), KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}



class CurrencyMismatch extends RuntimeException {
    CurrencyMismatch(Currency a, Currency b) {
        super(a + " vs " + b);
    }
}

/* ===================== Money ===================== */

final class Money {
    private final long minor;            // amount in minor units (never negative)
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (currency == null) throw new IllegalArgumentException("currency required");
        if (minor < 0) throw new IllegalArgumentException("money cannot be negative");
        this.minor = minor;
        this.currency = currency;
    }

    /** Build from a decimal string such as "19.99". Rejects negatives and excess precision. */
    static Money of(String decimal, Currency c) {
        if (decimal == null || c == null) throw new IllegalArgumentException("null argument");
        BigDecimal value;
        try {
            value = new BigDecimal(decimal.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a decimal number: " + decimal);
        }
        if (value.signum() < 0) throw new IllegalArgumentException("negative amount: " + decimal);

        // "10.500" is fine for EUR (same as 10.50); "10.505" is not.
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > c.minorUnits) {
            throw new IllegalArgumentException(
                    "too many decimal places for " + c + " (max " + c.minorUnits + "): " + decimal);
        }
        try {
            long minor = value.movePointRight(c.minorUnits).longValueExact();
            return new Money(minor, c);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("amount out of range: " + decimal);
        }
    }

    /** Build directly from minor units (cents, yen, fils...). */
    static Money ofMinor(long minor, Currency c) {
        return new Money(minor, c);
    }

    static Money zero(Currency c) {
        return new Money(0, c);
    }

    Money plus(Money o) {
        requireSame(o);
        return new Money(Math.addExact(minor, o.minor), currency);
    }

    Money times(Quantity q) {
        return new Money(Math.multiplyExact(minor, (long) q.value()), currency);
    }

    /** Applies a discount. Single documented rounding rule: HALF_EVEN. Result is always >= 0. */
    Money minus(Percentage p) {
        BigDecimal remainingBp = BigDecimal.valueOf(Percentage.MAX_BP - p.basisPoints());
        long result = BigDecimal.valueOf(minor)
                .multiply(remainingBp)
                .divide(BigDecimal.valueOf(Percentage.MAX_BP), 0, RoundingMode.HALF_EVEN)
                .longValueExact();
        return new Money(result, currency);
    }

    int compareTo(Money o) {
        requireSame(o);
        return Long.compare(minor, o.minor);
    }

    long minor() { return minor; }

    Currency currency() { return currency; }

    private void requireSame(Money o) {
        if (currency != o.currency) throw new CurrencyMismatch(currency, o.currency);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Money)) return false;
        Money m = (Money) obj;
        return minor == m.minor && currency == m.currency;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(minor) + currency.hashCode();
    }

    @Override
    public String toString() {
        BigDecimal amount = BigDecimal.valueOf(minor, currency.minorUnits);
        return amount.toPlainString() + " " + currency;
    }
}
final class Percentage {
    static final int MAX_BP = 10_000;

    private final int basisPoints;       // 0..10000  (100 bp = 1%)

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    static Percentage ofBasisPoints(int bp) {
        if (bp < 0 || bp > MAX_BP) {
            throw new IllegalArgumentException("basis points must be 0..10000, got " + bp);
        }
        return new Percentage(bp);
    }

    int basisPoints() { return basisPoints; }
    Money applyTo(Money m) {
        return m.minus(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Percentage)) return false;
        return basisPoints == ((Percentage) obj).basisPoints;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(basisPoints);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(basisPoints, 2).toPlainString() + "%";
    }
}
final class Quantity {
    private final int value;             // >= 1

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int v) {
        if (v < 1) throw new IllegalArgumentException("quantity must be >= 1, got " + v);
        return new Quantity(v);
    }

    int value() { return value; }

    Quantity plus(Quantity o) {
        return new Quantity(Math.addExact(value, o.value));
    }
    Quantity minus(Quantity o) {
        return Quantity.of(value - o.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Quantity)) return false;
        return value == ((Quantity) obj).value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}