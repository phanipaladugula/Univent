package com.univent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String to, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("myworkspaceforme@gmail.com");
            message.setTo(to);
            message.setSubject("Univent - Your OTP Code");
            message.setText(String.format(
                    "Welcome to Univent!\n\n" +
                            "Your OTP code is: %s\n\n" +
                            "This code will expire in 10 minutes.\n\n" +
                            "Use this to complete your registration/login.\n\n" +
                            "Thank you,\nUnivent Team",
                    otp
            ));

            mailSender.send(message);
            System.out.println("OTP email sent successfully to: " + to);

        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }
}