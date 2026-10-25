package com.example.linkpulse.link;

public class CodeGenerationException extends RuntimeException{
    public CodeGenerationException(int attempts){
        super("Could not generate a unique code after "+ attempts+" attempts");
    }
}
