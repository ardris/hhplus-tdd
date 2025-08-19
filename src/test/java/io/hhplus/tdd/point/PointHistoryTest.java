package io.hhplus.tdd.point;

public record PointHistoryTest(
        long id,
        long userId,
        long amount,
        TransactionTypeTest type,
        long updateMillis
) {
}
