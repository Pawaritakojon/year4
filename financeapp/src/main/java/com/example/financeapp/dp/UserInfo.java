package com.example.financeapp.dp;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "userinfo")
public class UserInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private Integer age;
    private Double salary;
    private Integer retirementAge;
    private Double desiredRetirementAmount;

    // ✅ ฟิลด์ใหม่
    // ถ้าจะยังไม่เพิ่มคอลัมน์ใน DB ให้เปลี่ยนเป็น @Transient
    @Column(name = "social_security_input", nullable = true)
    private Double socialSecurityInput;   // บาท/เดือน

    @Column(name = "provident_fund_input", nullable = true)
    private Double providentFundInput;    // บาท/เดือน

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
