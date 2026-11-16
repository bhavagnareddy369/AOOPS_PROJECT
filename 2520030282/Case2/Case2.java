import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Case2 {
    public static void main(String[] args) {
        Currency inr = Currency.getInstance("INR");
        ProductId pen = new ProductId("PEN");
        ProductId book = new ProductId("BOOK");

        // Every demo below is REJECTED as written.
        // Change the one line marked  <<<  and run again: it becomes ALLOWED.

        // 1) Invariant 1: cannot place an order with no lines
        Order o1 = new Order(new OrderId("O1"), inr);
        o1.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        o1.removeLine(pen);                                   // <<< COMMENT OUT this line -> allowed
        show("1. place the order", o1::place);

        // 2) Invariant 3: cannot add a line after the order is placed
        Order o2 = new Order(new OrderId("O2"), inr);
        o2.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        o2.place();                                           // <<< COMMENT OUT this line -> allowed
        show("2. add a line to the order", () -> o2.addLine(book, Money.of("250.00", inr), new Quantity(1)));

        // 3) Invariant 4: cannot pay a draft order
        Order o3 = new Order(new OrderId("O3"), inr);
        o3.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        // o3.place();                                        // <<< UNCOMMENT this line -> allowed
        show("3. pay the order", o3::pay);

        // 4) Invariant 4: cannot ship an unpaid order
        Order o4 = new Order(new OrderId("O4"), inr);
        o4.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        o4.place();
        // o4.pay();                                          // <<< UNCOMMENT this line -> allowed
        show("4. ship the order", () -> o4.ship(new TrackingNumber("TRK1"), Instant.now()));

        // 5) Invariant 4: cannot ship a cancelled order
        Order o5 = new Order(new OrderId("O5"), inr);
        o5.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        o5.place();
        o5.pay();
        o5.cancel("changed mind");                            // <<< COMMENT OUT this line -> allowed
        show("5. ship the order", () -> o5.ship(new TrackingNumber("TRK2"), Instant.now()));

        // 6) Invariant 4: cannot cancel a shipped order
        Order o6 = new Order(new OrderId("O6"), inr);
        o6.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        o6.place();
        o6.pay();
        o6.ship(new TrackingNumber("TRK3"), Instant.now());   // <<< COMMENT OUT this line -> allowed
        show("6. cancel the order", () -> o6.cancel("too late"));

        // 7) Invariant 6: same product, different price is not allowed
        Order o7 = new Order(new OrderId("O7"), inr);
        o7.addLine(pen, Money.of("10.00", inr), new Quantity(1));
        show("7. add pen again", () -> o7.addLine(pen, Money.of("99.00", inr), new Quantity(1))); // <<< change 99.00 to 10.00 -> allowed
    }

    static void show(String name, Runnable action) {
        try {
            action.run();
            System.out.println("ALLOWED   " + name);
        } catch (RuntimeException e) {
            System.out.println("REJECTED  " + name + "   (" + e.getMessage() + ")");
        }
    }
}
class DomainException extends RuntimeException {
    DomainException(String message) { super(message); }
}
record OrderId(String value) {
    OrderId {
        if (value == null || value.isBlank()) throw new DomainException("OrderId required");
    }
}

record ProductId(String value) {
    ProductId {
        if (value == null || value.isBlank()) throw new DomainException("ProductId required");
    }
}

record TrackingNumber(String value) {
    TrackingNumber {
        if (value == null || value.isBlank()) throw new DomainException("Tracking number required");
    }
}

record Quantity(int value) {
    Quantity {
        if (value <= 0) throw new DomainException("Quantity must be positive");
    }
    Quantity plus(Quantity other) { return new Quantity(value + other.value); }
}

record Money(BigDecimal amount, Currency currency) {
    Money {
        if (amount == null || currency == null) throw new DomainException("Money needs amount and currency");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }
    static Money zero(Currency c) { return new Money(BigDecimal.ZERO, c); }
    static Money of(String amount, Currency c) { return new Money(new BigDecimal(amount), c); }

    Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }
    Money times(int n) { return new Money(amount.multiply(BigDecimal.valueOf(n)), currency); }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw new DomainException("Currency mismatch");
    }

    @Override public String toString() { return amount + " " + currency.getCurrencyCode(); }
}

