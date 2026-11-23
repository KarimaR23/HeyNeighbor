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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hn.heyneighbor.service.ListingImageService;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;






/** Community User pages: the home page and Browse Listings. Both need listing.browse (AccessRules "/community"). */
@Controller
public class CommunityListingController {
    private static final List<Integer> RADIUS_OPTIONS = List.of(5, 10, 25, 50);
    private static final int ROW_SIZE = 8;

    private final CommunityListingRepository listings;
    private final ListingImageService imageService;

    public CommunityListingController(
            CommunityListingRepository listings,
            ListingImageService imageService) {
        this.listings = listings;
        this.imageService = imageService;
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

    /** Display the form for creating a new Community User listing. */
    @GetMapping("/community/listings/new")
    public String newListing(Model model) {
        model.addAttribute("pageTitle", "Create Listing");
        model.addAttribute("active", "create");
        model.addAttribute("categories",
                ListingStyle.decorateCategories(listings.categories()));
        model.addAttribute("fulfillmentMethods",
                listings.fulfillmentMethods());

        return "CommunityCreateListing";
    }

    /** Validate and publish a Community User listing. */
    @PostMapping("/community/listings/new")
    public String createListing(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer fulfillmentMethodId,
            @RequestParam String listingType,
            @RequestParam String offeringKind,
            @RequestParam(required = false) String tradeWanted,
            @RequestParam(required = false) Integer quantityAvailable,
            @RequestParam(required = false) String pickupDropoffNotes,
            @RequestParam(name = "listingImage", required = false) MultipartFile listingImage,
            HttpServletRequest request,
            RedirectAttributes redirect) {

        SessionUser user = Objects.requireNonNull(CurrentUser.get(request));

        // Check required text fields.
        if (title == null || title.isBlank()
                || description == null || description.isBlank()
                || title.length() > 150) {
            redirect.addFlashAttribute("error",
                    "Please enter a valid title and description.");
            return "redirect:/community/listings/new";
        }

        // Check that the selected category is active.
        boolean validCategory = categoryId != null
                && listings.categories().stream()
                .anyMatch(c -> categoryId.equals(
                        ((Number) c.get("category_id")).intValue()));

        boolean validMethod = fulfillmentMethodId != null
                && listings.fulfillmentMethods().stream()
                .anyMatch(m -> fulfillmentMethodId.equals(
                        ((Number) m.get("method_id")).intValue()));

        if (!validCategory || !validMethod) {
            redirect.addFlashAttribute("error",
                    "Please select a valid category and fulfillment method.");
            return "redirect:/community/listings/new";
        }

        // Only gifts and trades are allowed.
        if (!List.of("gift", "trade").contains(listingType)
                || !List.of("good", "service").contains(offeringKind)) {
            redirect.addFlashAttribute("error",
                    "Please select a valid listing type.");
            return "redirect:/community/listings/new";
        }

        // A trade must explain what the user wants in return.
        if ("trade".equals(listingType)
                && (tradeWanted == null || tradeWanted.isBlank())) {
            redirect.addFlashAttribute("error",
                    "Please describe what you want in return for the trade.");
            return "redirect:/community/listings/new";
        }

        // Quantity must be positive when provided.
        if (quantityAvailable != null && quantityAvailable < 1) {
            redirect.addFlashAttribute("error",
                    "Quantity must be at least 1.");
            return "redirect:/community/listings/new";
        }

        // Listings require the user's saved location.
        if (!listings.hasLocation(user.userId())) {
            redirect.addFlashAttribute("error",
                    "Your account needs a saved location before posting.");
            return "redirect:/community/listings/new";
        }

        // Save the listing in Neon.
        int listingId = listings.createListing(
                user.userId(),
                categoryId,
                fulfillmentMethodId,
                title.trim(),
                description.trim(),
                listingType,
                offeringKind,
                "trade".equals(listingType) ? tradeWanted.trim() : null,
                quantityAvailable,
                pickupDropoffNotes
        );

        // Save the optional picture and attach it to the listing.
        if (listingImage != null && !listingImage.isEmpty()) {
            try {
                String filename = imageService.saveImage(listingImage);
                int updated = listings.updateListingImage(user.userId(), listingId, filename);

                if (updated != 1) {
                    throw new IOException("Could not attach the image to the listing.");
                }
            } catch (IOException e) {
                redirect.addFlashAttribute("error",
                        "Your listing was published, but its picture could not be saved: "
                                + e.getMessage());
                return "redirect:/community/listings";
            }
        }

        redirect.addFlashAttribute("success",
                "Your listing was published successfully!");

        return "redirect:/community/listings";
    }


}
