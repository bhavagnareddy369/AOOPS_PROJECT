
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

// 1. CURRENCY
enum Currency {
    INR(2), USD(2), EUR(2), JPY(0), KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// 2. MONEY
final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0)
            throw new IllegalArgumentException("Negative money not allowed");

        this.minor = minor;
        this.currency = Objects.requireNonNull(currency);
    }

    static Money of(String amount, Currency currency) {
        BigDecimal value = new BigDecimal(amount);

        if (value.signum() < 0)
            throw new IllegalArgumentException("Negative price");

        value = value.setScale(
            currency.minorUnits,
            RoundingMode.UNNECESSARY
        );

        return new Money(
            value.movePointRight(currency.minorUnits).longValueExact(),
            currency
        );
    }

    static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    Money plus(Money other) {
        if (currency != other.currency)
            throw new IllegalArgumentException("Currency mismatch");

        return new Money(
            Math.addExact(minor, other.minor),
            currency
        );
    }

    Money times(Quantity quantity) {
        return new Money(
            Math.multiplyExact(minor, quantity.value()),
            currency
        );
    }

    Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Money)) return false;

        Money other = (Money) obj;

        return minor == other.minor &&
               currency == other.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minor, currency);
    }

    @Override
    public String toString() {
        return currency + " " +
            BigDecimal.valueOf(minor)
                .movePointLeft(currency.minorUnits)
                .toPlainString();
    }
}

// 3. QUANTITY
final class Quantity {
    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int value) {
        if (value < 1)
            throw new IllegalArgumentException(
                "Quantity must be at least 1"
            );

        return new Quantity(value);
    }

    int value() {
        return value;
    }

    Quantity plus(Quantity other) {
        return Quantity.of(Math.addExact(value, other.value));
    }
}

// 4. ORDER ID
final class OrderId {
    private final String value;

