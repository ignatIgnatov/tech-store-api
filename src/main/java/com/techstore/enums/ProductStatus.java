package com.techstore.enums;

public enum ProductStatus {
    NOT_AVAILABLE(0, "не е в наличност", "not available"),
    AVAILABLE(1, "в наличност", "available"),
    LIMITED_QUANTITY(2, "ограничено количество", "limited quantity"),
    ON_ROUTE(3, "в път", "on route"),
    ON_DEMAND(4, "по заявка", "on demand");

    private final int code;
    private final String nameBg;
    private final String nameEn;

    ProductStatus(int code, String nameBg, String nameEn) {
        this.code = code;
        this.nameBg = nameBg;
        this.nameEn = nameEn;
    }

    public static ProductStatus fromCode(int code) {
        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }

    public int getCode() { return code; }
    public String getNameBg() { return nameBg; }
    public String getNameEn() { return nameEn; }
}