record OrderLine(ProductId product, Money unitPrice, Quantity quantity) {
    Money total() { return unitPrice.times(quantity.value()); }

    // Invariant 6: same product -> quantity increases
    OrderLine combine(OrderLine other) {
        if (!product.equals(other.product)) throw new DomainException("Cannot combine different products");
        if (!unitPrice.equals(other.unitPrice)) throw new DomainException("Unit price differs for same product");
        return new OrderLine(product, unitPrice, quantity.plus(other.quantity));
    }
}
record Shipment(TrackingNumber tracking, Instant shippedAt) {}
enum Status { DRAFT, PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }
final class Order {
    private final OrderId id;
    private final Currency currency;
    private Status status = Status.DRAFT;
    private final Map<ProductId, OrderLine> lines = new LinkedHashMap<>();
    private Optional<Shipment> shipment = Optional.empty();
    private Optional<Instant> deliveredAt = Optional.empty();
    private Optional<String> cancelReason = Optional.empty();

    Order(OrderId id, Currency currency) {
        if (id == null || currency == null) throw new DomainException("id and currency required");
        this.id = id;
        this.currency = currency;
    }


    void addLine(ProductId p, Money unitPrice, Quantity q) {
        requireStatus(Status.DRAFT);                         // invariant 3
        requireCurrency(unitPrice);
        lines.merge(p, new OrderLine(p, unitPrice, q), OrderLine::combine); // invariant 6
    }

    void removeLine(ProductId p) {
        requireStatus(Status.DRAFT);                         // invariant 3
        if (lines.remove(p) == null) throw new DomainException("No such line: " + p.value());
    }

    void place() {
        requireStatus(Status.DRAFT);
        if (lines.isEmpty()) throw new DomainException("cannot place an empty order"); // invariant 1
        status = Status.PLACED;
    }

    void pay() {
        requireStatus(Status.PLACED);
        status = Status.PAID;
    }

    void ship(TrackingNumber t, Instant at) {
        requireStatus(Status.PAID);       // a CANCELLED order is never PAID -> cannot ship
        if (t == null || at == null) throw new DomainException("tracking and time required");
        shipment = Optional.of(new Shipment(t, at));
        status = Status.SHIPPED;
    }

    void deliver(Instant at) {
        requireStatus(Status.SHIPPED);
        if (at == null) throw new DomainException("delivery time required");
        deliveredAt = Optional.of(at);
        status = Status.DELIVERED;
    }

    void cancel(String reason) {
        // invariant 4: only DRAFT, PLACED, PAID may be cancelled
        requireStatus(Status.DRAFT, Status.PLACED, Status.PAID);
        cancelReason = Optional.ofNullable(reason);
        status = Status.CANCELLED;
    }

    // ----- Queries -----
    // invariant 2: derived, never stored, so it cannot drift
    Money total() {
        return lines.values().stream()
                .map(OrderLine::total)
                .reduce(Money.zero(currency), Money::plus);
    }

    List<OrderLine> lines() { return List.copyOf(lines.values()); } // defensive copy
    OrderId id() { return id; }
    Status status() { return status; }
    Optional<Shipment> shipment() { return shipment; }
    Optional<Instant> deliveredAt() { return deliveredAt; }
    Optional<String> cancelReason() { return cancelReason; }

    private void requireStatus(Status... allowed) {
        for (Status s : allowed) if (status == s) return;
        throw new DomainException("Illegal operation: order " + id.value() + " is " + status
                + ", expected " + java.util.Arrays.toString(allowed));
    }

    private void requireCurrency(Money m) {
        if (!currency.equals(m.currency())) throw new DomainException("Line currency must be " + currency);
    }

    @Override public String toString() {
        return "Order[" + id.value() + ", " + status + ", total=" + total() + ", lines=" + lines.size() + "]";
    }
}