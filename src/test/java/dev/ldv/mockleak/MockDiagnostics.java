package dev.ldv.mockleak;

import org.mockito.MockingDetails;
import org.mockito.Mockito;

final class MockDiagnostics {

    private MockDiagnostics() {
    }

    static void print(String scenario, Object candidate, String observed) {
        MockingDetails details = Mockito.mockingDetails(candidate);
        System.out.printf(
                "[mock-diagnostic] scenario=%s identity=%d mock=%s stubbings=%d observed=%s%n",
                scenario,
                System.identityHashCode(candidate),
                details.isMock(),
                details.getStubbings().size(),
                observed);
    }
}
