package me.bounser.nascraft.updatechecker;

/**
 * 版本号比较工具。支持 "26.4.3.1"、"v26.4.3.1" 这类点分数字，缺失的段按 0 处理。
 * 单独成类，避免单元测试加载依赖 Bukkit 的 {@link UpdateChecker}。
 */
public final class UpdateVersion {

    private UpdateVersion() {
    }

    /**
     * 比较两个版本号。
     *
     * @return 正数表示 a 比 b 新，0 表示相同，负数表示 a 比 b 旧
     */
    public static int compare(String versionA, String versionB) {
        long[] a = parse(versionA);
        long[] b = parse(versionB);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            long left = i < a.length ? a[i] : 0L;
            long right = i < b.length ? b[i] : 0L;
            if (left != right) {
                return Long.compare(left, right);
            }
        }
        return 0;
    }

    private static long[] parse(String version) {
        if (version == null) {
            return new long[0];
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new long[0];
        }
        String[] parts = normalized.split("\\.");
        long[] numbers = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = leadingNumber(parts[i]);
        }
        return numbers;
    }

    private static long leadingNumber(String part) {
        long value = 0L;
        boolean found = false;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            found = true;
            value = value * 10 + (c - '0');
            if (value > 1_000_000_000L) {
                return value;
            }
        }
        return found ? value : 0L;
    }
}
