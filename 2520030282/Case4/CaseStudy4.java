
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

// 1. MONEY
final class Money {
    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        if (amount.signum() < 0)
            throw new IllegalArgumentException("Money cannot be negative");

        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    static Money zero() {
        return Money.of("0.00");
    }

    Money times(long quantity) {
        return new Money(
            amount.multiply(BigDecimal.valueOf(quantity))
        );
    }

    Money percentage(int percent) {
        if (percent < 0 || percent > 100)
            throw new IllegalArgumentException("Invalid percentage");

        BigDecimal result = amount
            .multiply(BigDecimal.valueOf(percent))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new Money(result);
    }

    public String toString() {
        return "INR " + amount.toPlainString();
    }
}

// 2. DATE RANGE
final class DateRange {
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    DateRange(LocalDate checkIn, LocalDate checkOut) {
        this.checkIn = Objects.requireNonNull(checkIn);
        this.checkOut = Objects.requireNonNull(checkOut);

        if (!checkIn.isBefore(checkOut)) {
            throw new IllegalArgumentException(
                "Check-in must be before check-out"
            );
        }
    }

    LocalDate getCheckIn() {
        return checkIn;
    }

    LocalDate getCheckOut() {
        return checkOut;
    }

    long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    boolean overlaps(DateRange other) {
        return checkIn.isBefore(other.checkOut)
            && other.checkIn.isBefore(checkOut);
    }

    public String toString() {
        return checkIn + " to " + checkOut +
            " (" + nights() + " nights)";
    }
}

// 3. ROOM ID
final class RoomId {
    private final String value;

    RoomId(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid Room ID");

        this.value = value;
    }

