package iot.example.h2experiments.storage.domain

import org.joda.time.DateTime
import java.beans.ConstructorProperties
import java.math.BigDecimal

data class Line @ConstructorProperties("id", "receipt_id", "title", "quantity", "total_price",
        "price", "vat_rate", "vat_amount", "created_at", "updated_at","payment_case") constructor(
        val id: Long,
        val receiptId: Long,
        val title: String,
        val quantity: BigDecimal,
        val totalPrice: BigDecimal,
        val price: BigDecimal?,
        val vatRate: TaxCode,
        val vatAmount: BigDecimal?,
        val createdAt: DateTime?,
        val updatedAt: DateTime?,
        val paymentCase:Int

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Line

        if (id != other.id) return false
        if (receiptId != other.receiptId) return false
        if (title != other.title) return false
        if (quantity.compareTo(other.quantity) != 0) return false
        if (totalPrice.compareTo(other.totalPrice) != 0) return false

        if (price != null && other.price != null && price.compareTo(other.price) != 0) return false
        if (price == null && other.price != null) return false
        if (price != null && other.price == null) return false

        if (vatAmount != null && other.vatAmount != null && vatAmount.compareTo(other.vatAmount) != 0) return false
        if (vatAmount == null && other.vatAmount != null) return false
        if (vatAmount != null && other.vatAmount == null) return false

        if (vatRate != other.vatRate) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }
}