package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.CommunityListingRepository;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Objects;

/** Community User pages: the home page and Browse Listings. Both need listing.browse (AccessRules "/community"). */
@Controller
public class CommunityListingController {
    private static final List<Integer> RADIUS_OPTIONS = List.of(5, 10, 25, 50);
    private static final int ROW_SIZE = 8;

    private final CommunityListingRepository listings;

    public CommunityListingController(CommunityListingRepository listings) {
        this.listings = listings;
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

    @GetMapping("/community/listings")
    public String browse(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "category", required = false) Integer category,
                         @RequestParam(name = "radius", required = false) Integer radius,
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
        model.addAttribute("rows", ListingStyle.decorate(listings.browse(user.userId(), query, safeCategory, safeRadius)));
        return "CommunityListings";
    }
}