    public boolean equals(Object obj) {
        return obj instanceof RoomId &&
            value.equals(((RoomId) obj).value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    public String toString() {
        return value;
    }
}

// 4. RESERVATION ID
final class ReservationId {
    private final String value;

    ReservationId(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Invalid Reservation ID");

        this.value = value;
    }

    public boolean equals(Object obj) {
        return obj instanceof ReservationId &&
            value.equals(((ReservationId) obj).value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    public String toString() {
        return value;
    }
}

// 5. GUEST
final class Guest {
    private final String guestId;
    private final String name;

    Guest(String guestId, String name) {
        if (guestId == null || guestId.isBlank()
                || name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid guest details");

        this.guestId = guestId;
        this.name = name;
    }

    String getName() {
        return name;
    }

    String getGuestId() {
        return guestId;
    }
}

// 6. ROOM
final class Room {
    private final RoomId id;
    private final String roomType;
    private final Money nightlyRate;

    Room(RoomId id, String roomType, Money nightlyRate) {
        this.id = Objects.requireNonNull(id);
        this.roomType = Objects.requireNonNull(roomType);
        this.nightlyRate = Objects.requireNonNull(nightlyRate);
    }

    RoomId getId() {
        return id;
    }

    String getRoomType() {
        return roomType;
    }

    Money getNightlyRate() {
        return nightlyRate;
    }
}

// 7. PAYMENT GUARANTEE
final class PaymentGuarantee {
    private final String reference;

    PaymentGuarantee(String reference) {
        if (reference == null || reference.isBlank())
            throw new IllegalArgumentException("Payment guarantee required");

        this.reference = reference;
    }

    public String toString() {
        return reference;
    }
}

// 8. RESERVATION STATUS
enum ReservationStatus {
    REQUESTED,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED
}

// 9. CANCELLATION POLICY
final class CancellationPolicy {
    private final int fullRefundDays;
    private final int partialRefundDays;
    private final int partialRefundPercent;

    CancellationPolicy(int fullRefundDays, int partialRefundDays,
                       int partialRefundPercent) {

        if (fullRefundDays < partialRefundDays ||
            partialRefundDays < 0 ||
            partialRefundPercent < 0 ||
            partialRefundPercent > 100)
            throw new IllegalArgumentException("Invalid cancellation policy");

        this.fullRefundDays = fullRefundDays;
        this.partialRefundDays = partialRefundDays;
        this.partialRefundPercent = partialRefundPercent;
    }

    Money calculateRefund(Money total, long daysBeforeCheckIn) {
        if (daysBeforeCheckIn >= fullRefundDays)
            return total.percentage(100);

        if (daysBeforeCheckIn >= partialRefundDays)
            return total.percentage(partialRefundPercent);

        return Money.zero();
    }
}

// 10. RESERVATION
final class Reservation {
    private final ReservationId id;
    private final Guest guest;
    private final Room room;
    private final DateRange stay;
    private final Money totalPrice;

    private ReservationStatus status = ReservationStatus.REQUESTED;

    private Optional<PaymentGuarantee> guarantee =
        Optional.empty();

    private LocalDate checkedInAt;

    Reservation(ReservationId id, Guest guest,
                Room room, DateRange stay) {

        this.id = id;
        this.guest = guest;
        this.room = room;
        this.stay = stay;

        this.totalPrice = room.getNightlyRate().times(stay.nights());
    }

    void confirm(PaymentGuarantee paymentGuarantee) {
        requireStatus(ReservationStatus.REQUESTED);

        guarantee = Optional.of(
            Objects.requireNonNull(paymentGuarantee)
        );

        status = ReservationStatus.CONFIRMED;
    }

    void checkIn(LocalDate actualDate) {
        requireStatus(ReservationStatus.CONFIRMED);

        if (actualDate.isBefore(stay.getCheckIn()) ||
            !actualDate.isBefore(stay.getCheckOut()))
            throw new IllegalStateException("Invalid check-in date");

        checkedInAt = actualDate;
        status = ReservationStatus.CHECKED_IN;
    }

    void checkOut(LocalDate actualDate) {
        requireStatus(ReservationStatus.CHECKED_IN);

        if (actualDate.isBefore(checkedInAt))
            throw new IllegalArgumentException(
                "Check-out cannot be before actual check-in"
            );

        status = ReservationStatus.COMPLETED;
    }

    Money cancel(LocalDate cancellationDate,
                 CancellationPolicy policy) {

        if (status != ReservationStatus.REQUESTED &&
            status != ReservationStatus.CONFIRMED)
            throw new IllegalStateException(
                "Only requested or confirmed reservations can be cancelled"
            );

        Objects.requireNonNull(cancellationDate);
        Objects.requireNonNull(policy);

        Money refund = status == ReservationStatus.CONFIRMED
            ? policy.calculateRefund(
                totalPrice,
                ChronoUnit.DAYS.between(
                    cancellationDate,
                    stay.getCheckIn()
                )
            )
            : Money.zero();

        status = ReservationStatus.CANCELLED;

        return refund;
    }

    private void requireStatus(ReservationStatus required) {
        if (status != required)
            throw new IllegalStateException(
                "Required status: " + required +
                ", Current status: " + status
            );
    }

    ReservationId getId() {
        return id;
    }

    Guest getGuest() {
        return guest;
    }

    Room getRoom() {
        return room;
    }

    DateRange getStay() {
        return stay;
    }

    Money getTotalPrice() {
        return totalPrice;
    }

    ReservationStatus getStatus() {
        return status;
    }

    boolean blocksRoom() {
        return status == ReservationStatus.CONFIRMED ||
               status == ReservationStatus.CHECKED_IN;
    }

    boolean hasPaymentGuarantee() {
        return guarantee.isPresent();
    }
}

// 11. HOTEL
final class Hotel {
    private final Map<RoomId, Room> rooms =
        new LinkedHashMap<>();

    private final Map<ReservationId, Reservation> reservations =
        new LinkedHashMap<>();

    void addRoom(Room room) {
        rooms.put(room.getId(), room);
    }

    synchronized Reservation requestReservation(
            ReservationId id,
            String guestId,
            String guestName,
            RoomId roomId,
            DateRange stay) {

        Room room = rooms.get(roomId);

        if (room == null)
            throw new IllegalArgumentException("Room does not exist");

        if (reservations.containsKey(id))
            throw new IllegalArgumentException("Reservation ID already exists");

        Reservation reservation = new Reservation(
            id,
            new Guest(guestId, guestName),
            room,
            stay
        );

        reservations.put(id, reservation);

        return reservation;
    }

    // Check availability and confirm atomically.
    synchronized void confirmReservation(
            ReservationId reservationId,
            PaymentGuarantee guarantee) {

        Reservation reservation = reservations.get(reservationId);

        if (reservation == null)
            throw new IllegalArgumentException("Reservation not found");

        for (Reservation existing : reservations.values()) {

            if (!existing.getId().equals(reservationId) &&
                existing.blocksRoom() &&
                existing.getRoom().getId().equals(
                    reservation.getRoom().getId()
                ) &&
                existing.getStay().overlaps(reservation.getStay())) {

                throw new IllegalStateException(
                    "Room is already booked for overlapping dates"
                );
            }
        }

        reservation.confirm(guarantee);
    }

    synchronized void cancelReservation(
            ReservationId reservationId,
            LocalDate cancellationDate,
            CancellationPolicy policy) {

        Reservation reservation = reservations.get(reservationId);

        if (reservation == null)
            throw new IllegalArgumentException("Reservation not found");

        Money refund = reservation.cancel(cancellationDate, policy);

        System.out.println(
            "Refund for " + reservationId + ": " + refund
        );
    }
}

// 12. MAIN PROGRAM
public class CaseStudy4 {

    public static void main(String[] args) {

        System.out.println("===== HOTEL RESERVATION SYSTEM =====");

        Hotel hotel = new Hotel();

        RoomId room101 = new RoomId("ROOM-101");

        hotel.addRoom(
            new Room(
                room101,
                "Deluxe",
                Money.of("2500.00")
            )
        );

        Guest guest1 = new Guest("G001", "Aarav");

        System.out.println("Guest: " + guest1.getName());

        // Create stay period
        DateRange stay1 = new DateRange(
            LocalDate.of(2026, 12, 20),
            LocalDate.of(2026, 12, 23)
        );

        System.out.println("Stay: " + stay1);

        // Create reservation
        ReservationId booking1 = new ReservationId("RES-001");

        Reservation reservation1 = hotel.requestReservation(
            booking1,
            guest1.getGuestId(),
            guest1.getName(),
            room101,
            stay1
        );

        System.out.println("Initial status: " + reservation1.getStatus());

        // Confirm reservation with payment guarantee
        hotel.confirmReservation(
            booking1,
            new PaymentGuarantee("PAY-GUARANTEE-001")
        );

        System.out.println("After confirmation: " + reservation1.getStatus());

        System.out.println(
            "Payment guarantee present: " +
            reservation1.hasPaymentGuarantee()
        );

        System.out.println("Total price: " + reservation1.getTotalPrice());

        // Test overlapping booking
        try {
            DateRange overlappingStay = new DateRange(
                LocalDate.of(2026, 12, 22),
                LocalDate.of(2026, 12, 25)
            );

            ReservationId booking2 = new ReservationId("RES-002");

            hotel.requestReservation(
                booking2, "G002", "Diya",
                room101, overlappingStay
            );

            hotel.confirmReservation(
                booking2,
                new PaymentGuarantee("PAY-GUARANTEE-002")
            );

        } catch (IllegalStateException e) {
            System.out.println(
                "Overlapping booking rejected: " + e.getMessage()
            );
        }

        // Test consecutive booking
        DateRange consecutiveStay = new DateRange(
            LocalDate.of(2026, 12, 23),
            LocalDate.of(2026, 12, 26)
        );

        ReservationId booking3 = new ReservationId("RES-003");

        Reservation reservation3 = hotel.requestReservation(
            booking3, "G003", "Ishita",
            room101, consecutiveStay
        );

        hotel.confirmReservation(
            booking3,
            new PaymentGuarantee("PAY-GUARANTEE-003")
        );

        System.out.println(
            "Consecutive booking confirmed: " +
            reservation3.getStatus()
        );

        // Check in
        reservation1.checkIn(LocalDate.of(2026, 12, 20));

        System.out.println(
            "After check-in: " + reservation1.getStatus()
        );

        // Try cancelling after check-in
        try {
            reservation1.cancel(
                LocalDate.of(2026, 12, 20),
                new CancellationPolicy(7, 2, 50)
            );

        } catch (IllegalStateException e) {
            System.out.println(
                "Cancellation after check-in rejected: " +
                e.getMessage()
            );
        }

        // Check out
        reservation1.checkOut(LocalDate.of(2026, 12, 23));

        System.out.println(
            "After check-out: " + reservation1.getStatus()
        );

        // Test cancellation and refund
        DateRange laterStay = new DateRange(
            LocalDate.of(2027, 1, 10),
            LocalDate.of(2027, 1, 12)
        );

        ReservationId booking4 = new ReservationId("RES-004");

        hotel.requestReservation(
            booking4, "G004", "Mira",
            room101, laterStay
        );

        hotel.confirmReservation(
            booking4,
            new PaymentGuarantee("PAY-GUARANTEE-004")
        );

        CancellationPolicy policy =
            new CancellationPolicy(7, 2, 50);

        hotel.cancelReservation(
            booking4,
            LocalDate.of(2027, 1, 1),
            policy
        );

        // Test invalid date range
        try {
            new DateRange(
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 24)
            );

        } catch (IllegalArgumentException e) {
            System.out.println(
                "Invalid date range rejected: " + e.getMessage()
            );
        }
    }
}