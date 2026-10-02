package com.api.inventory.dto;
import java.math.BigDecimal;
import java.sql.Date; // ← Use this!

public class SalesReportDTO {
    private Date date; // ← Change from LocalDate to java.sql.Date
    private BigDecimal totalSales;
    private Long totalOrders;
    private BigDecimal totalTax;
    private BigDecimal totalDiscount;

    // Constructor MUST match SELECT NEW (...) exactly
    public SalesReportDTO(Date date,
                          BigDecimal totalSales,
                          Long totalOrders,
                          BigDecimal totalTax,
                          BigDecimal totalDiscount) {
        this.date = date;
        this.totalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
        this.totalOrders = totalOrders != null ? totalOrders : 0L;
        this.totalTax = totalTax != null ? totalTax : BigDecimal.ZERO;
        this.totalDiscount = totalDiscount != null ? totalDiscount : BigDecimal.ZERO;
    }

    // H2 (used by the tests) does not know MySQL's DATE(), so the day arrives untyped. Same result as the constructor above.
    public SalesReportDTO(Object day,
                          BigDecimal totalSales,
                          Long totalOrders,
                          BigDecimal totalTax,
                          BigDecimal totalDiscount) {
        this(toSqlDate(day), totalSales, totalOrders, totalTax, totalDiscount);
    }

    private static Date toSqlDate(Object day) {
        if (day == null || day instanceof Date) return (Date) day;
        if (day instanceof java.time.LocalDate d) return Date.valueOf(d);
        if (day instanceof java.time.LocalDateTime t) return Date.valueOf(t.toLocalDate());
        if (day instanceof java.util.Date d) return new Date(d.getTime());
        return Date.valueOf(day.toString().substring(0, 10));
    }

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public BigDecimal getTotalSales() {
		return totalSales;
	}

	public void setTotalSales(BigDecimal totalSales) {
		this.totalSales = totalSales;
	}

	public Long getTotalOrders() {
		return totalOrders;
	}

	public void setTotalOrders(Long totalOrders) {
		this.totalOrders = totalOrders;
	}

	public BigDecimal getTotalTax() {
		return totalTax;
	}

	public void setTotalTax(BigDecimal totalTax) {
		this.totalTax = totalTax;
	}

	public BigDecimal getTotalDiscount() {
		return totalDiscount;
	}

	public void setTotalDiscount(BigDecimal totalDiscount) {
		this.totalDiscount = totalDiscount;
	}
    
    
}