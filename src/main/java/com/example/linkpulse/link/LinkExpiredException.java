package com.example.linkpulse.link;

public class LinkExpiredException extends RuntimeException{
    public LinkExpiredException(String code){
        super("Link has expired: "+code);
    }
}
