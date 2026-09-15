package com.tikboost.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements PurchasesUpdatedListener {
    private static final String PREMIUM_PRODUCT_ID = "tikboost_premium_monthly";

    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BillingClient billingClient;
    private ProductDetails premiumProductDetails;
    private String premiumOfferToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(6, 8, 20));
        getWindow().setNavigationBarColor(Color.rgb(6, 8, 20));

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.rgb(6, 8, 20));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(6, 8, 20));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new AndroidBridge(this), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme)) return false;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
        initBilling();
    }

    private void initBilling() {
        PendingPurchasesParams pending = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();

        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(pending)
                .enableAutoServiceReconnection()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryPremiumProduct();
                    queryOwnedSubscriptions(false);
                } else {
                    notifyBillingState("unavailable", billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                notifyBillingState("disconnected", "Google Play Billing desconectado");
            }
        });
    }

    private void queryPremiumProduct() {
        if (billingClient == null || !billingClient.isReady()) return;

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult billingResult, QueryProductDetailsResult result) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || result == null) {
                    notifyBillingState("error", billingResult.getDebugMessage());
                    return;
                }

                List<ProductDetails> details = result.getProductDetailsList();
                if (details == null || details.isEmpty()) {
                    notifyBillingState("not_configured", "Crea la suscripción " + PREMIUM_PRODUCT_ID + " en Play Console");
                    return;
                }

                premiumProductDetails = details.get(0);
                List<ProductDetails.SubscriptionOfferDetails> offers = premiumProductDetails.getSubscriptionOfferDetails();
                if (offers == null || offers.isEmpty()) {
                    notifyBillingState("not_configured", "La suscripción no tiene un plan base disponible");
                    return;
                }

                ProductDetails.SubscriptionOfferDetails selected = offers.get(0);
                premiumOfferToken = selected.getOfferToken();
                String price = "Premium";
                try {
                    List<ProductDetails.PricingPhase> phases = selected.getPricingPhases().getPricingPhaseList();
                    if (phases != null && !phases.isEmpty()) {
                        price = phases.get(phases.size() - 1).getFormattedPrice();
                    }
                } catch (Exception ignored) {}

                try {
                    JSONObject payload = new JSONObject();
                    payload.put("status", "ready");
                    payload.put("productId", PREMIUM_PRODUCT_ID);
                    payload.put("title", premiumProductDetails.getTitle());
                    payload.put("description", premiumProductDetails.getDescription());
                    payload.put("price", price);
                    callJs("window.onTikBoostBillingProduct", payload.toString());
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyBillingState(String status, String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("status", status);
            payload.put("message", message == null ? "" : message);
            callJs("window.onTikBoostBillingProduct", payload.toString());
        } catch (Exception ignored) {}
    }

    private void purchasePremium(String userId) {
        runOnUiThread(() -> {
            if (billingClient == null || !billingClient.isReady()) {
                notifyBillingState("unavailable", "Google Play Billing aún no está listo");
                return;
            }
            if (premiumProductDetails == null || premiumOfferToken == null) {
                queryPremiumProduct();
                notifyBillingState("loading", "Consultando el plan Premium");
                return;
            }

            BillingFlowParams.ProductDetailsParams pd = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(premiumProductDetails)
                    .setOfferToken(premiumOfferToken)
                    .build();

            BillingFlowParams.Builder builder = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(pd));

            if (userId != null && !userId.trim().isEmpty()) {
                builder.setObfuscatedAccountId(sha256(userId.trim()));
            }

            BillingResult result = billingClient.launchBillingFlow(this, builder.build());
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                notifyPurchase("error", "", result.getDebugMessage());
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        int code = billingResult.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) processPurchase(purchase);
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyPurchase("cancelled", "", "Compra cancelada");
        } else {
            notifyPurchase("error", "", billingResult.getDebugMessage());
        }
    }

    private void processPurchase(Purchase purchase) {
        if (purchase == null) return;
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            notifyPurchase("purchased", purchase.getPurchaseToken(), "Compra recibida. Verificando…");
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            notifyPurchase("pending", purchase.getPurchaseToken(), "Pago pendiente");
        }
    }

    private void queryOwnedSubscriptions(boolean manualRestore) {
        if (billingClient == null || !billingClient.isReady()) {
            if (manualRestore) notifyPurchase("error", "", "Google Play Billing no está listo");
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                if (manualRestore) notifyPurchase("error", "", billingResult.getDebugMessage());
                return;
            }
            if (purchases == null || purchases.isEmpty()) {
                if (manualRestore) notifyPurchase("none", "", "No encontramos una suscripción activa en esta cuenta de Google Play");
                return;
            }
            boolean found = false;
            for (Purchase p : purchases) {
                if (p.getProducts().contains(PREMIUM_PRODUCT_ID) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    found = true;
                    notifyPurchase("purchased", p.getPurchaseToken(), manualRestore ? "Restaurando compra…" : "Sincronizando compra…");
                }
            }
            if (manualRestore && !found) notifyPurchase("none", "", "No encontramos una suscripción Premium activa");
        });
    }

    private void notifyPurchase(String status, String token, String message) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("status", status);
            payload.put("purchaseToken", token == null ? "" : token);
            payload.put("productId", PREMIUM_PRODUCT_ID);
            payload.put("message", message == null ? "" : message);
            callJs("window.onTikBoostPurchase", payload.toString());
        } catch (Exception ignored) {}
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (billingClient != null) billingClient.endConnection();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        super.onDestroy();
    }

    private String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        return out.toString();
    }

    private String cleanBase(String baseUrl, boolean httpsOnly) throws Exception {
        String clean = baseUrl == null ? "" : baseUrl.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (httpsOnly && !clean.startsWith("https://")) throw new Exception("La URL debe empezar con https://");
        if (!httpsOnly && !clean.startsWith("https://") && !clean.startsWith("http://")) throw new Exception("URL inválida");
        return clean;
    }

    private String doHttp(String method, String urlText, String body, String accessToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(70000);
            connection.setRequestProperty("Accept", "application/json");
            if (accessToken != null && !accessToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = stream == null ? "" : readAll(stream);
            JSONObject envelope = new JSONObject();
            envelope.put("httpStatus", status);
            envelope.put("body", responseBody);
            return envelope.toString();
        } finally {
            connection.disconnect();
        }
    }

    private void serverRequest(String callback, String method, String url, String body, String token) {
        executor.execute(() -> {
            String result;
            try {
                result = doHttp(method, url, body, token);
            } catch (Exception e) {
                try {
                    JSONObject envelope = new JSONObject();
                    envelope.put("httpStatus", 0);
                    envelope.put("body", "");
                    envelope.put("error", e.getMessage() == null ? "Error de conexión" : e.getMessage());
                    result = envelope.toString();
                } catch (Exception ignored) {
                    result = "{\"httpStatus\":0,\"error\":\"Error de conexión\"}";
                }
            }
            callJs(callback, result);
        });
    }

    private void callJs(String function, String jsonText) {
        final String arg = JSONObject.quote(jsonText == null ? "" : jsonText);
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(function + "(" + arg + ")", null);
        });
    }

    public class AndroidBridge {
        private final Context context;

        AndroidBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("TikBoost", text));
        }

        @JavascriptInterface
        public void shareText(String text) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(share, "Compartir idea de TikBoost");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        }

        @JavascriptInterface
        public void showToast(String text) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void generateIdea(String baseUrl, String payload, String accessToken) {
            try {
                String base = cleanBase(baseUrl, false);
                serverRequest("window.onTikBoostAIResponse", "POST", base + "/api/generate", payload, accessToken);
            } catch (Exception e) {
                notifyJsError("window.onTikBoostAIResponse", e.getMessage());
            }
        }

        @JavascriptInterface
        public void getAccount(String baseUrl, String accessToken) {
            try {
                String base = cleanBase(baseUrl, false);
                serverRequest("window.onTikBoostAccountResponse", "GET", base + "/api/account", null, accessToken);
            } catch (Exception e) {
                notifyJsError("window.onTikBoostAccountResponse", e.getMessage());
            }
        }

        @JavascriptInterface
        public void getConfig(String baseUrl) {
            try {
                String base = cleanBase(baseUrl, false);
                serverRequest("window.onTikBoostConfigResponse", "GET", base + "/api/config", null, null);
            } catch (Exception e) {
                notifyJsError("window.onTikBoostConfigResponse", e.getMessage());
            }
        }

        @JavascriptInterface
        public void verifyPurchase(String baseUrl, String purchaseToken, String productId, String accessToken) {
            try {
                String base = cleanBase(baseUrl, false);
                JSONObject payload = new JSONObject();
                payload.put("purchaseToken", purchaseToken);
                payload.put("productId", productId);
                serverRequest("window.onTikBoostVerifyPurchaseResponse", "POST", base + "/api/billing/verify", payload.toString(), accessToken);
            } catch (Exception e) {
                notifyJsError("window.onTikBoostVerifyPurchaseResponse", e.getMessage());
            }
        }

        @JavascriptInterface
        public void testServer(String baseUrl) {
            try {
                String base = cleanBase(baseUrl, true);
                serverRequest("window.onTikBoostHealthResponse", "GET", base + "/api/health", null, null);
            } catch (Exception e) {
                notifyJsError("window.onTikBoostHealthResponse", e.getMessage());
            }
        }

        @JavascriptInterface
        public void purchasePremium(String userId) {
            MainActivity.this.purchasePremium(userId);
        }

        @JavascriptInterface
        public void queryPremiumPrice() {
            runOnUiThread(() -> queryPremiumProduct());
        }

        @JavascriptInterface
        public void restorePremium() {
            runOnUiThread(() -> queryOwnedSubscriptions(true));
        }

        private void notifyJsError(String callback, String message) {
            try {
                JSONObject envelope = new JSONObject();
                envelope.put("httpStatus", 0);
                envelope.put("body", "");
                envelope.put("error", message == null ? "Error" : message);
                callJs(callback, envelope.toString());
            } catch (Exception ignored) {}
        }
    }
}
