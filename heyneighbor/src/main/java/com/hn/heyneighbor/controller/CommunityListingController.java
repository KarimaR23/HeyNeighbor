package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.CommunityListingRepository;
import com.hn.heyneighbor.service.ListingPhotoStorage;
import com.hn.heyneighbor.service.ListingService;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Community User pages: the home page, Browse Listings and Post a Listing.
 * /community needs listing.browse; /community/listings/new needs listing.create (AccessRules).
 */
@Controller
public class CommunityListingController {
    private static final List<Integer> RADIUS_OPTIONS = List.of(5, 10, 25, 50);
    private static final int ROW_SIZE = 8;

    private final CommunityListingRepository listings;
    private final ListingService listingService;

    public CommunityListingController(CommunityListingRepository listings, ListingService listingService) {
        this.listings = listings;
        this.listingService = listingService;
    }

    /** Community User home: Explore, Still interested? and trades, like the Figma home screen. */
    @GetMapping("/community")
    public String home(HttpServletRequest request, Model model) {
        SessionUser user = Objects.requireNonNull(CurrentUser.get(request));

        model.addAttribute("pageTitle", "Home");
        model.addAttribute("active", "home");
        model.addAttribute("categories", ListingStyle.decorateCategories(listings.categories()));
        model.addAttribute("radiusOptions", RADIUS_OPTIONS);
        model.addAttribute("explore", ListingStyle.decorate(listings.newest(user.userId(), ROW_SIZE)));
        model.addAttribute("recent", ListingStyle.decorate(listings.recentlyViewed(user.userId(), ROW_SIZE)));
        model.addAttribute("trades", ListingStyle.decorate(listings.trades(user.userId(), ROW_SIZE)));
        return "CommunityHome";
    }

    /** Post a Listing form (Sprint V: Community User creates a new listing for an item, service, or trade). */
    @GetMapping("/community/listings/new")
    public String newListing(HttpServletRequest request, Model model) {
        Map<String, String> form = new HashMap<>();
        form.put("offeringKind", "good");
        form.put("listingType", "gift");
        return renderForm(request, model, form, null);
    }

    @PostMapping("/community/listings/new")
    public String createListing(@RequestParam(name = "offeringKind", required = false) String offeringKind,
                                @RequestParam(name = "listingType", required = false) String listingType,
                                @RequestParam(name = "title", required = false) String title,
                                @RequestParam(name = "description", required = false) String description,
                                @RequestParam(name = "categoryId", required = false) String categoryId,
                                @RequestParam(name = "tradeWanted", required = false) String tradeWanted,
                                @RequestParam(name = "quantity", required = false) String quantity,
                                @RequestParam(name = "quantityUnit", required = false) String quantityUnit,
                                @RequestParam(name = "fulfillmentMethodId", required = false) String fulfillmentMethodId,
                                @RequestParam(name = "availabilityNotes", required = false) String availabilityNotes,
                                @RequestParam(name = "pickupDropoffNotes", required = false) String pickupDropoffNotes,
                                @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                                HttpServletRequest request, Model model) {
        SessionUser user = Objects.requireNonNull(CurrentUser.get(request));
        ListingService.Form f = new ListingService.Form(
                Options.text(title), Options.text(description), Options.text(categoryId),
                Options.oneOf(listingType, ListingService.TYPES), Options.oneOf(offeringKind, ListingService.KINDS),
                Options.text(tradeWanted), Options.text(quantity), Options.text(quantityUnit),
                Options.text(fulfillmentMethodId), Options.text(availabilityNotes), Options.text(pickupDropoffNotes));

        ListingService.Result result = listingService.create(user.userId(), f, photos);
        if (result.created()) {
            return "redirect:/community/listings?posted=" + result.listingId();
        }

        // show the form again with what they typed; photos must be chosen again (browsers never refill file boxes)
        Map<String, String> form = new HashMap<>();
        put(form, "offeringKind", f.offeringKind());
        put(form, "listingType", f.listingType());
        put(form, "title", f.title());
        put(form, "description", f.description());
        put(form, "categoryId", f.categoryId());
        put(form, "tradeWanted", f.tradeWanted());
        put(form, "quantity", f.quantity());
        put(form, "quantityUnit", f.quantityUnit());
        put(form, "fulfillmentMethodId", f.fulfillmentMethodId());
        put(form, "availabilityNotes", f.availabilityNotes());
        put(form, "pickupDropoffNotes", f.pickupDropoffNotes());
        return renderForm(request, model, form, result.errors());
    }

    private String renderForm(HttpServletRequest request, Model model, Map<String, String> form, List<String> errors) {
        SessionUser user = Objects.requireNonNull(CurrentUser.get(request));
        model.addAttribute("pageTitle", "Post a Listing");
        model.addAttribute("active", "post");
        model.addAttribute("categories", ListingStyle.decorateCategories(listings.categories()));
        model.addAttribute("radiusOptions", RADIUS_OPTIONS);
        model.addAttribute("methods", listings.fulfillmentMethods());
        model.addAttribute("location", listings.profileLocation(user.userId()));
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("maxPhotos", ListingPhotoStorage.MAX_PHOTOS);
        return "CommunityPostListing";
    }

    private static void put(Map<String, String> form, String key, String value) {
        form.put(key, value == null ? "" : value);
    }

    @GetMapping("/community/listings")
    public String browse(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "category", required = false) Integer category,
                         @RequestParam(name = "radius", required = false) Integer radius,
                         @RequestParam(name = "posted", required = false) Long posted,
                         HttpServletRequest request, Model model) {
        SessionUser user = Objects.requireNonNull(CurrentUser.get(request));
        String query = q == null ? "" : q.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        Integer safeRadius = radius != null && RADIUS_OPTIONS.contains(radius) ? radius : null;
        var categories = ListingStyle.decorateCategories(listings.categories());
        Integer safeCategory = category != null && categories.stream()
                .anyMatch(c -> category.equals(((Number) c.get("category_id")).intValue())) ? category : null;
        String categoryName = safeCategory == null ? null : categories.stream()
                .filter(c -> safeCategory.equals(((Number) c.get("category_id")).intValue()))
                .map(c -> (String) c.get("name")).findFirst().orElse(null);

        model.addAttribute("pageTitle", "Browse Listings");
        model.addAttribute("active", "browse");
        model.addAttribute("categories", categories);
        model.addAttribute("q", query);
        model.addAttribute("selectedCategory", safeCategory);
        model.addAttribute("selectedCategoryName", categoryName);
        model.addAttribute("selectedRadius", safeRadius);
        model.addAttribute("radiusOptions", RADIUS_OPTIONS);
        model.addAttribute("locationMissing", safeRadius != null && !listings.hasLocation(user.userId()));
        var rows = ListingStyle.decorate(listings.browse(user.userId(), query, safeCategory, safeRadius));
        model.addAttribute("rows", rows);
        // after Post a Listing: confirm, and only if the new listing really is this user's
        model.addAttribute("posted", posted != null && rows.stream().anyMatch(r ->
                posted.equals(((Number) r.get("listing_id")).longValue())
                        && user.userId() == ((Number) r.get("owner_user_id")).longValue()) ? posted : null);
        return "CommunityListings";
    }
}