    OrderId(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid Order ID");

        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof OrderId &&
            value.equals(((OrderId) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

// 5. PRODUCT ID
final class ProductId {
    private final String value;

    ProductId(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid Product ID");

        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProductId &&
            value.equals(((ProductId) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

// 6. TRACKING NUMBER
final class TrackingNumber {
    private final String value;

    TrackingNumber(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid tracking number");

        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

// 7. PAYMENT REFERENCE
final class PaymentRef {
    private final String value;

    PaymentRef(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid payment reference");

        this.value = value;
    }
}

// 8. SHIPMENT
final class Shipment {
    private final TrackingNumber tracking;
    private final Instant shippedAt;

    Shipment(TrackingNumber tracking, Instant shippedAt) {
        this.tracking = Objects.requireNonNull(tracking);
        this.shippedAt = Objects.requireNonNull(shippedAt);
    }

    TrackingNumber getTracking() {
        return tracking;
    }

    Instant getShippedAt() {
        return shippedAt;
    }
}

// 9. ORDER LINE
final class OrderLine {
    private final ProductId productId;
    private final Money unitPrice;
    private final Quantity quantity;

    OrderLine(ProductId productId, Money unitPrice, Quantity quantity) {
        this.productId = productId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    OrderLine combine(OrderLine other) {
        if (!productId.equals(other.productId))
            throw new IllegalArgumentException("Different products");

        if (!unitPrice.equals(other.unitPrice))
            throw new IllegalArgumentException("Different prices");

        return new OrderLine(
            productId,
            unitPrice,
            quantity.plus(other.quantity)
        );
    }

    Money total() {
        return unitPrice.times(quantity);
    }

    ProductId getProductId() {
        return productId;
    }

    Money getUnitPrice() {
        return unitPrice;
    }

    Quantity getQuantity() {
        return quantity;
    }
}

// 10. ORDER STATUS
enum OrderStatus {
    DRAFT,
    PLACED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

// 11. ORDER CLASS
final class Order {

    private final OrderId id;
    private final Currency currency;

    private OrderStatus status = OrderStatus.DRAFT;

    private final Map<ProductId, OrderLine> lines =
        new LinkedHashMap<>();

    private Optional<Shipment> shipment = Optional.empty();

    private Optional<PaymentRef> payment = Optional.empty();

    Order(OrderId id, Currency currency) {
        this.id = id;
        this.currency = currency;
    }

    // Add products only when order is DRAFT
    void addLine(ProductId productId, Money price, Quantity quantity) {
        requireStatus(OrderStatus.DRAFT);

        if (price.getCurrency() != currency)
            throw new IllegalArgumentException("Currency mismatch");

        OrderLine newLine =
            new OrderLine(productId, price, quantity);

        lines.merge(productId, newLine, OrderLine::combine);
    }

    // Remove products only when order is DRAFT
    void removeLine(ProductId productId) {
        requireStatus(OrderStatus.DRAFT);
        lines.remove(productId);
    }

    // Place order
    void place() {
        requireStatus(OrderStatus.DRAFT);

        if (lines.isEmpty())
            throw new IllegalStateException(
                "Cannot place an empty order"
            );

        status = OrderStatus.PLACED;
    }

    // Pay for order
    void pay(PaymentRef paymentRef) {
        requireStatus(OrderStatus.PLACED);

        payment = Optional.of(
            Objects.requireNonNull(paymentRef)
        );

        status = OrderStatus.PAID;
    }

    // Ship order
    void ship(TrackingNumber tracking, Instant at) {
        requireStatus(OrderStatus.PAID);

        shipment = Optional.of(new Shipment(tracking, at));

        status = OrderStatus.SHIPPED;
    }

    // Deliver order
    void deliver() {
        requireStatus(OrderStatus.SHIPPED);

        status = OrderStatus.DELIVERED;
    }

    // Cancel order
    void cancel(String reason) {
        if (status != OrderStatus.DRAFT &&
            status != OrderStatus.PLACED &&
            status != OrderStatus.PAID) {

            throw new IllegalStateException(
                "Order cannot be cancelled"
            );
        }

        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Reason is required");

        status = OrderStatus.CANCELLED;
    }

    // Calculate total from order lines
    Money total() {
        Money result = Money.zero(currency);

        for (OrderLine line : lines.values()) {
            result = result.plus(line.total());
        }

        return result;
    }

    List<OrderLine> lines() {
        return List.copyOf(lines.values());
    }

    OrderStatus getStatus() {
        return status;
    }

    OrderId getId() {
        return id;
    }

    Optional<Shipment> getShipment() {
        return shipment;
    }

    // Check valid state before performing an operation
    private void requireStatus(OrderStatus required) {
        if (status != required) {
            throw new IllegalStateException(
                "Required: " + required +
                ", Current: " + status
            );
        }
    }
}

// 12. MAIN PROGRAM
public class CaseStudy2 {

    public static void main(String[] args) {

        System.out.println("===== ORDER MANAGEMENT SYSTEM =====");

        // Create order
        Order order = new Order(
            new OrderId("ORD-101"),
            Currency.INR
        );

        // Add products
        order.addLine(
            new ProductId("P101"),
            Money.of("1000.00", Currency.INR),
            Quantity.of(2)
        );

        order.addLine(
            new ProductId("P102"),
            Money.of("500.00", Currency.INR),
            Quantity.of(1)
        );

        // Add same product again
        order.addLine(
            new ProductId("P101"),
            Money.of("1000.00", Currency.INR),
            Quantity.of(1)
        );

        System.out.println("Order ID: " + order.getId());
        System.out.println("Status: " + order.getStatus());

        // Display order lines
        System.out.println("\nOrder Items:");

        for (OrderLine line : order.lines()) {
            System.out.println(
                "Product: " + line.getProductId() +
                " | Quantity: " + line.getQuantity().value() +
                " | Unit Price: " + line.getUnitPrice() +
                " | Total: " + line.total()
            );
        }

        // Calculate total
        System.out.println("\nOrder Total: " + order.total());

        // Complete order lifecycle
        order.place();
        System.out.println("\nAfter placing: " + order.getStatus());

        order.pay(new PaymentRef("PAY-7788"));
        System.out.println("After payment: " + order.getStatus());

        order.ship(
            new TrackingNumber("TRACK-12345"),
            Instant.now()
        );

        System.out.println("After shipping: " + order.getStatus());

        System.out.println(
            "Tracking Number: " +
            order.getShipment().get().getTracking()
        );

        order.deliver();
        System.out.println("After delivery: " + order.getStatus());

        // Test invalid cancellation
        try {
            order.cancel("Customer changed mind");
        } catch (IllegalStateException e) {
            System.out.println(
                "\nCancellation rejected: " + e.getMessage()
            );
        }

        // Test empty order
        Order emptyOrder = new Order(
            new OrderId("ORD-102"),
            Currency.INR
        );

        try {
            emptyOrder.place();
        } catch (IllegalStateException e) {
            System.out.println(
                "Empty order rejected: " + e.getMessage()
            );
        }
    }
}
