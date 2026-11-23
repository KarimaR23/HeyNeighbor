
package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.PartnerListingRepository;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;

/** Community Partner pages for creating organization listings. */
@Controller
public class PartnerListingController {

    private final PartnerListingRepository listings;

    public PartnerListingController(PartnerListingRepository listings) {
        this.listings = listings;
    }


    /** Display listings belonging to the signed-in partner's organization. */
    @GetMapping("/partner/listings")
    public String ourListings(HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);

        if (user == null || !"community_partner".equals(user.roleName())) {
            return "error/403";
        }

        var organizations = listings.approvedOrganization(user.userId());

        model.addAttribute("pageTitle", "Our Listings");
        model.addAttribute("active", "listings");
        model.addAttribute("organization",
                organizations.isEmpty() ? null : organizations.get(0));
        model.addAttribute("listings",
                listings.listingsForOwner(user.userId()));

        return "PartnerListings";
    }


    /** Display the Community Partner listing form. */
    @GetMapping("/partner/listings/new")
    public String newListing(HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);

        if (user == null || !"community_partner".equals(user.roleName())) {
            return "error/403";
        }

        var organizations = listings.approvedOrganization(user.userId());

        model.addAttribute("pageTitle", "Create Organization Listing");
        model.addAttribute("active", "listings");
        model.addAttribute("categories",
                ListingStyle.decorateCategories(listings.categories()));
        model.addAttribute("fulfillmentMethods", listings.fulfillmentMethods());
        model.addAttribute("organization",
                organizations.isEmpty() ? null : organizations.get(0));

        return "PartnerCreateListing";
    }

    /** Validate and publish a Community Partner listing. */
    @PostMapping("/partner/listings/new")
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
            HttpServletRequest request,
            RedirectAttributes redirect) {

        SessionUser user = CurrentUser.get(request);

        if (user == null || !"community_partner".equals(user.roleName())) {
            return "error/403";
        }

        // Only approved organizations can publish listings.
        if (listings.approvedOrganization(user.userId()).isEmpty()) {
            redirect.addFlashAttribute("error",
                    "Your organization must be approved before posting.");
            return "redirect:/partner/listings/new";
        }

        // Check required text fields.
        if (title == null || title.isBlank()
                || description == null || description.isBlank()
                || title.length() > 150) {
            redirect.addFlashAttribute("error",
                    "Please enter a valid title and description.");
            return "redirect:/partner/listings/new";
        }

        // Validate category and fulfillment method.
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
            return "redirect:/partner/listings/new";
        }

        // Validate listing type and offering kind.
        if (!List.of("gift", "trade").contains(listingType)
                || !List.of("good", "service").contains(offeringKind)) {
            redirect.addFlashAttribute("error",
                    "Please select a valid listing type.");
            return "redirect:/partner/listings/new";
        }

        // Trades must describe what is wanted in return.
        if ("trade".equals(listingType)
                && (tradeWanted == null || tradeWanted.isBlank())) {
            redirect.addFlashAttribute("error",
                    "Please describe what you want in return.");
            return "redirect:/partner/listings/new";
        }

        if (quantityAvailable != null && quantityAvailable < 1) {
            redirect.addFlashAttribute("error",
                    "Quantity must be at least 1.");
            return "redirect:/partner/listings/new";
        }

        // Check the partner's saved location.
        if (!listings.hasLocation(user.userId())) {
            redirect.addFlashAttribute("error",
                    "Your account needs a saved location before posting.");
            return "redirect:/partner/listings/new";
        }

        // Publish the listing under the approved organization.
        listings.createListing(
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

        redirect.addFlashAttribute("success",
                "Your organization listing was published successfully!");

        return "redirect:/partner/listings";
    }
}
