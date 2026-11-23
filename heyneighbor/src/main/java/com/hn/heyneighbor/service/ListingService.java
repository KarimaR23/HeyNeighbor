package com.hn.heyneighbor.service;

import com.hn.heyneighbor.repository.CommunityListingRepository;
import com.hn.heyneighbor.repository.CommunityListingRepository.NewListing;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Community User: Create Listing (Sprint V).
 * Checks what was entered on the Post a Listing form, saves any photos and inserts the listing.
 * The listing is placed at the owner's profile location so it shows up in local, distance-based searches.
 */
@Service
public class ListingService {

    public static final List<String> TYPES = List.of("gift", "trade");
    public static final List<String> KINDS = List.of("good", "service");

    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 150;          // listing.title is VARCHAR(150)
    static final int DESCRIPTION_MIN = 10;
    static final int DESCRIPTION_MAX = 2000;
    static final int TRADE_MAX = 500;
    static final int NOTES_MAX = 500;
    static final int UNIT_MAX = 30;            // listing.quantity_unit is VARCHAR(30)
    static final int QUANTITY_MAX = 999;

    /** The form as typed. Text is already trimmed (blank means null); numbers are still text. */
    public record Form(String title, String description, String categoryId, String listingType, String offeringKind,
                       String tradeWanted, String quantity, String quantityUnit, String fulfillmentMethodId,
                       String availabilityNotes, String pickupDropoffNotes) {
    }

    public record Result(long listingId, List<String> errors) {
        public boolean created() { return listingId > 0; }
        public List<String> getErrors() { return errors; }
    }

    private final CommunityListingRepository listings;
    private final ListingPhotoStorage photos;

    public ListingService(CommunityListingRepository listings, ListingPhotoStorage photos) {
        this.listings = listings;
        this.photos = photos;
    }

    public Result create(long userId, Form f, List<MultipartFile> uploaded) {
        List<MultipartFile> chosenPhotos = ListingPhotoStorage.chosen(uploaded);
        List<String> errors = new ArrayList<>();

        if (listings.isBlockedFromListing(userId)) {
            errors.add("A moderator has temporarily restricted your account from posting listings.");
            return new Result(0, errors);
        }

        Map<String, Object> place = listings.profileLocation(userId);
        if (place.get("latitude") == null || place.get("longitude") == null || place.get("postal_code") == null) {
            errors.add("Add your ZIP code to your profile before posting, so neighbors near you can find your listing.");
        }

        // what and how
        if (f.offeringKind() == null || !KINDS.contains(f.offeringKind())) {
            errors.add("Choose whether you are offering an item or a service.");
        }
        if (f.listingType() == null || !TYPES.contains(f.listingType())) {
            errors.add("Choose Free (gift) or Trade.");
        }

        // title and description
        if (f.title() == null) {
            errors.add("Enter a title.");
        } else if (f.title().length() < TITLE_MIN || f.title().length() > TITLE_MAX) {
            errors.add("The title must be between " + TITLE_MIN + " and " + TITLE_MAX + " characters.");
        }
        if (f.description() == null) {
            errors.add("Enter a description.");
        } else if (f.description().length() < DESCRIPTION_MIN || f.description().length() > DESCRIPTION_MAX) {
            errors.add("The description must be between " + DESCRIPTION_MIN + " and " + DESCRIPTION_MAX + " characters.");
        }

        // category must be one of the active categories
        Integer categoryId = whole(f.categoryId());
        boolean categoryOk = categoryId != null && listings.categories().stream()
                .anyMatch(c -> categoryId.equals(((Number) c.get("category_id")).intValue()));
        if (!categoryOk) {
            errors.add("Choose a category.");
        }

        // trades must say what the owner wants in return
        String tradeWanted = "trade".equals(f.listingType()) ? f.tradeWanted() : null;
        if ("trade".equals(f.listingType())) {
            if (tradeWanted == null) {
                errors.add("For a trade, say what you would like in return.");
            } else if (tradeWanted.length() > TRADE_MAX) {
                errors.add("What you would like in return must be " + TRADE_MAX + " characters or fewer.");
            }
        }

        // optional quantity
        Integer quantity = null;
        if (f.quantity() != null) {
            quantity = whole(f.quantity());
            if (quantity == null || quantity < 1 || quantity > QUANTITY_MAX) {
                errors.add("Quantity must be a whole number from 1 to " + QUANTITY_MAX + ", or left blank.");
            }
        }
        if (f.quantityUnit() != null && f.quantityUnit().length() > UNIT_MAX) {
            errors.add("The unit (like \"boxes\" or \"hours\") must be " + UNIT_MAX + " characters or fewer.");
        }

        // pickup, drop-off or both
        Integer methodId = whole(f.fulfillmentMethodId());
        boolean methodOk = methodId != null && listings.fulfillmentMethods().stream()
                .anyMatch(m -> methodId.equals(((Number) m.get("method_id")).intValue()));
        if (!methodOk) {
            errors.add("Choose pickup, drop-off or both.");
        }

        if (f.availabilityNotes() != null && f.availabilityNotes().length() > NOTES_MAX) {
            errors.add("Availability must be " + NOTES_MAX + " characters or fewer.");
        }
        if (f.pickupDropoffNotes() != null && f.pickupDropoffNotes().length() > NOTES_MAX) {
            errors.add("Pickup or drop-off details must be " + NOTES_MAX + " characters or fewer.");
        }

        errors.addAll(photos.check(chosenPhotos));
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }

        List<String> savedPhotos = List.of();
        try {
            savedPhotos = photos.save(chosenPhotos);
            long id = listings.create(new NewListing(
                    userId, categoryId, methodId, f.title(), f.description(),
                    f.listingType(), f.offeringKind(), tradeWanted, quantity,
                    quantity == null ? null : f.quantityUnit(),
                    f.availabilityNotes(), f.pickupDropoffNotes(), toJson(savedPhotos),
                    asText(place.get("city")), asText(place.get("postal_code")),
                    place.get("latitude"), place.get("longitude")));
            return new Result(id, List.of());
        } catch (IOException e) {
            photos.deleteQuietly(savedPhotos);
            return new Result(0, List.of("Your photos could not be saved. Please try again."));
        } catch (RuntimeException e) {
            photos.deleteQuietly(savedPhotos);
            throw e;
        }
    }

    /** A database value as text, or null. */
    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    /** A whole number from text, or null. */
    private static Integer whole(String text) {
        if (text == null) return null;
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ["/uploads/listings/a.jpg", ...] or null when there are no photos. Paths are our own random file names. */
    private static String toJson(List<String> paths) {
        if (paths.isEmpty()) return null;
        return paths.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(",", "[", "]"));
    }
}
