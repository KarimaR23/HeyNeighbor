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

@Controller
public class CommunityListingController {
    private final CommunityListingRepository listings;

    public CommunityListingController(CommunityListingRepository listings) {
        this.listings = listings;
    }

    @GetMapping("/community/listings")
    public String browse(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "category", required = false) Integer category,
                         @RequestParam(name = "radius", required = false) Integer radius,
                         HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);
        String query = q == null ? "" : q.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        Integer safeRadius = radius != null && List.of(5, 10, 25, 50).contains(radius) ? radius : null;
        var categories = listings.categories();
        Integer safeCategory = category != null && categories.stream()
                .anyMatch(c -> category.equals(((Number) c.get("category_id")).intValue())) ? category : null;

        model.addAttribute("pageTitle", "Browse Listings");
        model.addAttribute("categories", categories);
        model.addAttribute("q", query);
        model.addAttribute("selectedCategory", safeCategory);
        model.addAttribute("selectedRadius", safeRadius);
        model.addAttribute("radiusOptions", List.of(5, 10, 25, 50));
        model.addAttribute("locationMissing", safeRadius != null && !listings.hasLocation(user.userId()));
        model.addAttribute("rows", listings.browse(user.userId(), query, safeCategory, safeRadius));
        return "CommunityListings";
    }
}
