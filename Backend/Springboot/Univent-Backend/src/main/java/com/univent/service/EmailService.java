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
        SimpleMailMessage message = new SimpleMailMessage();
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
    }
}