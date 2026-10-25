package com.example.linkpulse.link;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RandomBase62Generator implements CodeGenerator{
    private static final String ALPHABET="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int LENGTH=7;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        char[] chars=new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i]=ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}
