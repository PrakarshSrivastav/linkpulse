package com.example.linkpulse.link;

public class AliasTakenException extends RuntimeException{
    public AliasTakenException(String alias){
        super("Alias already taken: "+alias);
    }
}
