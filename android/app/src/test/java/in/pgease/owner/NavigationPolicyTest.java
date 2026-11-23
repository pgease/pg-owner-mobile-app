package in.pgease.owner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationPolicyTest {

    private final NavigationPolicy policy = new NavigationPolicy();

    @Test
    public void appHostsStayInTheWebView() {
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://app.pgease.com/dashboard", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://app.pgease.in/login", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("http://localhost:5173/rent", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("http://10.0.2.2:5173/", false));
    }

    @Test
    public void gatewayHostsStayInTheWebView() {
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://pay.easebuzz.in/pay/abc", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://testpay.easebuzz.in/kyc", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://checkout.razorpay.com/v1/checkout", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://app.digio.in/gateway/v2", false));
        assertEquals(
            NavigationPolicy.Decision.IN_APP,
            policy.decide("https://ebz-static.s3.ap-south-1.amazonaws.com/easecheckout/v2.0.0/easebuzz-checkout-v2.min.js", false)
        );
    }

    @Test
    public void externalSchemesLeaveTheWebView() {
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("upi://pay?pa=shop@oksbi&cu=INR", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("tel:+917701953356", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("mailto:hello@pgease.com", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("whatsapp://send?phone=919876543210", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("intent://pay#Intent;scheme=upi;end", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("phonepe://pay", true));
    }

    @Test
    public void whatsAppLinksOpenTheAppEvenDuringPayment() {
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("https://wa.me/919876543210", false));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("https://api.whatsapp.com/send?phone=919876543210", true));
        assertEquals(NavigationPolicy.Decision.EXTERNAL_APP, policy.decide("https://chat.whatsapp.com/invite", false));
    }

    @Test
    public void pdfLinksDownload() {
        assertEquals(NavigationPolicy.Decision.DOWNLOAD, policy.decide("https://app.pgease.com/agreements/rent.pdf", false));
        assertEquals(NavigationPolicy.Decision.DOWNLOAD, policy.decide("https://cdn.example.com/receipt.PDF?token=1", false));
    }

    @Test
    public void otherWebsitesOpenInTheBrowserUnlessAPaymentIsInProgress() {
        assertEquals(NavigationPolicy.Decision.EXTERNAL_BROWSER, policy.decide("https://example.com/help", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("https://acs.bank.example/3ds", true));
    }

    @Test
    public void blobAndAboutStayInside() {
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("blob:https://app.pgease.com/uuid", false));
        assertEquals(NavigationPolicy.Decision.IN_APP, policy.decide("about:blank", false));
    }

    @Test
    public void configuredDevHostIsTreatedAsTheApp() {
        NavigationPolicy dev = new NavigationPolicy("192.168.1.15");
        assertEquals(NavigationPolicy.Decision.IN_APP, dev.decide("http://192.168.1.15:5173/login", false));
        assertTrue(dev.isAppHost("http://192.168.1.15:5173/login"));
        assertFalse(policy.isAppHost("http://192.168.1.15:5173/login"));
    }

    @Test
    public void appFlagIsAddedOnceAndPreservesTheHash() {
        assertEquals(
            "https://app.pgease.com/wa/payments/12?app=1",
            policy.withAppFlag("https://app.pgease.com/wa/payments/12")
        );
        assertEquals(
            "https://app.pgease.com/login?app=1#otp",
            policy.withAppFlag("https://app.pgease.com/login#otp")
        );
        assertEquals(
            "https://app.pgease.com/dashboard?app=1",
            policy.withAppFlag("https://app.pgease.com/dashboard?app=1")
        );
        assertEquals(
            "https://app.pgease.com/dashboard?app=0",
            policy.withAppFlag("https://app.pgease.com/dashboard?app=0")
        );
        assertEquals("https://example.com/docs", policy.withAppFlag("https://example.com/docs"));
    }
}
