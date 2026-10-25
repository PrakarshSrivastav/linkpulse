package com.example.linkpulse.link;

public class LinkNotFoundException extends RuntimeException{
    public LinkNotFoundException(String code){
        super("No link for code: "+ code);
    }
}
