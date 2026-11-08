package dev.ldv.mockleak;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GreetingSource {

    public String greeting() {
        return "real greeting";
    }
}
