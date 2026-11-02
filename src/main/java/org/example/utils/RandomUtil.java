package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

public class RandomUtil {

    private static final Random RANDOM = new Random();

    private RandomUtil() {
    }

    public static int nextInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    public static double nextDouble(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }

    public static BigDecimal nextBigDecimal(double min, double max, int scale) {
        double val = nextDouble(min, max);
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP);
    }

    public static <T> T pickOne(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("列表不能为空");
        }
        return list.get(RANDOM.nextInt(list.size()));
    }

    public static <T> T pickOne(T[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("数组不能为空");
        }
        return arr[RANDOM.nextInt(arr.length)];
    }
}