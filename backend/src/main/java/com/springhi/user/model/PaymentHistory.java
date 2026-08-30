package com.springhi.user.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history", schema = "springhi")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_name", nullable = false, length = 20)
    private String planName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "billing_cycle", length = 10)
    private String billingCycle;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    // Stripe invoice id when this payment was recorded from a Stripe webhook; used for idempotent
    // handling of Stripe's webhook retries/redelivery (nullable for non-Stripe/manual payments).
    @Column(name = "stripe_invoice_id", length = 64)
    private String stripeInvoiceId;

    @PrePersist
    protected void onCreate() {
        if (paymentDate == null) paymentDate = LocalDateTime.now();
    }

    public PaymentHistory() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public void setStripeInvoiceId(String stripeInvoiceId) { this.stripeInvoiceId = stripeInvoiceId; }
}
