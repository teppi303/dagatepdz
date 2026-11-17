package vn.daga;

public enum Rarity {
    THUONG("&f", "Thường", 1.0, 5),
    KHA("&a", "Khá", 1.25, 10),
    HIEM("&9", "Hiếm", 1.55, 20),
    HUYEN_THOAI("&6", "Huyền Thoại", 2.0, 40);

    public final String color;
    public final String label;
    public final double mult;
    public final int hatchMinutes;

    Rarity(String color, String label, double mult, int hatchMinutes) {
        this.color = color;
        this.label = label;
        this.mult = mult;
        this.hatchMinutes = hatchMinutes;
    }

    public String tag() {
        return color + label;
    }

    public Rarity next() {
        return values()[Math.min(ordinal() + 1, values().length - 1)];
    }

    public static Rarity parse(String s) {
        if (s == null) return null;
        String x = s.toLowerCase().replace("-", "").replace("_", "");
        return switch (x) {
            case "thuong" -> THUONG;
            case "kha" -> KHA;
            case "hiem" -> HIEM;
            case "huyenthoai" -> HUYEN_THOAI;
            default -> null;
        };
    }
}
