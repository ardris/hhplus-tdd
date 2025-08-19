package io.hhplus.tdd.point;

public record UserPointTest(
        long id,
        long point,
        long updateMillis
) {

    public static UserPointTest empty(long id) {
        return new UserPointTest(id, 0, System.currentTimeMillis());
    }
}
