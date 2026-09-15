package com.elimara.chatarchive;

import android.app.Activity;
import android.content.Context;

import com.android.billingclient.api.AcknowledgePurchaseParams;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing wrapper for ChatArchive Lifetime Pro.
 *
 * Product ID in Play Console MUST be:
 *     chatarchive_pro_lifetime
 *
 * This is a client-only implementation suitable for the first Play release.
 * For stronger anti-fraud protection at scale, verify purchase tokens with a
 * secure backend using the Google Play Developer API before granting Pro.
 */
public final class PlayBillingManager implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "chatarchive_pro_lifetime";

    public interface Listener {
        void onBillingReady(String localizedPrice, boolean proOwned);
        void onEntitlementChanged(boolean proOwned);
        void onBillingMessage(String message);
    }

    private final Context appContext;
    private final Listener listener;
    private final BillingClient billingClient;

    private ProductDetails productDetails;
    private String selectedOfferToken = "";
    private String localizedPrice = "";

    public PlayBillingManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;

        PendingPurchasesParams pendingParams =
                PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build();

        billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build();
    }

    public void start() {
        if (billingClient.isReady()) {
            refresh();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    refresh();
                } else {
                    emitMessage("Google Play billing is unavailable: "
                            + safeDebug(result));
                    emitReady();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Automatic reconnection is enabled.
            }
        });
    }

    public void refresh() {
        queryOwnedPurchases();
        queryProductDetails();
    }

    public void restorePurchases() {
        if (!billingClient.isReady()) {
            emitMessage("Connecting to Google Play…");
            start();
            return;
        }
        queryOwnedPurchases();
    }

    private void queryProductDetails() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
        );

        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(products)
                        .build();

        billingClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(
                            BillingResult result,
                            QueryProductDetailsResult detailsResult) {

                        if (result.getResponseCode()
                                != BillingClient.BillingResponseCode.OK) {
                            emitMessage("Could not load the Pro product: "
                                    + safeDebug(result));
                            emitReady();
                            return;
                        }

                        List<ProductDetails> details =
                                detailsResult.getProductDetailsList();

                        productDetails = null;
                        selectedOfferToken = "";
                        localizedPrice = "";

                        for (ProductDetails candidate : details) {
                            if (PRODUCT_ID.equals(candidate.getProductId())) {
                                productDetails = candidate;
                                selectOffer(candidate);
                                break;
                            }
                        }

                        if (productDetails == null) {
                            emitMessage(
                                    "ChatArchive Pro is not available for this Play account yet. "
                                            + "Confirm the product is active in Play Console and "
                                            + "install a Play-distributed test build.");
                        }

                        emitReady();
                    }
                }
        );
    }

    private void selectOffer(ProductDetails details) {
        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                details.getOneTimePurchaseOfferDetailsList();

        ProductDetails.OneTimePurchaseOfferDetails selected = null;

        if (offers != null && !offers.isEmpty()) {
            // Prefer a permanent purchase rather than a rental.
            for (ProductDetails.OneTimePurchaseOfferDetails offer : offers) {
                if (offer.getRentalDetails() == null) {
                    selected = offer;
                    break;
                }
            }
            if (selected == null) selected = offers.get(0);
        } else {
            selected = details.getOneTimePurchaseOfferDetails();
        }

        if (selected != null) {
            localizedPrice = selected.getFormattedPrice();
            String token = selected.getOfferToken();
            selectedOfferToken = token == null ? "" : token;
        }
    }

    private void queryOwnedPurchases() {
        QueryPurchasesParams params =
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build();

        billingClient.queryPurchasesAsync(
                params,
                (result, purchases) -> {
                    if (result.getResponseCode()
                            != BillingClient.BillingResponseCode.OK) {
                        emitMessage("Could not restore purchases: "
                                + safeDebug(result));
                        emitReady();
                        return;
                    }

                    boolean owned = false;

                    for (Purchase purchase : purchases) {
                        if (!purchase.getProducts().contains(PRODUCT_ID)) continue;

                        if (purchase.getPurchaseState()
                                == Purchase.PurchaseState.PURCHASED) {
                            owned = true;
                            grantAndAcknowledge(purchase);
                        } else if (purchase.getPurchaseState()
                                == Purchase.PurchaseState.PENDING) {
                            emitMessage(
                                    "Your ChatArchive Pro purchase is pending. "
                                            + "Pro will unlock after Google Play confirms payment.");
                        }
                    }

                    // Only revoke the cached Play entitlement after a successful
                    // Play query proves this account no longer owns the product.
                    ProAccess.setPlayOwned(appContext, owned);
                    emitEntitlement();
                    emitReady();
                }
        );
    }

    public void launchPurchase(Activity activity) {
        if (ProAccess.isPro(appContext)) {
            emitMessage("ChatArchive Lifetime Pro is already active.");
            emitEntitlement();
            return;
        }

        if (!billingClient.isReady()) {
            emitMessage("Connecting to Google Play…");
            start();
            return;
        }

        if (productDetails == null) {
            emitMessage("Loading the Google Play price…");
            queryProductDetails();
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder itemBuilder =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails);

        if (selectedOfferToken != null && !selectedOfferToken.isEmpty()) {
            itemBuilder.setOfferToken(selectedOfferToken);
        }

        BillingFlowParams params =
                BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                                Collections.singletonList(itemBuilder.build()))
                        .build();

        BillingResult launchResult =
                billingClient.launchBillingFlow(activity, params);

        if (launchResult.getResponseCode()
                != BillingClient.BillingResponseCode.OK) {
            emitMessage("Could not open Google Play checkout: "
                    + safeDebug(launchResult));
        }
    }

    @Override
    public void onPurchasesUpdated(
            BillingResult result,
            List<Purchase> purchases) {

        if (result.getResponseCode()
                == BillingClient.BillingResponseCode.OK
                && purchases != null) {

            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(PRODUCT_ID)) continue;

                if (purchase.getPurchaseState()
                        == Purchase.PurchaseState.PURCHASED) {
                    grantAndAcknowledge(purchase);
                    emitMessage("ChatArchive Lifetime Pro unlocked.");
                } else if (purchase.getPurchaseState()
                        == Purchase.PurchaseState.PENDING) {
                    emitMessage(
                            "Payment is pending. Pro will unlock when Google Play confirms it.");
                }
            }

        } else if (result.getResponseCode()
                == BillingClient.BillingResponseCode.USER_CANCELED) {
            emitMessage("Purchase cancelled.");
        } else {
            emitMessage("Google Play purchase error: " + safeDebug(result));
        }
    }

    private void grantAndAcknowledge(Purchase purchase) {
        if (purchase.getPurchaseState()
                != Purchase.PurchaseState.PURCHASED) {
            return;
        }

        // Grant locally after Google Play reports PURCHASED.
        // For stronger fraud protection, move token verification to a backend.
        ProAccess.setPlayOwned(appContext, true);
        emitEntitlement();

        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params =
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();

            billingClient.acknowledgePurchase(
                    params,
                    result -> {
                        if (result.getResponseCode()
                                != BillingClient.BillingResponseCode.OK) {
                            emitMessage(
                                    "Pro is active, but Google Play acknowledgement "
                                            + "will be retried on the next launch.");
                        }
                    }
            );
        }
    }

    public String getLocalizedPrice() {
        return localizedPrice;
    }

    public void end() {
        try {
            if (billingClient.isReady()) {
                billingClient.endConnection();
            }
        } catch (Exception ignored) {
        }
    }

    private void emitReady() {
        if (listener != null) {
            listener.onBillingReady(
                    localizedPrice,
                    ProAccess.isPro(appContext)
            );
        }
    }

    private void emitEntitlement() {
        if (listener != null) {
            listener.onEntitlementChanged(
                    ProAccess.isPro(appContext)
            );
        }
    }

    private void emitMessage(String message) {
        if (listener != null) {
            listener.onBillingMessage(message);
        }
    }

    private String safeDebug(BillingResult result) {
        String message = result == null ? "" : result.getDebugMessage();
        if (message == null || message.trim().isEmpty()) {
            return "response code "
                    + (result == null ? "unknown" : result.getResponseCode());
        }
        return message;
    }
}
