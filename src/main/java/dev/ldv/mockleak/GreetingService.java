package dev.ldv.mockleak;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GreetingService {

    private final GreetingSource greetingSource;

    public GreetingService(GreetingSource greetingSource) {
        this.greetingSource = greetingSource;
    }

    public String greet() {
        return greetingSource.greeting();
    }
}
