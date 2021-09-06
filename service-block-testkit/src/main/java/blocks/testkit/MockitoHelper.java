package blocks.testkit;

import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.verification.VerificationMode;

public final class MockitoHelper {

    public static VerificationMode exactlyOnce() {
        return VerificationModeFactory.times(1);
    }

    public static VerificationMode exactlyTwice() {
        return VerificationModeFactory.times(2);
    }

    private MockitoHelper() {
    }
}
