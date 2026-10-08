package com.api.inventory.controller;

import com.api.inventory.entity.ItemMaster;
import com.api.inventory.entity.SellerProfile;
import com.api.inventory.repository.ItemMasterRepository;
import com.api.inventory.repository.SellerProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

/**
 * The link people share: /api/share/products/12. WhatsApp, Facebook and the like read this page (they do not run
 * the website's JavaScript), so it carries the product's name, price, description and photo for the preview, then
 * sends a person straight on to the product page. Public; only products a shopper may see.
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ItemMasterRepository items;
    private final SellerProfileRepository sellers;
    private final String site;

    public ShareController(ItemMasterRepository items, SellerProfileRepository sellers,
                           @Value("${app.public-url:http://localhost:4200}") String publicUrl) {
        this.items = items;
        this.sellers = sellers;
        this.site = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @GetMapping(value = "/products/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> product(@PathVariable Long id) {
        Optional<ItemMaster> found = items.findById(id).filter(this::visible);
        if (found.isEmpty()) {
            return page(site + "/products", "DP DrukBazaars", "Online shopping in Bhutan.", site + "/icons/icon-512.png");
        }
        ItemMaster item = found.get();
        String title = item.getItemName() + (item.getSellingPrice() == null ? "" : " · Nu. " + money(item.getSellingPrice()));
        String seller = item.getSellerId() == null ? "DP DrukBazaars"
                : sellers.findById(item.getSellerId()).map(SellerProfile::getShopName).orElse("DP DrukBazaars");
        String about = item.getDescription() == null || item.getDescription().isBlank()
                ? "Sold by " + seller + ". Order online from DP DrukBazaars and follow it to your door."
                : shorten(item.getDescription(), 200);
        String photo = item.getImagePath() != null && item.getImagePath().startsWith("/")
                ? site + item.getImagePath() : site + "/icons/icon-512.png";
        return page(site + "/products/" + item.getItemId(), title, about, photo);
    }

    /** What a shopper may see: our own products, and switched-on products of approved sellers. */
    private boolean visible(ItemMaster item) {
        if (Boolean.FALSE.equals(item.getIsActive())) {
            return false;
        }
        return item.getSellerId() == null || sellers.findById(item.getSellerId()).map(SellerProfile::isApproved).orElse(false);
    }

    private ResponseEntity<String> page(String url, String title, String about, String photo) {
        String u = html(url), t = html(title), a = html(about), p = html(photo);
        String body = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>%s</title>
                <meta name="description" content="%s">
                <meta property="og:type" content="product">
                <meta property="og:site_name" content="DP DrukBazaars">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:url" content="%s">
                <meta name="twitter:card" content="summary_large_image">
                <link rel="canonical" href="%s">
                <meta http-equiv="refresh" content="0; url=%s">
                </head><body><p><a href="%s">Open %s on DP DrukBazaars</a></p></body></html>
                """.formatted(t, a, t, a, p, u, u, u, u, t);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(body);
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String shorten(String text, int max) {
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max - 1).trim() + "…";
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
